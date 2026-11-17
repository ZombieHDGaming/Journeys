package dev.voidscape.journeys.api.quest;

import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;

/**
 * A condition placed in a quest's requirements or on a transition.
 *
 * @param type        key of the {@link dev.voidscape.journeys.api.type.ConditionType}
 * @param params      type parameters
 * @param negate      inverts the result
 * @param description optional MiniMessage text shown to players instead of the type's generated summary, e.g. on
 *                    the quest board's requirement checklist ("Reach the Miner rank" rather than a permission node)
 */
public record ConfiguredCondition(Key type, ParameterValues params, boolean negate, @Nullable String description) {

    public ConfiguredCondition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(params, "params");
    }

    public ConfiguredCondition(Key type, ParameterValues params, boolean negate) {
        this(type, params, negate, null);
    }
}
