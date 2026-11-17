package dev.voidscape.journeys.api.quest;

/** How a completed stage picks its next transition. */
public enum BranchMode {
    /** The first transition whose conditions pass is followed automatically. */
    AUTOMATIC,
    /** The player picks one of the transitions whose conditions pass, from chat or a menu. */
    CHOICE
}
