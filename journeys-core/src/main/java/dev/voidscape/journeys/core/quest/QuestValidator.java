package dev.voidscape.journeys.core.quest;

import dev.voidscape.journeys.api.quest.BranchMode;
import dev.voidscape.journeys.api.quest.ConfiguredCondition;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.ConfiguredReward;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestIds;
import dev.voidscape.journeys.api.quest.QuestStage;
import dev.voidscape.journeys.api.quest.StageTransition;
import dev.voidscape.journeys.api.quest.TransitionTarget;
import dev.voidscape.journeys.api.type.ElementType;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterValues;
import dev.voidscape.journeys.api.type.TypeRegistry;
import dev.voidscape.journeys.core.type.Registries;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Semantic checks on a quest definition. Errors prevent a quest from loading or being saved from the editor;
 * warnings are reported but allowed.
 */
public final class QuestValidator {

    public enum Severity {
        ERROR,
        WARNING
    }

    public record Issue(Severity severity, String path, String message) {
        @Override
        public String toString() {
            return severity + " " + path + ": " + message;
        }
    }

    private final Registries registries;

    public QuestValidator(Registries registries) {
        this.registries = registries;
    }

    public static boolean hasErrors(List<Issue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
    }

    public List<Issue> validate(QuestDefinition quest) {
        List<Issue> issues = new ArrayList<>();

        if (!QuestIds.isValid(quest.id())) {
            error(issues, "id", "'" + quest.id() + "' must match " + QuestIds.PATTERN.pattern());
        }
        if (quest.stages().isEmpty()) {
            error(issues, "stages", "a quest needs at least one stage");
        } else if (!quest.stages().containsKey(quest.startStage())) {
            error(issues, "start-stage", "stage '" + quest.startStage() + "' does not exist");
        }

        if (!QuestIds.isValid(quest.settings().board().category())) {
            error(issues, "settings.board.category", "category must match " + QuestIds.PATTERN.pattern());
        }
        checkConditions(issues, "requirements", quest.requirements());
        checkRewards(issues, "rewards", quest.rewards());

        for (QuestStage stage : quest.stages().values()) {
            checkStage(issues, quest, stage);
        }

        if (quest.stages().containsKey(quest.startStage())) {
            Set<String> reachable = reachableStages(quest);
            for (String stageId : quest.stages().keySet()) {
                if (!reachable.contains(stageId)) {
                    warn(issues, "stages." + stageId, "unreachable from the start stage");
                }
            }
        }
        return issues;
    }

    private void checkStage(List<Issue> issues, QuestDefinition quest, QuestStage stage) {
        String path = "stages." + stage.id();
        if (!QuestIds.isValid(stage.id())) {
            error(issues, path, "stage id must match " + QuestIds.PATTERN.pattern());
        }

        Set<String> objectiveIds = new HashSet<>();
        for (int i = 0; i < stage.objectives().size(); i++) {
            ConfiguredObjective objective = stage.objectives().get(i);
            String objectivePath = path + ".objectives[" + i + "]";
            if (!QuestIds.isValid(objective.id())) {
                error(issues, objectivePath + ".id", "objective id must match " + QuestIds.PATTERN.pattern());
            }
            if (!objectiveIds.add(objective.id())) {
                error(issues, objectivePath + ".id", "duplicate objective id '" + objective.id() + "' in this stage");
            }
            Optional<ObjectiveType> type = checkType(issues, objectivePath, registries.objectives(), objective.type(), objective.params());
            if (type.isPresent() && !type.get().countable() && objective.amount() != 1) {
                warn(issues, objectivePath + ".amount", type.get().key().asString() + " is not countable; amount is ignored");
            }
        }

        if (stage.branchMode() == BranchMode.CHOICE && stage.transitions().isEmpty()) {
            warn(issues, path + ".branch-mode", "choice stage has no transitions; the quest completes after it");
        }
        for (int i = 0; i < stage.transitions().size(); i++) {
            StageTransition transition = stage.transitions().get(i);
            String transitionPath = path + ".transitions[" + i + "]";
            if (transition.target() instanceof TransitionTarget.ToStage(String target) && !quest.stages().containsKey(target)) {
                error(issues, transitionPath + ".to", "stage '" + target + "' does not exist");
            }
            if (stage.branchMode() == BranchMode.CHOICE && (transition.label() == null || transition.label().isBlank())) {
                warn(issues, transitionPath + ".label", "choice transitions should have a label");
            }
            checkConditions(issues, transitionPath + ".conditions", transition.conditions());
        }
        if (stage.branchMode() == BranchMode.AUTOMATIC && !stage.transitions().isEmpty()
                && stage.transitions().stream().allMatch(t -> !t.conditions().isEmpty())) {
            warn(issues, path + ".transitions", "every transition has conditions; the quest stalls here if none pass");
        }
        checkRewards(issues, path + ".rewards", stage.rewards());
    }

    private void checkConditions(List<Issue> issues, String path, List<ConfiguredCondition> conditions) {
        for (int i = 0; i < conditions.size(); i++) {
            ConfiguredCondition condition = conditions.get(i);
            checkType(issues, path + "[" + i + "]", registries.conditions(), condition.type(), condition.params());
        }
    }

    private void checkRewards(List<Issue> issues, String path, List<ConfiguredReward> rewards) {
        for (int i = 0; i < rewards.size(); i++) {
            ConfiguredReward reward = rewards.get(i);
            checkType(issues, path + "[" + i + "]", registries.rewards(), reward.type(), reward.params());
        }
    }

    private <T extends ElementType> Optional<T> checkType(List<Issue> issues, String path, TypeRegistry<T> registry,
                                                          net.kyori.adventure.key.Key key, ParameterValues values) {
        Optional<T> type = registry.get(key);
        if (type.isEmpty()) {
            error(issues, path + ".type", "unknown type '" + key.asString() + "' (is the plugin providing it installed?)");
            return type;
        }
        Set<String> known = new HashSet<>();
        for (Parameter<?> parameter : type.get().parameters()) {
            known.add(parameter.id());
            Optional<String> raw = values.raw(parameter.id());
            if (raw.isEmpty()) {
                if (parameter.required()) {
                    error(issues, path + ".params." + parameter.id(), "is required");
                }
                continue;
            }
            try {
                parameter.type().parse(raw.get());
            } catch (IllegalArgumentException e) {
                error(issues, path + ".params." + parameter.id(), e.getMessage());
            }
        }
        for (String id : values.asMap().keySet()) {
            if (!known.contains(id)) {
                warn(issues, path + ".params." + id, "unknown parameter, ignored");
            }
        }
        return type;
    }

    private static Set<String> reachableStages(QuestDefinition quest) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(quest.startStage());
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!seen.add(id)) {
                continue;
            }
            QuestStage stage = quest.stages().get(id);
            if (stage == null) {
                continue;
            }
            for (StageTransition transition : stage.transitions()) {
                if (transition.target() instanceof TransitionTarget.ToStage(String target)) {
                    queue.add(target);
                }
            }
        }
        return seen;
    }

    private static void error(List<Issue> issues, String path, String message) {
        issues.add(new Issue(Severity.ERROR, path, message));
    }

    private static void warn(List<Issue> issues, String path, String message) {
        issues.add(new Issue(Severity.WARNING, path, message));
    }
}
