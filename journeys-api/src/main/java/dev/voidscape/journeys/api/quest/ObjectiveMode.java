package dev.voidscape.journeys.api.quest;

/** How the objectives of a stage combine. */
public enum ObjectiveMode {
    /** Every objective must be completed, in any order. */
    ALL,
    /** Completing any single objective completes the stage. */
    ANY,
    /** Objectives must be completed in the order they are listed; only the first unfinished one gains progress. */
    SEQUENTIAL
}
