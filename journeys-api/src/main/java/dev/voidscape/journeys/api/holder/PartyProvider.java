package dev.voidscape.journeys.api.holder;

import java.util.Optional;
import java.util.UUID;

/**
 * Bridges a party or team plugin into Journeys so that {@link HolderScope#PARTY} quests share progress.
 *
 * <p>Returned holders must have {@link HolderScope#PARTY} scope and an id that stays the same for the lifetime of
 * the party, since progress is stored under it. Implementations should call
 * {@link HolderService#partyChanged(UUID)} when a player joins or leaves a party so Journeys can load or release the
 * party's progress.</p>
 */
public interface PartyProvider {

    /** Name used in logs. */
    String name();

    /** The party the player is in, if any. Called on the server thread. */
    Optional<QuestHolder> partyOf(UUID playerId);
}
