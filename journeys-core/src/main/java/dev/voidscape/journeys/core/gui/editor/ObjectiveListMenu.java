package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.editor.QuestDraft.ObjectiveDraft;
import dev.voidscape.journeys.core.editor.QuestDraft.StageDraft;
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

/** Objectives of a stage. Order matters for sequential stages. */
final class ObjectiveListMenu extends PaginatedMenu<ObjectiveDraft> {

    private final JourneysContext ctx;
    private final EditorSessions.Session session;
    private final StageDraft stage;

    ObjectiveListMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent, StageDraft stage) {
        super(viewer, parent);
        this.ctx = ctx;
        this.session = session;
        this.stage = stage;
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>" + stage.id + " » Objectives");
    }

    @Override
    protected List<ObjectiveDraft> entries() {
        return List.copyOf(stage.objectives);
    }

    @Override
    protected Button entry(ObjectiveDraft objective, int index) {
        Optional<ObjectiveType> type = ctx.registries().objectives().get(objective.type);
        Items item = type.map(t -> Items.of(t.icon())).orElseGet(() -> Items.of(Material.BARRIER))
                .name(objective.description != null ? Text.parse(objective.description)
                        : QuestTexts.describe(ctx.registries().objectives(), objective.type, dev.voidscape.journeys.api.type.ParameterValues.of(objective.params)))
                .lore("<dark_gray>" + objective.id + " · " + objective.type.asString())
                .lore("<gray>Amount: <white>" + objective.amount);
        if (type.isEmpty()) {
            item.lore("<red>Unknown type");
        }
        item.lore("")
                .lore(EditorMenu.hint("Left-click", "edit"))
                .lore(EditorMenu.hint("Shift-left-click", "move up"))
                .lore(EditorMenu.hint("Shift-right-click", "remove"));
        return item.button(click -> {
            if (click.isShiftRight()) {
                stage.objectives.remove(objective);
                refresh();
            } else if (click.isShiftLeft()) {
                if (index > 0) {
                    Collections.swap(stage.objectives, index, index - 1);
                }
                refresh();
            } else {
                new ObjectiveEditorMenu(ctx, session, viewer, this, stage, objective).open();
            }
        });
    }

    @Override
    protected void controls() {
        set(bottom() + 4, Items.of(Material.EMERALD).name("<green><bold>Add objective").lore(EditorMenu.hint("Click", "pick a type"))
                .button(click -> new TypePickerMenu(viewer, this, ctx.registries(), Registries.Kind.OBJECTIVE, type -> {
                    ObjectiveDraft objective = new ObjectiveDraft(stage.nextObjectiveId(), type.key());
                    stage.objectives.add(objective);
                    new ObjectiveEditorMenu(ctx, session, viewer, this, stage, objective).open();
                }).open()));
    }
}
