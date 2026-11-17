package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.editor.QuestDraft;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.text.Text;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Base for editor menus: access to the draft being edited and chat prompts that return to this menu. */
abstract class EditorMenu extends Menu {

    protected final JourneysContext ctx;
    protected final EditorSessions.Session session;

    EditorMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent) {
        super(viewer, parent);
        this.ctx = ctx;
        this.session = session;
    }

    protected QuestDraft draft() {
        return session.draft;
    }

    /**
     * Asks for text in chat and reopens this menu afterwards.
     *
     * @param question MiniMessage question
     * @param current  current value, offered as click-to-insert so it can be tweaked instead of retyped
     * @param apply    applies the input and returns an error message, or null on success
     */
    protected void prompt(String question, @Nullable String current, Function<String, @Nullable String> apply) {
        Component message = Text.parse("<gold>» </gold>" + question);
        if (current != null && !current.isEmpty()) {
            message = message.appendNewline().append(Text.parse("<gray>Current: </gray>").append(Component.text(current)
                    .clickEvent(ClickEvent.suggestCommand(current))
                    .hoverEvent(HoverEvent.showText(Text.parse("<gray>Click to insert into chat")))));
        }
        ctx.prompts().ask(viewer, message, input -> {
            String error;
            try {
                error = apply.apply(input);
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
            if (error != null) {
                viewer.sendMessage(Text.parse("<red>✖ </red>").append(Component.text(error)));
            }
            open();
        }, this::open);
    }

    /** Shared editor for MiniMessage description lines. */
    protected Button descriptionButton(List<String> lines, Runnable after) {
        Items item = Items.of(Material.WRITABLE_BOOK).name("<yellow>Description");
        if (lines.isEmpty()) {
            item.lore("<dark_gray>(empty)");
        } else {
            item.lore(Text.items(lines));
        }
        item.lore("").lore(hint("Left-click", "add a line")).lore(hint("Right-click", "remove the last line"))
                .lore(hint("Shift-right-click", "clear"));
        return item.button(click -> {
            if (click.isShiftRight()) {
                lines.clear();
                after.run();
            } else if (click.isRight()) {
                if (!lines.isEmpty()) {
                    lines.removeLast();
                }
                after.run();
            } else {
                prompt("Type a description line (MiniMessage)", null, input -> {
                    ParameterTypes.TEXT.parse(input);
                    lines.add(input);
                    return null;
                });
            }
        });
    }

    /** Standard lore line explaining a click. */
    protected static String hint(String click, String action) {
        return "<dark_gray>" + click + ": <gray>" + action;
    }
}
