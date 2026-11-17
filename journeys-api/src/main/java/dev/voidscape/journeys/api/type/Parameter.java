package dev.voidscape.journeys.api.type;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * One configurable value of an {@link ElementType}.
 *
 * @param id           key used in YAML and in {@link ParameterValues}; lowercase, e.g. {@code entity}
 * @param name         label shown in the editor
 * @param type         how the raw string is parsed and validated
 * @param required     whether a value must be set for the quest to validate
 * @param defaultValue raw value used when nothing is set; only meaningful for optional parameters
 * @param <T>          parsed value type
 */
public record Parameter<T>(
        String id,
        Component name,
        ParameterType<T> type,
        boolean required,
        @Nullable String defaultValue
) {

    public Parameter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }

    public static <T> Parameter<T> required(String id, Component name, ParameterType<T> type) {
        return new Parameter<>(id, name, type, true, null);
    }

    public static <T> Parameter<T> optional(String id, Component name, ParameterType<T> type, @Nullable String defaultValue) {
        return new Parameter<>(id, name, type, false, defaultValue);
    }
}
