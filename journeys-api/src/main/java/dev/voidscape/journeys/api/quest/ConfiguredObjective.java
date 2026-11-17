package dev.voidscape.journeys.api.quest;

import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;

/**
 * An objective placed in a stage.
 *
 * @param id          unique within the stage; progress is stored under this id
 * @param type        key of the {@link dev.voidscape.journeys.api.type.ObjectiveType}
 * @param amount      goal, at least 1
 * @param params      type parameters
 * @param description optional MiniMessage text replacing the type's generated description
 */
public record ConfiguredObjective(String id, Key type, int amount, ParameterValues params, @Nullable String description) {

    public ConfiguredObjective {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(params, "params");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1");
        }
    }
}
