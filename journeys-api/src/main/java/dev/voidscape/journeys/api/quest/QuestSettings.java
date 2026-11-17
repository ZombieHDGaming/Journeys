package dev.voidscape.journeys.api.quest;

import dev.voidscape.journeys.api.holder.HolderScope;
import java.time.Duration;
import java.util.Objects;

/**
 * Behavioural flags of a quest.
 *
 * @param repeatable        whether the quest can be started again after completion
 * @param cooldown          minimum time between completion and the next start; {@link Duration#ZERO} for none
 * @param maxCompletions    how often the quest may be completed in total; 0 for unlimited
 * @param scope             whether progress belongs to the player or their party
 * @param abandonable       whether players may abandon the quest themselves
 * @param hidden            hidden quests do not appear in the player's quest log
 * @param requirePermission whether starting needs the permission {@link #permissionNode(String) journeys.quest.&lt;id&gt;},
 *                          on top of the quest's requirements
 * @param board             how the quest appears on the quest board
 * @param bossBar           the boss bar shown while this quest is tracked
 */
public record QuestSettings(
        boolean repeatable,
        Duration cooldown,
        int maxCompletions,
        HolderScope scope,
        boolean abandonable,
        boolean hidden,
        boolean requirePermission,
        BoardSettings board,
        BossBarSettings bossBar
) {

    public static final QuestSettings DEFAULT = new QuestSettings(false, Duration.ZERO, 0, HolderScope.SOLO, true, false,
            false, BoardSettings.DEFAULT, BossBarSettings.DEFAULT);

    public QuestSettings {
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(bossBar, "bossBar");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must not be negative");
        }
        if (maxCompletions < 0) {
            throw new IllegalArgumentException("maxCompletions must not be negative");
        }
    }

    /** The permission required when {@link #requirePermission()} is set. */
    public static String permissionNode(String questId) {
        return "journeys.quest." + questId;
    }
}
