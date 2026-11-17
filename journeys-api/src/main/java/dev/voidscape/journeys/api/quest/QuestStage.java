package dev.voidscape.journeys.api.quest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One node of a quest graph.
 *
 * @param id            unique within the quest
 * @param displayName   MiniMessage name
 * @param description   MiniMessage lines
 * @param objectiveMode how the objectives combine
 * @param objectives    what has to be done in this stage; a stage without objectives completes immediately
 * @param branchMode    how the next stage is chosen
 * @param transitions   outgoing edges, in priority order; none means the quest completes after this stage
 * @param rewards       granted when this stage's objectives are completed
 */
public record QuestStage(
        String id,
        String displayName,
        List<String> description,
        ObjectiveMode objectiveMode,
        List<ConfiguredObjective> objectives,
        BranchMode branchMode,
        List<StageTransition> transitions,
        List<ConfiguredReward> rewards
) {

    public QuestStage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(objectiveMode, "objectiveMode");
        Objects.requireNonNull(branchMode, "branchMode");
        description = List.copyOf(description);
        objectives = List.copyOf(objectives);
        transitions = List.copyOf(transitions);
        rewards = List.copyOf(rewards);
    }

    /** Whether completing this stage completes the quest. */
    public boolean isFinal() {
        return transitions.isEmpty();
    }

    public Optional<ConfiguredObjective> objective(String objectiveId) {
        return objectives.stream().filter(o -> o.id().equals(objectiveId)).findFirst();
    }
}
