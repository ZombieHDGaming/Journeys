package dev.voidscape.journeys.core.engine;

import dev.voidscape.journeys.api.progress.QuestProgress;
import dev.voidscape.journeys.api.progress.QuestStatus;
import dev.voidscape.journeys.core.storage.QuestRecord;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Mutable progress of one holder on one quest. Only touched on the server thread. */
final class QuestState {

    final UUID holderId;
    final String questId;
    QuestStatus status;
    @Nullable String stageId;
    boolean stageComplete;
    long startedAt;
    long updatedAt;
    int completions;
    long lastCompletedAt;
    final Map<String, Integer> objectives = new LinkedHashMap<>();
    boolean dirty;

    QuestState(UUID holderId, String questId) {
        this.holderId = holderId;
        this.questId = questId;
        this.status = QuestStatus.ACTIVE;
    }

    static QuestState from(QuestRecord record) {
        QuestState state = new QuestState(record.holderId(), record.questId());
        state.status = record.status();
        state.stageId = record.stageId();
        state.stageComplete = record.stageComplete();
        state.startedAt = record.startedAt();
        state.updatedAt = record.updatedAt();
        state.completions = record.completions();
        state.lastCompletedAt = record.lastCompletedAt();
        state.objectives.putAll(record.objectives());
        return state;
    }

    void touch() {
        updatedAt = System.currentTimeMillis();
        dirty = true;
    }

    QuestRecord toRecord() {
        return new QuestRecord(holderId, questId, status, stageId, stageComplete, startedAt, updatedAt, completions,
                lastCompletedAt, objectives);
    }

    QuestProgress snapshot() {
        return new Snapshot(holderId, questId, status, Optional.ofNullable(stageId), stageComplete, Map.copyOf(objectives),
                Instant.ofEpochMilli(startedAt), completions,
                lastCompletedAt > 0 ? Optional.of(Instant.ofEpochMilli(lastCompletedAt)) : Optional.empty());
    }

    private record Snapshot(
            UUID holderId,
            String questId,
            QuestStatus status,
            Optional<String> currentStage,
            boolean stageComplete,
            Map<String, Integer> objectiveProgress,
            Instant startedAt,
            int completions,
            Optional<Instant> lastCompletedAt
    ) implements QuestProgress {
    }
}
