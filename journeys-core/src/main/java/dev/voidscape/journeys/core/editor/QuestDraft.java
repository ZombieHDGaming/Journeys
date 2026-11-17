package dev.voidscape.journeys.core.editor;

import dev.voidscape.journeys.api.holder.HolderScope;
import dev.voidscape.journeys.api.quest.BoardSettings;
import dev.voidscape.journeys.api.quest.BranchMode;
import dev.voidscape.journeys.api.quest.ConfiguredCondition;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.ConfiguredReward;
import dev.voidscape.journeys.api.quest.ObjectiveMode;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestSettings;
import dev.voidscape.journeys.api.quest.QuestStage;
import dev.voidscape.journeys.api.quest.StageTransition;
import dev.voidscape.journeys.api.quest.TransitionTarget;
import dev.voidscape.journeys.api.type.ParameterValues;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;

/**
 * Mutable working copy of a quest used by the editor. Definitions are immutable, so the editor edits a draft and
 * {@link #build()}s a new definition on save.
 */
public final class QuestDraft {

    public String id;
    public String displayName;
    public List<String> description = new ArrayList<>();
    public Key icon = Key.key("minecraft:book");
    public boolean repeatable;
    public Duration cooldown = Duration.ZERO;
    public int maxCompletions;
    public HolderScope scope = HolderScope.SOLO;
    public boolean abandonable = true;
    public boolean hidden;
    public boolean requirePermission;
    public String category = BoardSettings.DEFAULT_CATEGORY;
    public BoardSettings.BoardMode boardMode = BoardSettings.DEFAULT.mode();
    public boolean showWhenLocked = BoardSettings.DEFAULT.showWhenLocked();
    public String startStage;
    public final Map<String, StageDraft> stages = new LinkedHashMap<>();
    public final List<ElementDraft> requirements = new ArrayList<>();
    public final List<ElementDraft> rewards = new ArrayList<>();
    public final Map<String, String> properties = new LinkedHashMap<>();

    /** A new quest with a single empty stage. */
    public QuestDraft(String id) {
        this.id = id;
        this.displayName = "<gold>" + id;
        this.startStage = "start";
        this.stages.put("start", new StageDraft("start"));
    }

    public static QuestDraft of(QuestDefinition quest) {
        QuestDraft draft = new QuestDraft(quest.id());
        draft.stages.clear();
        draft.displayName = quest.displayName();
        draft.description = new ArrayList<>(quest.description());
        draft.icon = quest.icon();
        QuestSettings s = quest.settings();
        draft.repeatable = s.repeatable();
        draft.cooldown = s.cooldown();
        draft.maxCompletions = s.maxCompletions();
        draft.scope = s.scope();
        draft.abandonable = s.abandonable();
        draft.hidden = s.hidden();
        draft.requirePermission = s.requirePermission();
        draft.category = s.board().category();
        draft.boardMode = s.board().mode();
        draft.showWhenLocked = s.board().showWhenLocked();
        draft.startStage = quest.startStage();
        quest.stages().values().forEach(stage -> draft.stages.put(stage.id(), StageDraft.of(stage)));
        quest.requirements().forEach(c -> draft.requirements.add(ElementDraft.of(c)));
        quest.rewards().forEach(r -> draft.rewards.add(ElementDraft.of(r)));
        draft.properties.putAll(quest.properties());
        return draft;
    }

    public QuestDefinition build() {
        Map<String, QuestStage> builtStages = new LinkedHashMap<>();
        stages.forEach((id, stage) -> builtStages.put(id, stage.build()));
        return new QuestDefinition(id, displayName, description, icon,
                new QuestSettings(repeatable, cooldown, maxCompletions, scope, abandonable, hidden, requirePermission,
                        new BoardSettings(category, boardMode, showWhenLocked)),
                startStage, builtStages,
                requirements.stream().map(ElementDraft::toCondition).toList(),
                rewards.stream().map(ElementDraft::toReward).toList(),
                properties);
    }

    /** Renames a stage and rewrites every reference to it. */
    public void renameStage(String from, String to) {
        StageDraft stage = stages.get(from);
        if (stage == null || stages.containsKey(to)) {
            return;
        }
        Map<String, StageDraft> reordered = new LinkedHashMap<>();
        stages.forEach((id, s) -> reordered.put(id.equals(from) ? to : id, s));
        stages.clear();
        stages.putAll(reordered);
        stage.id = to;
        if (startStage.equals(from)) {
            startStage = to;
        }
        for (StageDraft s : stages.values()) {
            for (TransitionDraft t : s.transitions) {
                if (t.target.equals(from)) {
                    t.target = to;
                }
            }
        }
    }

