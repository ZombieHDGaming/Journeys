package dev.voidscape.journeys.core.config;

import dev.voidscape.journeys.core.text.PlaceholderService;
import dev.voidscape.journeys.core.text.Text;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Player-facing messages from {@code messages.yml}, written in MiniMessage.
 *
 * <p>Missing keys fall back to the bundled defaults, so upgrading never leaves blank messages. Every message can use
 * {@code <prefix>}. An empty string disables a message.</p>
 */
public final class Messages {

    private static final String FILE = "messages.yml";

    private final Plugin plugin;
    private final Logger logger;
    private final PlaceholderService placeholders;
    private final Map<String, String> raw = new HashMap<>();
    private TagResolver prefix = TagResolver.empty();

    public Messages(Plugin plugin, PlaceholderService placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
        this.logger = plugin.getLogger();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(FILE)) {
            if (in != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            logger.warning("Could not read bundled " + FILE + ": " + e.getMessage());
        }
        raw.clear();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                Object value = config.get(key);
                raw.put(key, value instanceof List<?> list ? String.join("\n", list.stream().map(String::valueOf).toList()) : String.valueOf(value));
            }
        }
        prefix = Placeholder.parsed("prefix", raw.getOrDefault("prefix", ""));
    }

    /** Renders a message. Unknown keys render as the key itself so mistakes are visible. */
    public Component get(String key, TagResolver... resolvers) {
        return get(null, key, resolvers);
    }

    /** Renders a message with the viewer's external placeholders (e.g. PlaceholderAPI) filled in. */
    public Component get(@Nullable OfflinePlayer viewer, String key, TagResolver... resolvers) {
        String message = raw.get(key);
        if (message == null) {
            return Component.text(key);
        }
        return placeholders.parse(viewer, message, TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
    }

    /** Renders a message for item names or lore, without the default italics. */
    public Component item(String key, TagResolver... resolvers) {
        return item(null, key, resolvers);
    }

    public Component item(@Nullable OfflinePlayer viewer, String key, TagResolver... resolvers) {
        return Text.noItalic(get(viewer, key, resolvers));
    }

    /** Renders a multi-line message as separate lines, e.g. for lore. */
    public List<Component> lines(String key, TagResolver... resolvers) {
        return lines(null, key, resolvers);
    }

    public List<Component> lines(@Nullable OfflinePlayer viewer, String key, TagResolver... resolvers) {
        String message = raw.getOrDefault(key, key);
        if (message.isEmpty()) {
            return List.of();
        }
        TagResolver all = TagResolver.resolver(prefix, TagResolver.resolver(resolvers));
        return message.lines().map(line -> Text.noItalic(placeholders.parse(viewer, line, all))).toList();
    }

    public boolean isEnabled(String key) {
        String message = raw.get(key);
        return message == null || !message.isEmpty();
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        if (isEnabled(key)) {
            audience.sendMessage(get(audience instanceof OfflinePlayer player ? player : null, key, resolvers));
        }
    }

    public void actionBar(Audience audience, String key, TagResolver... resolvers) {
        if (isEnabled(key)) {
            audience.sendActionBar(get(audience instanceof OfflinePlayer player ? player : null, key, resolvers));
        }
    }
}
