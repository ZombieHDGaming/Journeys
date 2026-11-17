package dev.voidscape.journeys.core.storage;

import dev.voidscape.journeys.api.progress.QuestStatus;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, storage-facing snapshot of a holder's progress on one quest.
 *
 * @param lastCompletedAt epoch millis, or 0 if never completed
 * @param objectives      progress of the current stage's objectives
 */
public record QuestRecord(
        UUID holderId,
        String questId,
        QuestStatus status,
        @Nullable String stageId,
        boolean stageComplete,
        long startedAt,
        long updatedAt,
        int completions,
        long lastCompletedAt,
        Map<String, Integer> objectives
) {

    public QuestRecord {
        Objects.requireNonNull(holderId, "holderId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(status, "status");
        objectives = Map.copyOf(objectives);
    }
}
