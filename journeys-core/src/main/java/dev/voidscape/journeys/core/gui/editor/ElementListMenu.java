package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.type.ElementType;
import dev.voidscape.journeys.api.type.ParameterValues;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.editor.QuestDraft.ElementDraft;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.gui.PaginatedMenu;
import dev.voidscape.journeys.core.quest.QuestTexts;
import dev.voidscape.journeys.core.text.Text;
import dev.voidscape.journeys.core.type.Registries;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** A list of conditions or rewards, edited in place. */
final class ElementListMenu extends PaginatedMenu<ElementDraft> {

    private final JourneysContext ctx;
    private final EditorSessions.Session session;
    private final Registries.Kind kind;
    private final List<ElementDraft> elements;
    private final String name;

    ElementListMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent,
                    Registries.Kind kind, List<ElementDraft> elements, String name) {
        super(viewer, parent);
        this.ctx = ctx;
        this.session = session;
        this.kind = kind;
        this.elements = elements;
        this.name = name;
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>" + session.originalId + " » " + name);
    }

    @Override
    protected List<ElementDraft> entries() {
        return List.copyOf(elements);
    }

    @Override
    protected Button entry(ElementDraft element, int index) {
        boolean condition = kind == Registries.Kind.CONDITION;
        Optional<? extends ElementType> type = ctx.registries().of(kind).get(element.type);
        Component description = QuestTexts.describe(ctx.registries().of(kind), element.type, ParameterValues.of(element.params));
        Items item = type.<Items>map(t -> Items.of(t.icon())).orElseGet(() -> Items.of(Material.BARRIER))
                .name(condition && element.negate ? Text.parse("<red>NOT </red>").append(description) : description)
                .lore("<dark_gray>" + element.type.asString());
        if (condition && element.description != null) {
            item.lore(Text.parse("<gray>Players see: ").append(Text.parse(element.description)));
        }
        element.params.forEach((key, value) -> item.lore(Component.text(key + " = " + value)));
        if (type.isEmpty()) {
            item.lore("<red>Unknown type");
        }
        item.lore("").lore(EditorMenu.hint("Left-click", "edit parameters"));
        if (condition) {
            item.lore(EditorMenu.hint("Right-click", "toggle NOT"));
        }
        item.lore(EditorMenu.hint("Shift-left-click", "move up")).lore(EditorMenu.hint("Shift-right-click", "remove"));
        return item.button(click -> {
            if (click.isShiftRight()) {
                elements.remove(element);
                refresh();
            } else if (click.isShiftLeft()) {
                if (index > 0) {
                    Collections.swap(elements, index, index - 1);
                }
                refresh();
            } else if (click.isRight() && condition) {
                element.negate = !element.negate;
                refresh();
            } else if (type.isPresent()) {
                new ParameterEditorMenu(viewer, this, ctx, element, type.get()).open();
            }
        });
    }

    @Override
    protected void controls() {
        String noun = kind == Registries.Kind.CONDITION ? "condition" : "reward";
        set(bottom() + 4, Items.of(Material.EMERALD).name("<green><bold>Add " + noun).lore(EditorMenu.hint("Click", "pick a type"))
                .button(click -> new TypePickerMenu(viewer, this, ctx.registries(), kind, type -> {
                    ElementDraft element = new ElementDraft(type.key());
                    elements.add(element);
                    if (type.parameters().isEmpty()) {
                        open();
                    } else {
                        new ParameterEditorMenu(viewer, this, ctx, element, type).open();
                    }
                }).open()));
    }
}
