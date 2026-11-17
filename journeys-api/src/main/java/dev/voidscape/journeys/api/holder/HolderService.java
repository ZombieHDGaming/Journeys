package dev.voidscape.journeys.api.holder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Resolves the holders a player belongs to. */
public interface HolderService {

    QuestHolder solo(UUID playerId);

    /** The player's party holder, if a provider is registered and the player is in a party. */
    Optional<QuestHolder> party(UUID playerId);

    /** The holder a quest with the given scope should use for this player. Falls back to solo. */
    QuestHolder holderFor(UUID playerId, HolderScope scope);

    /** Every holder the player currently belongs to: solo first, then party. */
    List<QuestHolder> holdersOf(UUID playerId);

    /** Sets the party provider, replacing any previous one. Pass null to remove it. */
    void setPartyProvider(@Nullable PartyProvider provider);

    /** Notifies Journeys that the player's party membership changed. Must be called on the server thread. */
    void partyChanged(UUID playerId);
}
