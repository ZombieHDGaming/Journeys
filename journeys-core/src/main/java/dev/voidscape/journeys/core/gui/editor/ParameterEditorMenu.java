package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.ElementType;
import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.QuestDraft.ElementDraft;
import dev.voidscape.journeys.core.editor.QuestDraft.ParameterizedDraft;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.gui.PaginatedMenu;
import dev.voidscape.journeys.core.text.Text;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Edits the parameters of any objective, condition or reward, driven entirely by the type's declared
 * {@link Parameter}s. This is what lets third-party types work in the editor without extra code.
 */
final class ParameterEditorMenu extends PaginatedMenu<Parameter<?>> {

    private final JourneysContext ctx;
    private final ParameterizedDraft target;
    private final ElementType type;

    ParameterEditorMenu(Player viewer, @Nullable Menu parent, JourneysContext ctx, ParameterizedDraft target, ElementType type) {
        super(viewer, parent);
        this.ctx = ctx;
        this.target = target;
        this.type = type;
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>Parameters » ").append(type.displayName());
    }

    @Override
    protected int rows() {
        return Math.min(6, Math.max(2, (type.parameters().size() + 8) / 9 + 1));
    }

    @Override
    protected List<Parameter<?>> entries() {
        return type.parameters();
    }

    @Override
    protected Button entry(Parameter<?> parameter, int index) {
        String value = target.params.get(parameter.id());
        Items item = Items.of(parameter.type().icon()).name(Text.parse("<yellow>").append(parameter.name()))
                .lore("<dark_gray>" + parameter.id() + " · " + parameter.type().inputHint());
        if (value != null) {
            item.lore(Text.parse("<white>").append(Component.text(value)));
        } else if (parameter.defaultValue() != null) {
            item.lore("<gray>Not set, default: <white>" + Text.escape(parameter.defaultValue()));
        } else {
            item.lore(parameter.required() ? "<red>Required, not set" : "<gray>Not set");
        }
        item.lore("");
        if (parameter.type().isToggle()) {
            item.lore(EditorMenu.hint("Left-click", "toggle"));
        } else {
            item.lore(EditorMenu.hint("Left-click", "set"));
        }
        if (!parameter.required()) {
            item.lore(EditorMenu.hint("Right-click", "clear"));
        }
        item.glow(value != null);

        return item.button(click -> {
            if (click.isRight() && !parameter.required()) {
                target.params.remove(parameter.id());
                refresh();
            } else if (parameter.type().isToggle()) {
                String current = value != null ? value : parameter.defaultValue();
                boolean on = current != null && ParameterTypes.BOOLEAN.parse(current);
                target.params.put(parameter.id(), Boolean.toString(!on));
                refresh();
            } else {
                ask(parameter, value);
            }
        });
    }

    /** Conditions also get a player-facing description, shown on the quest board's requirement checklist. */
    @Override
    protected void controls() {
        if (!(target instanceof ElementDraft condition) || !(type instanceof ConditionType)) {
            return;
        }
        set(bottom() + 4, Items.of(Material.WRITABLE_BOOK).name("<yellow>Description for players")
                .lore(condition.description == null ? Component.text("(generated: ").append(type.displayName()).append(Component.text(")"))
                        : Text.parse(condition.description))
                .lore("<dark_gray>Shown on the quest board, e.g. 'Reach the Miner rank'")
                .lore("").lore(EditorMenu.hint("Left-click", "set (MiniMessage)")).lore(EditorMenu.hint("Right-click", "clear"))
                .button(click -> {
                    if (click.isRight()) {
                        condition.description = null;
                        refresh();
                        return;
                    }
                    ctx.prompts().ask(viewer, Text.parse("<gold>» </gold>Type the requirement as players should see it (MiniMessage)"), input -> {
                        try {
                            ParameterTypes.TEXT.parse(input);
                            condition.description = input;
                        } catch (IllegalArgumentException e) {
                            viewer.sendMessage(Text.parse("<red>✖ </red>").append(Component.text(String.valueOf(e.getMessage()))));
                        }
                        open();
                    }, this::open);
                }));
    }

    private void ask(Parameter<?> parameter, @Nullable String current) {
        Component question = Text.parse("<gold>» </gold>Enter ").append(parameter.name())
                .append(Text.parse(" <gray>(" + Text.escape(parameter.type().inputHint()) + ")"));
        if (current != null) {
            question = question.appendNewline().append(Text.parse("<gray>Current: ")
                    .append(Component.text(current).clickEvent(ClickEvent.suggestCommand(current))));
        }
        ctx.prompts().ask(viewer, question, input -> {
            try {
                parameter.type().parse(input);
                target.params.put(parameter.id(), input);
            } catch (IllegalArgumentException e) {
                viewer.sendMessage(Text.parse("<red>✖ </red>").append(Component.text(String.valueOf(e.getMessage()))));
            }
            open();
        }, this::open);
    }
}
