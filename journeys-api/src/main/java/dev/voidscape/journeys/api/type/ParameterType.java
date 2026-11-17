package dev.voidscape.journeys.api.type;

import java.util.List;
import net.kyori.adventure.key.Key;

/**
 * Parses and validates the raw string form of a parameter.
 *
 * <p>Parameter values are stored as strings everywhere (YAML, editor drafts) and only parsed when needed. This keeps
 * serialization uniform and lets the editor accept free text input for any type.</p>
 *
 * @param <T> parsed value type
 */
public interface ParameterType<T> {

    /** Stable id, e.g. {@code integer}, used in editor hints. */
    String id();

    /**
     * Parses a raw value.
     *
     * @throws IllegalArgumentException with a user-facing message when the value is invalid
     */
    T parse(String raw);

    /** Icon used for this parameter in the editor. */
    default Key icon() {
        return Key.key("minecraft:name_tag");
    }

    /** Short hint shown when prompting for a value, e.g. "a whole number". */
    default String inputHint() {
        return id();
    }

    /** Example values offered in prompts. Empty when there are none worth listing. */
    default List<String> examples() {
        return List.of();
    }

    /** Whether the editor should toggle this value on click instead of prompting for text. */
    default boolean isToggle() {
        return false;
    }
}
