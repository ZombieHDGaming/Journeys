package dev.voidscape.journeys.api.progress;

/** Lifecycle state of a holder's progress on a quest. */
public enum QuestStatus {
    ACTIVE,
    COMPLETED,
    FAILED,
    ABANDONED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
