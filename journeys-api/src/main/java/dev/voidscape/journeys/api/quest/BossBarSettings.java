package dev.voidscape.journeys.api.quest;

import java.util.Objects;
import net.kyori.adventure.bossbar.BossBar;

/**
 * Boss bar shown for a quest while it is the player's tracked quest.
 *
 * @param enabled whether this quest shows a boss bar at all
 * @param color   bar color
 * @param overlay bar style: solid, or split into 6, 10, 12 or 20 notches
 * @param mode    whether the bar is always visible or only briefly after progress
 */
public record BossBarSettings(boolean enabled, BossBar.Color color, BossBar.Overlay overlay, Mode mode) {

    public static final BossBarSettings DEFAULT = new BossBarSettings(false, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS, Mode.ALWAYS);

    public BossBarSettings {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(overlay, "overlay");
        Objects.requireNonNull(mode, "mode");
    }

    public enum Mode {
        /** Visible for as long as the quest is active and tracked. */
        ALWAYS,
        /** Appears when the quest progresses or changes stage, then hides after a few seconds (see config.yml). */
        ON_PROGRESS
    }
}
