package dev.voidscape.journeys.core.config;

import dev.voidscape.journeys.api.quest.QuestIds;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Quest board categories from {@code categories.yml}. Quests pick a category by id; categories that are not listed
 * still work and fall back to the id as their name.
 */
public final class Categories {

    private static final String FILE = "categories.yml";

    /**
     * @param name        MiniMessage name
     * @param description MiniMessage lines
     * @param order       sort order on the board, lowest first
     */
    public record Category(String id, String name, Key icon, List<String> description, int order) {
    }

    private final Plugin plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public Categories(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        categories.clear();
        ConfigurationSection section = yaml.getConfigurationSection("categories");
        if (section == null) {
            return;
        }
        int index = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            String normalized = id.toLowerCase(Locale.ROOT);
            if (entry == null || !QuestIds.isValid(normalized)) {
                plugin.getLogger().warning(FILE + ": skipping invalid category '" + id + "'");
                continue;
            }
            Key icon;
            try {
                icon = Key.key(entry.getString("icon", "minecraft:book"));
            } catch (InvalidKeyException e) {
                plugin.getLogger().warning(FILE + ": category " + id + " has an invalid icon; using a book");
                icon = Key.key("minecraft:book");
            }
            categories.put(normalized, new Category(normalized, entry.getString("name", normalized), icon,
                    entry.getStringList("description"), entry.getInt("order", index)));
            index++;
        }
    }

    /** The configured category, or a default one named after the id. */
    public Category get(String id) {
        Category category = categories.get(id);
        return category != null ? category : new Category(id, "<white>" + id, Key.key("minecraft:book"), List.of(), Integer.MAX_VALUE);
    }

    /** Sorts category ids by configured order, then by id. */
    public List<Category> sorted(Iterable<String> ids) {
        List<Category> result = new ArrayList<>();
        ids.forEach(id -> result.add(get(id)));
        result.sort(Comparator.comparingInt(Category::order).thenComparing(Category::id));
        return result;
    }

    public List<String> ids() {
        return List.copyOf(categories.keySet());
    }
}
