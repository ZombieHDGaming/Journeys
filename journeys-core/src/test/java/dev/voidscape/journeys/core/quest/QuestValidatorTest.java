package dev.voidscape.journeys.core.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidscape.journeys.api.quest.BranchMode;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.ObjectiveMode;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestStage;
import dev.voidscape.journeys.api.type.ParameterValues;
import dev.voidscape.journeys.core.QuestFixtures;
import dev.voidscape.journeys.core.TestTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

class QuestValidatorTest {

    private final QuestValidator validator = new QuestValidator(TestTypes.registries());

    private static QuestDefinition withStages(UnaryOperator<Map<String, QuestStage>> change) {
        QuestDefinition base = QuestFixtures.branching();
        Map<String, QuestStage> stages = change.apply(new LinkedHashMap<>(base.stages()));
        return new QuestDefinition(base.id(), base.displayName(), base.description(), base.icon(), base.settings(),
                base.startStage(), stages, base.requirements(), base.rewards(), base.properties());
    }

    private static boolean has(List<QuestValidator.Issue> issues, QuestValidator.Severity severity, String pathPrefix) {
        return issues.stream().anyMatch(i -> i.severity() == severity && i.path().startsWith(pathPrefix));
    }

    @Test
    void validQuestHasNoErrors() {
        List<QuestValidator.Issue> issues = validator.validate(QuestFixtures.branching());
        assertFalse(QuestValidator.hasErrors(issues), issues.toString());
    }

    @Test
    void danglingTransitionIsAnError() {
        QuestDefinition quest = withStages(stages -> {
            stages.put("right", QuestFixtures.stage("right", ObjectiveMode.ALL, List.of(), BranchMode.AUTOMATIC,
                    List.of(QuestFixtures.to("nowhere"))));
            return stages;
        });
        assertTrue(has(validator.validate(quest), QuestValidator.Severity.ERROR, "stages.right.transitions[0].to"));
    }

    @Test
    void unknownTypesAndMissingParametersAreErrors() {
        QuestDefinition quest = withStages(stages -> {
            stages.put("left", QuestFixtures.stage("left", ObjectiveMode.ALL, List.of(
                    new ConfiguredObjective("x", Key.key("other:missing"), 1, ParameterValues.empty(), null),
                    new ConfiguredObjective("y", Key.key("journeys:test_objective"), 1, ParameterValues.empty(), null)
            ), BranchMode.AUTOMATIC, List.of()));
            return stages;
        });
        List<QuestValidator.Issue> issues = validator.validate(quest);
        assertTrue(has(issues, QuestValidator.Severity.ERROR, "stages.left.objectives[0].type"));
        assertTrue(has(issues, QuestValidator.Severity.ERROR, "stages.left.objectives[1].params.target"));
    }

    @Test
    void duplicateObjectiveIdsAreErrors() {
        QuestDefinition quest = withStages(stages -> {
            stages.put("left", QuestFixtures.stage("left", ObjectiveMode.ALL,
                    List.of(QuestFixtures.objective("same", 1), QuestFixtures.objective("same", 2)), BranchMode.AUTOMATIC, List.of()));
            return stages;
        });
        assertTrue(has(validator.validate(quest), QuestValidator.Severity.ERROR, "stages.left.objectives[1].id"));
    }

    @Test
    void unreachableStagesAndUnlabelledChoicesAreWarnings() {
        QuestDefinition quest = withStages(stages -> {
            stages.put("orphan", QuestFixtures.stage("orphan", ObjectiveMode.ALL, List.of(), BranchMode.AUTOMATIC, List.of()));
            QuestStage start = stages.get("start");
            stages.put("start", QuestFixtures.stage("start", start.objectiveMode(), start.objectives(), BranchMode.CHOICE,
                    List.of(QuestFixtures.to("left"), QuestFixtures.to("right"))));
            return stages;
        });
        List<QuestValidator.Issue> issues = validator.validate(quest);
        assertFalse(QuestValidator.hasErrors(issues), issues.toString());
        assertTrue(has(issues, QuestValidator.Severity.WARNING, "stages.orphan"));
        assertTrue(has(issues, QuestValidator.Severity.WARNING, "stages.start.transitions[0].label"));
    }

    @Test
    void invalidBoardCategoryIsAnError() {
        QuestDefinition base = QuestFixtures.branching();
        var s = base.settings();
        QuestDefinition quest = new QuestDefinition(base.id(), base.displayName(), base.description(), base.icon(),
                new dev.voidscape.journeys.api.quest.QuestSettings(s.repeatable(), s.cooldown(), s.maxCompletions(), s.scope(),
                        s.abandonable(), s.hidden(), s.requirePermission(),
                        new dev.voidscape.journeys.api.quest.BoardSettings("Bad Category!", s.board().mode(), true), s.bossBar()),
                base.startStage(), base.stages(), base.requirements(), base.rewards(), base.properties());
        assertTrue(has(validator.validate(quest), QuestValidator.Severity.ERROR, "settings.board.category"));
    }

    @Test
    void missingStartStageIsAnError() {
        QuestDefinition base = QuestFixtures.branching();
        QuestDefinition quest = new QuestDefinition(base.id(), base.displayName(), base.description(), base.icon(), base.settings(),
                "ghost", base.stages(), base.requirements(), base.rewards(), base.properties());
        assertTrue(has(validator.validate(quest), QuestValidator.Severity.ERROR, "start-stage"));
    }
}
