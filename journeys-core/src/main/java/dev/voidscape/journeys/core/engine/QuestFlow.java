package dev.voidscape.journeys.core.engine;

import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.QuestStage;
import dev.voidscape.journeys.api.quest.StageTransition;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Pure rules of stage progression, kept free of server state so they can be unit tested. */
public final class QuestFlow {

    private QuestFlow() {
    }

    public static boolean isObjectiveComplete(ConfiguredObjective objective, Map<String, Integer> progress,
                                              ToIntFunction<ConfiguredObjective> goal) {
        return progress.getOrDefault(objective.id(), 0) >= goal.applyAsInt(objective);
    }

    /** Whether an objective can receive progress right now under the stage's {@link dev.voidscape.journeys.api.quest.ObjectiveMode}. */
    public static boolean accepts(QuestStage stage, ConfiguredObjective objective, Map<String, Integer> progress,
                                  ToIntFunction<ConfiguredObjective> goal) {
        if (isObjectiveComplete(objective, progress, goal)) {
            return false;
        }
        return switch (stage.objectiveMode()) {
            case ALL, ANY -> true;
            case SEQUENTIAL -> {
                for (ConfiguredObjective candidate : stage.objectives()) {
                    if (!isObjectiveComplete(candidate, progress, goal)) {
                        yield candidate.id().equals(objective.id());
                    }
                }
                yield false;
            }
        };
    }

    /** Whether the stage's objectives are done. A stage without objectives is complete immediately. */
    public static boolean isStageComplete(QuestStage stage, Map<String, Integer> progress, ToIntFunction<ConfiguredObjective> goal) {
        List<ConfiguredObjective> objectives = stage.objectives();
        if (objectives.isEmpty()) {
            return true;
        }
        return switch (stage.objectiveMode()) {
            case ALL, SEQUENTIAL -> objectives.stream().allMatch(o -> isObjectiveComplete(o, progress, goal));
            case ANY -> objectives.stream().anyMatch(o -> isObjectiveComplete(o, progress, goal));
        };
    }

    /** Index of the first transition whose conditions pass, in priority order. */
    public static OptionalInt firstAvailable(List<StageTransition> transitions, Predicate<StageTransition> conditionsPass) {
        for (int i = 0; i < transitions.size(); i++) {
            if (conditionsPass.test(transitions.get(i))) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }
}
