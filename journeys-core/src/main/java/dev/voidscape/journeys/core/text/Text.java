package dev.voidscape.journeys.core.text;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** MiniMessage helpers shared across the plugin. */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    public static MiniMessage mini() {
        return MINI;
    }

    public static Component parse(String miniMessage, TagResolver... resolvers) {
        return MINI.deserialize(miniMessage, resolvers);
    }

    /** Parses text for item names and lore, where Minecraft would otherwise render it in italics. */
    public static Component item(String miniMessage, TagResolver... resolvers) {
        return noItalic(parse(miniMessage, resolvers));
    }

    public static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> items(List<String> lines, TagResolver... resolvers) {
        return lines.stream().map(line -> item(line, resolvers)).toList();
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Escapes user input so it is shown literally when placed into MiniMessage. */
    public static String escape(String input) {
        return MINI.escapeTags(input);
    }

    public static TagResolver unparsed(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    /** Inserts a MiniMessage string as formatted text, e.g. a quest's display name. */
    public static TagResolver parsed(String key, String miniMessage) {
        return Placeholder.component(key, parse(miniMessage));
    }

    public static TagResolver component(String key, Component value) {
        return Placeholder.component(key, value);
    }
}
