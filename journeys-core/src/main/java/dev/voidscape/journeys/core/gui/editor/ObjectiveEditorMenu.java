package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.quest.QuestIds;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.editor.QuestDraft.ObjectiveDraft;
import dev.voidscape.journeys.core.editor.QuestDraft.StageDraft;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.text.Text;
import dev.voidscape.journeys.core.type.Registries;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Edits one objective: type, goal amount, parameters and an optional custom description. */
final class ObjectiveEditorMenu extends EditorMenu {

    private final StageDraft stage;
    private final ObjectiveDraft objective;

    ObjectiveEditorMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent,
                        StageDraft stage, ObjectiveDraft objective) {
        super(ctx, session, viewer, parent);
        this.stage = stage;
        this.objective = objective;
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>" + stage.id + " » " + objective.id);
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void build() {
        Optional<ObjectiveType> type = ctx.registries().objectives().get(objective.type);

        set(10, type.map(t -> Items.of(t.icon()).name(t.displayName())).orElseGet(() -> Items.of(Material.BARRIER).name("<red>Unknown type"))
                .lore("<dark_gray>" + objective.type.asString()).lore("")
                .lore(hint("Click", "change type (clears parameters)"))
                .button(click -> new TypePickerMenu(viewer, this, ctx.registries(), Registries.Kind.OBJECTIVE, picked -> {
                    if (!picked.key().equals(objective.type)) {
                        objective.type = picked.key();
                        objective.params.clear();
                    }
                    open();
                }).open()));

        set(11, Items.of(Material.PAPER).name("<yellow>Objective id").lore("<gray>" + objective.id)
                .lore("<dark_gray>Progress is stored under this id").lore("")
                .lore(hint("Click", "rename"))
                .button(click -> prompt("Type a new objective id", objective.id, input -> {
                    String id = input.toLowerCase(Locale.ROOT);
                    if (!QuestIds.isValid(id)) {
                        return "Ids may only contain lowercase letters, digits, _ and -";
                    }
                    if (stage.objectives.stream().anyMatch(o -> o != objective && o.id.equals(id))) {
                        return "Another objective in this stage uses that id";
                    }
                    objective.id = id;
                    return null;
                })));

        boolean countable = type.map(ObjectiveType::countable).orElse(true);
        if (countable) {
            set(13, Items.of(Material.REPEATER).name("<yellow>Amount").lore("<white>" + objective.amount).lore("")
                    .lore(hint("Left-click", "type an amount"))
                    .lore(hint("Right-click", "+1"))
                    .lore(hint("Shift-right-click", "-1"))
                    .button(click -> {
                        if (click.isShiftRight()) {
                            objective.amount = Math.max(1, objective.amount - 1);
                            refresh();
                        } else if (click.isRight()) {
                            objective.amount++;
                            refresh();
                        } else {
                            prompt("Type the goal amount", Integer.toString(objective.amount), input -> {
                                objective.amount = ParameterTypes.POSITIVE_INTEGER.parse(input);
                                return null;
                            });
                        }
                    }));
        } else {
            set(13, Items.of(Material.GRAY_DYE).name("<gray>Amount").lore("<dark_gray>This objective is done once").display());
        }

        if (type.isPresent()) {
            ObjectiveType t = type.get();
            Items params = Items.of(Material.COMPARATOR).name("<gold><bold>Parameters");
            if (t.parameters().isEmpty()) {
                params.lore("<dark_gray>(this type has none)");
            }
            objective.params.forEach((key, value) -> params.lore(Component.text(key + " = " + value)));
            set(15, params.lore("").lore(hint("Click", "edit")).button(click ->
                    new ParameterEditorMenu(viewer, this, ctx, objective, t).open()));
        }

        set(16, Items.of(Material.WRITABLE_BOOK).name("<yellow>Custom description")
                .lore(objective.description == null ? Component.text("(generated from the type)") : Text.parse(objective.description))
                .lore("").lore(hint("Left-click", "set (MiniMessage)")).lore(hint("Right-click", "use the generated one"))
                .button(click -> {
                    if (click.isRight()) {
                        objective.description = null;
                        refresh();
                    } else {
                        prompt("Type the objective description shown to players (MiniMessage)", objective.description, input -> {
                            ParameterTypes.TEXT.parse(input);
                            objective.description = input;
                            return null;
                        });
                    }
                }));

        backButton(27);
        fillBottomRow();
    }
}
