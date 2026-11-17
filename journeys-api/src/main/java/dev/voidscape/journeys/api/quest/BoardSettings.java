package dev.voidscape.journeys.api.quest;

import java.util.Objects;

/**
 * How a quest appears on the quest board.
 *
 * @param category       category id the quest is listed under; see {@link QuestIds} for the allowed characters
 * @param mode           whether the quest is listed, and whether it can be started from the board
 * @param showWhenLocked whether players who do not meet the requirements see it, with a checklist of what is missing,
 *                       or whether it stays hidden until unlocked
 */
public record BoardSettings(String category, BoardMode mode, boolean showWhenLocked) {

    public static final String DEFAULT_CATEGORY = "general";
    public static final BoardSettings DEFAULT = new BoardSettings(DEFAULT_CATEGORY, BoardMode.START, true);

    public BoardSettings {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(mode, "mode");
    }

    public enum BoardMode {
        /** Not listed on the board. Started some other way, e.g. by an NPC or a command. */
        HIDDEN,
        /** Listed with its status and requirements, but started elsewhere. */
        VIEW,
        /** Listed and started by clicking it on the board. */
        START
    }
}
