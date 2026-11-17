package dev.voidscape.journeys.core.engine;

import static dev.voidscape.journeys.core.QuestFixtures.objective;
import static dev.voidscape.journeys.core.QuestFixtures.stage;
import static dev.voidscape.journeys.core.QuestFixtures.to;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidscape.journeys.api.quest.BranchMode;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.ObjectiveMode;
import dev.voidscape.journeys.api.quest.QuestStage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class QuestFlowTest {

    private static final ToIntFunction<ConfiguredObjective> GOAL = ConfiguredObjective::amount;

    private static QuestStage stageWith(ObjectiveMode mode) {
        return stage("s", mode, List.of(objective("a", 2), objective("b", 1)), BranchMode.AUTOMATIC, List.of());
    }

    @Test
    void allModeNeedsEveryObjective() {
        QuestStage stage = stageWith(ObjectiveMode.ALL);
        Map<String, Integer> progress = new HashMap<>(Map.of("a", 2));
        assertFalse(QuestFlow.isStageComplete(stage, progress, GOAL));
        progress.put("b", 1);
        assertTrue(QuestFlow.isStageComplete(stage, progress, GOAL));
    }

    @Test
    void anyModeNeedsOneObjective() {
        QuestStage stage = stageWith(ObjectiveMode.ANY);
        assertFalse(QuestFlow.isStageComplete(stage, Map.of("a", 1), GOAL));
        assertTrue(QuestFlow.isStageComplete(stage, Map.of("b", 1), GOAL));
    }

    @Test
    void sequentialModeOnlyAcceptsTheFirstUnfinishedObjective() {
        QuestStage stage = stageWith(ObjectiveMode.SEQUENTIAL);
        ConfiguredObjective a = stage.objectives().get(0);
        ConfiguredObjective b = stage.objectives().get(1);

        assertTrue(QuestFlow.accepts(stage, a, Map.of(), GOAL));
        assertFalse(QuestFlow.accepts(stage, b, Map.of(), GOAL));

        Map<String, Integer> aDone = Map.of("a", 2);
        assertFalse(QuestFlow.accepts(stage, a, aDone, GOAL), "completed objectives take no more progress");
        assertTrue(QuestFlow.accepts(stage, b, aDone, GOAL));
    }

    @Test
    void stageWithoutObjectivesIsCompleteImmediately() {
        QuestStage empty = stage("e", ObjectiveMode.ALL, List.of(), BranchMode.AUTOMATIC, List.of());
        assertTrue(QuestFlow.isStageComplete(empty, Map.of(), GOAL));
    }

    @Test
    void firstAvailableTransitionWinsInOrder() {
        var transitions = List.of(to("x", "X", 9), to("y"), to("z"));
        OptionalInt picked = QuestFlow.firstAvailable(transitions, t -> t.conditions().isEmpty());
        assertEquals(OptionalInt.of(1), picked);
        assertEquals(OptionalInt.empty(), QuestFlow.firstAvailable(transitions, t -> false));
    }
}
