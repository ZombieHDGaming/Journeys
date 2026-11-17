package dev.voidscape.journeys.api.holder;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A holder that is a single player. Its id is the player's UUID. */
public record SoloHolder(UUID playerId) implements QuestHolder {

    public SoloHolder {
        Objects.requireNonNull(playerId, "playerId");
    }

    @Override
    public UUID id() {
        return playerId;
    }

    @Override
    public HolderScope scope() {
        return HolderScope.SOLO;
    }

    @Override
    public Set<UUID> members() {
        return Set.of(playerId);
    }
}
