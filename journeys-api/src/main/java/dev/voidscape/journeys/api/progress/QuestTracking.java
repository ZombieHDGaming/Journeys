package dev.voidscape.journeys.api.progress;

import java.util.Optional;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * The quest each player is currently following. It drives the boss bar and the {@code %journeys_tracked_...%}
 * placeholders.
 *
 * <p>Starting a quest with a boss bar tracks it automatically, as does starting any quest while nothing is tracked.
 * When the tracked quest ends, the most recently started active quest is tracked instead. The choice is saved with
 * the player. All methods must be called on the server thread.</p>
 */
public interface QuestTracking {

    /** The tracked quest id, if the player is tracking an active quest. */
    Optional<String> tracked(Player player);

    /**
     * Tracks a quest, or stops tracking with null.
     *
     * @return false if the quest is not active for the player
     */
    boolean track(Player player, @Nullable String questId);
}
