package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.quest.QuestIds;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.editor.QuestDraft;
import dev.voidscape.journeys.core.editor.QuestDraft.StageDraft;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.ConfirmMenu;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.gui.PaginatedMenu;
import dev.voidscape.journeys.core.text.Text;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** All stages of the quest being edited. */
final class StageListMenu extends PaginatedMenu<StageDraft> {

    private final JourneysContext ctx;
    private final EditorSessions.Session session;

    StageListMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent) {
        super(viewer, parent);
        this.ctx = ctx;
        this.session = session;
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>" + session.originalId + " » Stages");
    }

    @Override
    protected List<StageDraft> entries() {
        return List.copyOf(session.draft.stages.values());
    }

    @Override
    protected Button entry(StageDraft stage, int index) {
        QuestDraft draft = session.draft;
        boolean start = draft.startStage.equals(stage.id);
        Items item = Items.of(start ? Material.LIME_BANNER : Material.WHITE_BANNER)
                .name(Text.parse(stage.displayName))
                .lore("<dark_gray>" + stage.id + (start ? " <green>(start)" : ""))
                .lore("<gray>" + stage.objectives.size() + " objective(s), " + stage.objectiveMode.name().toLowerCase(Locale.ROOT))
                .glow(start);
        if (stage.transitions.isEmpty()) {
            item.lore("<gray>Then: <gold>complete the quest");
        } else {
            item.lore("<gray>Then (" + stage.branchMode.name().toLowerCase(Locale.ROOT) + "):");
            stage.transitions.forEach(t -> item.lore("<gray>  → <white>" + t.target));
        }
        item.lore("")
                .lore(EditorMenu.hint("Left-click", "edit"))
                .lore(EditorMenu.hint("Right-click", "make this the start stage"))
                .lore(EditorMenu.hint("Shift-right-click", "delete"));
        return item.button(click -> {
            if (click.isShiftRight()) {
                new ConfirmMenu(viewer, this, Text.parse("<red>Delete stage " + stage.id + "?"), () -> {
                    draft.removeStage(stage.id);
                    open();
                }).open();
            } else if (click.isRight()) {
                draft.startStage = stage.id;
                refresh();
            } else {
                new StageEditorMenu(ctx, session, viewer, this, stage).open();
            }
        });
    }

    @Override
    protected void controls() {
        set(bottom() + 4, Items.of(Material.EMERALD).name("<green><bold>Add stage").lore(EditorMenu.hint("Click", "enter a stage id"))
                .button(click -> {
                    viewer.closeInventory();
                    ctx.prompts().ask(viewer, Text.parse("<gold>» </gold>Type an id for the new stage"), input -> {
                        String id = input.toLowerCase(Locale.ROOT);
                        if (!QuestIds.isValid(id)) {
                            viewer.sendMessage(Text.parse("<red>✖ Ids may only contain lowercase letters, digits, _ and -"));
                            open();
                        } else if (session.draft.stages.containsKey(id)) {
                            viewer.sendMessage(Text.parse("<red>✖ That stage already exists."));
                            open();
                        } else {
                            StageDraft stage = new StageDraft(id);
                            session.draft.stages.put(id, stage);
                            new StageEditorMenu(ctx, session, viewer, this, stage).open();
                        }
                    }, this::open);
                }));
    }
}
