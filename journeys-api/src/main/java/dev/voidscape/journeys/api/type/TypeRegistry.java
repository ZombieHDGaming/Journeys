package dev.voidscape.journeys.api.type;

import java.util.Collection;
import java.util.Optional;
import net.kyori.adventure.key.Key;

/**
 * Registry of element types. Thread-safe; registration normally happens during plugin enable.
 *
 * <p>Quests that reference a type which is not registered fail validation and are not loaded, so register your types
 * before Journeys loads quests (declare Journeys as a dependency and register in {@code onEnable}), or call
 * {@code /journeys reload} afterwards.</p>
 */
public interface TypeRegistry<T extends ElementType> {

    /**
     * Registers a type.
     *
     * @throws IllegalArgumentException if a type with the same key is already registered
     */
    void register(T type);

    /** Removes a type. Returns false if it was not registered. */
    boolean unregister(Key key);

    Optional<T> get(Key key);

    /** All registered types, in registration order. */
    Collection<T> all();
}