    /** Removes a stage. Transitions pointing at it are removed too. */
    public void removeStage(String id) {
        stages.remove(id);
        for (StageDraft s : stages.values()) {
            s.transitions.removeIf(t -> t.target.equals(id));
        }
        if (startStage.equals(id) && !stages.isEmpty()) {
            startStage = stages.keySet().iterator().next();
        }
    }

    /** Mutable copy of a stage. */
    public static final class StageDraft {
        public String id;
        public String displayName;
        public List<String> description = new ArrayList<>();
        public ObjectiveMode objectiveMode = ObjectiveMode.ALL;
        public BranchMode branchMode = BranchMode.AUTOMATIC;
        public final List<ObjectiveDraft> objectives = new ArrayList<>();
        public final List<TransitionDraft> transitions = new ArrayList<>();
        public final List<ElementDraft> rewards = new ArrayList<>();

        public StageDraft(String id) {
            this.id = id;
            this.displayName = "<yellow>" + id;
        }

        static StageDraft of(QuestStage stage) {
            StageDraft draft = new StageDraft(stage.id());
            draft.displayName = stage.displayName();
            draft.description = new ArrayList<>(stage.description());
            draft.objectiveMode = stage.objectiveMode();
            draft.branchMode = stage.branchMode();
            stage.objectives().forEach(o -> draft.objectives.add(ObjectiveDraft.of(o)));
            stage.transitions().forEach(t -> draft.transitions.add(TransitionDraft.of(t)));
            stage.rewards().forEach(r -> draft.rewards.add(ElementDraft.of(r)));
            return draft;
        }

        QuestStage build() {
            return new QuestStage(id, displayName, description, objectiveMode,
                    objectives.stream().map(ObjectiveDraft::build).toList(),
                    branchMode,
                    transitions.stream().map(TransitionDraft::build).toList(),
                    rewards.stream().map(ElementDraft::toReward).toList());
        }

        /** Next free objective id such as {@code objective_3}. */
        public String nextObjectiveId() {
            int n = objectives.size() + 1;
            while (true) {
                String candidate = "objective_" + n;
                if (objectives.stream().noneMatch(o -> o.id.equals(candidate))) {
                    return candidate;
                }
                n++;
            }
        }
    }

    /** Anything with a type and parameters: objectives, conditions and rewards. */
    public abstract static class ParameterizedDraft {
        public Key type;
        public final Map<String, String> params = new LinkedHashMap<>();

        ParameterizedDraft(Key type) {
            this.type = type;
        }

        ParameterValues values() {
            return ParameterValues.of(params);
        }
    }

    public static final class ObjectiveDraft extends ParameterizedDraft {
        public String id;
        public int amount = 1;
        public @Nullable String description;

        public ObjectiveDraft(String id, Key type) {
            super(type);
            this.id = id;
        }

        static ObjectiveDraft of(ConfiguredObjective objective) {
            ObjectiveDraft draft = new ObjectiveDraft(objective.id(), objective.type());
            draft.amount = objective.amount();
            draft.description = objective.description();
            draft.params.putAll(objective.params().asMap());
            return draft;
        }

        ConfiguredObjective build() {
            return new ConfiguredObjective(id, type, Math.max(1, amount), values(), description);
        }
    }

    /** A condition or reward. {@code negate} is only used for conditions. */
    public static final class ElementDraft extends ParameterizedDraft {
        public boolean negate;
        /** Player-facing text; only used for conditions. */
        public @Nullable String description;

        public ElementDraft(Key type) {
            super(type);
        }

        static ElementDraft of(ConfiguredCondition condition) {
            ElementDraft draft = new ElementDraft(condition.type());
            draft.negate = condition.negate();
            draft.description = condition.description();
            draft.params.putAll(condition.params().asMap());
            return draft;
        }

        static ElementDraft of(ConfiguredReward reward) {
            ElementDraft draft = new ElementDraft(reward.type());
            draft.params.putAll(reward.params().asMap());
            return draft;
        }

        ConfiguredCondition toCondition() {
            return new ConfiguredCondition(type, values(), negate, description);
        }

        ConfiguredReward toReward() {
            return new ConfiguredReward(type, values());
        }
    }

    public static final class TransitionDraft {
        /** Stage id, {@code @complete} or {@code @fail}. */
        public String target;
        public @Nullable String label;
        public final List<ElementDraft> conditions = new ArrayList<>();

        public TransitionDraft(String target) {
            this.target = target;
        }

        static TransitionDraft of(StageTransition transition) {
            TransitionDraft draft = new TransitionDraft(transition.target().serialize());
            draft.label = transition.label();
            transition.conditions().forEach(c -> draft.conditions.add(ElementDraft.of(c)));
            return draft;
        }

        StageTransition build() {
            return new StageTransition(TransitionTarget.parse(target), label, conditions.stream().map(ElementDraft::toCondition).toList());
        }
    }
}
