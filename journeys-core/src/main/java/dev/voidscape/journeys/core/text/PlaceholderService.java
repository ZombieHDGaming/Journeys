package dev.voidscape.journeys.core.text;

import dev.voidscape.journeys.api.text.Placeholders;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.Nullable;

/**
 * Applies external placeholders before MiniMessage parsing. Placeholders are filled in for the player who will see
 * the text, so the same quest name can read differently per player.
 */
public final class PlaceholderService implements Placeholders {

    private final Logger logger;
    private volatile @Nullable BiFunction<OfflinePlayer, String, String> resolver;
    private volatile boolean warned;

    public PlaceholderService(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String apply(@Nullable OfflinePlayer player, String text) {
        BiFunction<OfflinePlayer, String, String> current = resolver;
        if (current == null || player == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            String result = current.apply(player, text);
            return result == null ? text : result;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                logger.log(Level.WARNING, "Placeholder resolver failed; showing text without placeholders", e);
            }
            return text;
        }
    }

    @Override
    public void setResolver(@Nullable BiFunction<OfflinePlayer, String, String> resolver) {
        this.resolver = resolver;
        this.warned = false;
    }

    @Override
    public boolean isAvailable() {
        return resolver != null;
    }

    /** Fills placeholders for the viewer, then parses MiniMessage. */
    public Component parse(@Nullable OfflinePlayer viewer, String miniMessage, TagResolver... resolvers) {
        return Text.parse(apply(viewer, miniMessage), resolvers);
    }

    /** Like {@link #parse} but without item italics, for item names and lore. */
    public Component item(@Nullable OfflinePlayer viewer, String miniMessage) {
        return Text.noItalic(parse(viewer, miniMessage));
    }

    public List<Component> items(@Nullable OfflinePlayer viewer, List<String> lines) {
        return lines.stream().map(line -> item(viewer, line)).toList();
    }

    /** A {@code <key>} tag holding MiniMessage text with the viewer's placeholders filled in. */
    public TagResolver parsed(@Nullable OfflinePlayer viewer, String key, String miniMessage) {
        return Placeholder.component(key, parse(viewer, miniMessage));
    }
}
