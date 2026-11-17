package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.quest.BranchMode;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.editor.QuestDraft.StageDraft;
import dev.voidscape.journeys.core.editor.QuestDraft.TransitionDraft;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.gui.PaginatedMenu;
import dev.voidscape.journeys.core.text.Text;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Outgoing transitions of a stage. For automatic stages the first one whose conditions pass wins. */
final class TransitionListMenu extends PaginatedMenu<TransitionDraft> {

    private final JourneysContext ctx;
    private final EditorSessions.Session session;
    private final StageDraft stage;

    TransitionListMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent, StageDraft stage) {
        super(viewer, parent);
        this.ctx = ctx;
        this.session = session;
        this.stage = stage;
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>" + stage.id + " » Transitions");
    }

    @Override
    protected List<TransitionDraft> entries() {
        return List.copyOf(stage.transitions);
    }

    @Override
    protected Button entry(TransitionDraft transition, int index) {
        Items item = Items.of(Material.ARROW).name("<yellow>#" + (index + 1) + " → <white>" + transition.target);
        if (transition.label != null) {
            item.lore(Text.parse("<gray>Label: ").append(Text.parse(transition.label)));
        } else if (stage.branchMode == BranchMode.CHOICE) {
            item.lore("<red>No label (players see the target id)");
        }
        item.lore("<gray>" + transition.conditions.size() + " condition(s)")
                .lore("")
                .lore(EditorMenu.hint("Left-click", "edit"))
                .lore(EditorMenu.hint("Shift-left-click", "move up (higher priority)"))
                .lore(EditorMenu.hint("Shift-right-click", "remove"));
        return item.button(click -> {
            if (click.isShiftRight()) {
                stage.transitions.remove(transition);
                refresh();
            } else if (click.isShiftLeft()) {
                if (index > 0) {
                    Collections.swap(stage.transitions, index, index - 1);
                }
                refresh();
            } else {
                new TransitionEditorMenu(ctx, session, viewer, this, stage, transition).open();
            }
        });
    }

    @Override
    protected void controls() {
        set(bottom() + 4, Items.of(Material.EMERALD).name("<green><bold>Add transition").lore(EditorMenu.hint("Click", "pick where it leads"))
                .button(click -> new StagePickerMenu(ctx, session, viewer, this, true, target -> {
                    TransitionDraft transition = new TransitionDraft(target);
                    stage.transitions.add(transition);
                    new TransitionEditorMenu(ctx, session, viewer, this, stage, transition).open();
                }).open()));
    }
}
