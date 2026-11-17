package dev.voidscape.journeys.api.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;

/** Built-in {@link ParameterType}s. */
public final class ParameterTypes {

    private ParameterTypes() {
    }

    /** Any single line of text. */
    public static final ParameterType<String> STRING = simple("string", "any text", Key.key("minecraft:paper"), raw -> raw);

    /**
     * MiniMessage formatted text. Parsed leniently, as everywhere else in the plugin, so unclosed tags such as
     * {@code <green>Hello} are fine; anything MiniMessage itself cannot parse is rejected.
     */
    public static final ParameterType<String> TEXT = simple("text", "MiniMessage text, e.g. <green>Hello", Key.key("minecraft:writable_book"), raw -> {
        try {
            MiniMessage.miniMessage().deserialize(raw);
        } catch (ParsingException e) {
            throw new IllegalArgumentException("Invalid MiniMessage: " + e.getMessage());
        }
        return raw;
    });

    public static final ParameterType<Integer> INTEGER = simple("integer", "a whole number", Key.key("minecraft:repeater"), raw -> {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a whole number");
        }
    });

    public static final ParameterType<Integer> POSITIVE_INTEGER = simple("positive_integer", "a whole number above 0", Key.key("minecraft:repeater"), raw -> {
        int value = INTEGER.parse(raw);
        if (value <= 0) {
            throw new IllegalArgumentException("Must be greater than 0");
        }
        return value;
    });

    public static final ParameterType<Double> DECIMAL = simple("decimal", "a number, e.g. 2.5", Key.key("minecraft:comparator"), raw -> {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a number");
        }
    });

    public static final ParameterType<Boolean> BOOLEAN = new ParameterType<>() {
        @Override
        public String id() {
            return "boolean";
        }

        @Override
        public Boolean parse(String raw) {
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on" -> true;
                case "false", "no", "off" -> false;
                default -> throw new IllegalArgumentException("Expected true or false");
            };
        }

        @Override
        public Key icon() {
            return Key.key("minecraft:lever");
        }

        @Override
        public boolean isToggle() {
            return true;
        }
    };

    /** A namespaced key such as {@code minecraft:stone}. */
    public static final ParameterType<Key> KEY = simple("key", "a namespaced key, e.g. minecraft:stone", Key.key("minecraft:tripwire_hook"), raw -> {
        NamespacedKey key = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
        if (key == null) {
            throw new IllegalArgumentException("'" + raw + "' is not a valid key");
        }
        return key;
    });

    /** An item material. */
    public static final ParameterType<Material> ITEM = registryMaterial("item", "an item id, e.g. diamond", Key.key("minecraft:chest"), Material::isItem);

    /** A block material. */
    public static final ParameterType<Material> BLOCK = registryMaterial("block", "a block id, e.g. oak_log", Key.key("minecraft:grass_block"), Material::isBlock);

    public static final ParameterType<EntityType> ENTITY_TYPE = simple("entity_type", "an entity id, e.g. zombie", Key.key("minecraft:zombie_head"), raw -> {
        NamespacedKey key = KEY.parse(raw) instanceof NamespacedKey nk ? nk : null;
        EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
        if (type == null) {
            throw new IllegalArgumentException("Unknown entity type '" + raw + "'");
        }
        return type;
    });

    /**
     * A comma separated list of another type, e.g. {@code zombie, husk}. Parses to an unmodifiable list.
     */
    public static <T> ParameterType<List<T>> listOf(ParameterType<T> element) {
        return new ParameterType<>() {
            @Override
            public String id() {
                return "list_of_" + element.id();
            }

            @Override
            public List<T> parse(String raw) {
                List<T> values = new ArrayList<>();
                for (String part : raw.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        values.add(element.parse(trimmed));
                    }
                }
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("Expected at least one value");
                }
                return Collections.unmodifiableList(values);
            }

            @Override
            public Key icon() {
                return element.icon();
            }

            @Override
            public String inputHint() {
                return "one or more of " + element.inputHint() + ", separated by commas";
            }
        };
    }

    /** Creates a parameter type from a parse function. */
    public static <T> ParameterType<T> simple(String id, String hint, Key icon, Function<String, T> parser) {
        return new ParameterType<>() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public T parse(String raw) {
                return parser.apply(raw);
            }

            @Override
            public Key icon() {
                return icon;
            }

            @Override
            public String inputHint() {
                return hint;
            }
        };
    }

    private static ParameterType<Material> registryMaterial(String id, String hint, Key icon, java.util.function.Predicate<Material> filter) {
        return simple(id, hint, icon, raw -> {
            Key key = KEY.parse(raw);
            Material material = Registry.MATERIAL.get((NamespacedKey) key);
            if (material == null || !filter.test(material)) {
                throw new IllegalArgumentException("Unknown " + id + " '" + raw + "'");
            }
            return material;
        });
    }
}
