package dev.voidscape.journeys.api.quest;

import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.Objects;
import net.kyori.adventure.key.Key;

/**
 * A condition placed in a quest's requirements or on a transition.
 *
 * @param type   key of the {@link dev.voidscape.journeys.api.type.ConditionType}
 * @param params type parameters
 * @param negate inverts the result
 */
public record ConfiguredCondition(Key type, ParameterValues params, boolean negate) {

    public ConfiguredCondition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(params, "params");
    }
}
