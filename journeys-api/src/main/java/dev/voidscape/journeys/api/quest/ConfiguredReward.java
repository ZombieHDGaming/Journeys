package dev.voidscape.journeys.api.quest;

import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.Objects;
import net.kyori.adventure.key.Key;

/**
 * A reward placed on a stage or quest.
 *
 * @param type   key of the {@link dev.voidscape.journeys.api.type.RewardType}
 * @param params type parameters
 */
public record ConfiguredReward(Key type, ParameterValues params) {

    public ConfiguredReward {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(params, "params");
    }
}
