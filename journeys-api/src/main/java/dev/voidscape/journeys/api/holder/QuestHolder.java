package dev.voidscape.journeys.api.holder;

import java.util.Set;
import java.util.UUID;

/**
 * Owner of quest progress: a single player, or a group sharing progress.
 *
 * <p>Progress is stored per holder id. For solo holders the id is the player's UUID; party providers supply a stable
 * id of their own for each party.</p>
 */
public interface QuestHolder {

    UUID id();

    HolderScope scope();

    /** Current members. For solo holders, just the player. */
    Set<UUID> members();

    default boolean isMember(UUID playerId) {
        return members().contains(playerId);
    }
}
