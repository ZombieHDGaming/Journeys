package dev.voidscape.journeys.api.progress;

/** Outcome of an attempt to start a quest. */
public enum StartResult {
    STARTED,
    UNKNOWN_QUEST,
    /** The holder's progress is still being loaded from storage. */
    NOT_LOADED,
    ALREADY_ACTIVE,
    /** The quest is not repeatable and was already completed. */
    ALREADY_COMPLETED,
    MAX_COMPLETIONS,
    ON_COOLDOWN,
    REQUIREMENTS_NOT_MET,
    /** A {@link dev.voidscape.journeys.api.event.QuestStartEvent} listener cancelled the start. */
    CANCELLED;

    public boolean success() {
        return this == STARTED;
    }
}
