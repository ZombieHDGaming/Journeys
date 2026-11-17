package dev.voidscape.journeys.api.progress;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read-only snapshot of a holder's progress on one quest. */
public interface QuestProgress {

    UUID holderId();

    String questId();

    QuestStatus status();

    /** The current stage while active. */
    Optional<String> currentStage();

    /**
     * True once the current stage's objectives are done but no transition has been taken yet: either the player has
     * to make a choice, or no automatic transition's conditions pass yet.
     */
    boolean stageComplete();

    /** Progress of the current stage's objectives, by objective id. */
    Map<String, Integer> objectiveProgress();

    default int objectiveProgress(String objectiveId) {
        return objectiveProgress().getOrDefault(objectiveId, 0);
    }

    /** When the current (or last) run of the quest started. */
    Instant startedAt();

    /** Total number of completions, across repeats. */
    int completions();

    Optional<Instant> lastCompletedAt();
}
