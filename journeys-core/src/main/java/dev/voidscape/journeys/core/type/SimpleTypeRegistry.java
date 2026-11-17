package dev.voidscape.journeys.core.type;

import dev.voidscape.journeys.api.type.ElementType;
import dev.voidscape.journeys.api.type.TypeRegistry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;

/**
 * Insertion-ordered registry. Hooks let the plugin register Bukkit listeners for types that implement
 * {@link org.bukkit.event.Listener} and unregister them again.
 */
public final class SimpleTypeRegistry<T extends ElementType> implements TypeRegistry<T> {

    private final String kind;
    private final Map<Key, T> types = new LinkedHashMap<>();
    private final Consumer<T> onRegister;
    private final Consumer<T> onUnregister;

    public SimpleTypeRegistry(String kind, Consumer<T> onRegister, Consumer<T> onUnregister) {
        this.kind = kind;
        this.onRegister = onRegister;
        this.onUnregister = onUnregister;
    }

    @Override
    public synchronized void register(T type) {
        Objects.requireNonNull(type, "type");
        if (types.putIfAbsent(type.key(), type) != null) {
            throw new IllegalArgumentException("A " + kind + " type with key " + type.key().asString() + " is already registered");
        }
        onRegister.accept(type);
    }

    @Override
    public synchronized boolean unregister(Key key) {
        T removed = types.remove(key);
        if (removed == null) {
            return false;
        }
        onUnregister.accept(removed);
        return true;
    }

    @Override
    public synchronized Optional<T> get(Key key) {
        return Optional.ofNullable(types.get(key));
    }

    @Override
    public synchronized Collection<T> all() {
        return List.copyOf(types.values());
    }

    /** Unregisters everything; used on plugin disable. */
    public synchronized void clear() {
        for (T type : List.copyOf(types.values())) {
            unregister(type.key());
        }
    }
}
