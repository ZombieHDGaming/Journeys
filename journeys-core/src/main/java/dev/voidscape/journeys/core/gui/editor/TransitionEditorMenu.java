package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.editor.QuestDraft.StageDraft;
import dev.voidscape.journeys.core.editor.QuestDraft.TransitionDraft;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.text.Text;
import dev.voidscape.journeys.core.type.Registries;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Edits one transition: where it leads, its label for choice stages, and its conditions. */
final class TransitionEditorMenu extends EditorMenu {

    private final StageDraft stage;
    private final TransitionDraft transition;

    TransitionEditorMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent,
                         StageDraft stage, TransitionDraft transition) {
        super(ctx, session, viewer, parent);
        this.stage = stage;
        this.transition = transition;
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>" + stage.id + " → " + transition.target);
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        set(11, Items.of(Material.COMPASS).name("<yellow>Leads to").lore("<white>" + transition.target).lore("")
                .lore(hint("Click", "change"))
                .button(click -> new StagePickerMenu(ctx, session, viewer, this, true, target -> {
                    transition.target = target;
                    open();
                }).open()));
        set(13, Items.of(Material.OAK_SIGN).name("<yellow>Label")
                .lore(transition.label == null ? Component.text("(none)") : Text.parse(transition.label))
                .lore("<dark_gray>Shown to players on choice stages").lore("")
                .lore(hint("Left-click", "set (MiniMessage)")).lore(hint("Right-click", "clear"))
                .button(click -> {
                    if (click.isRight()) {
                        transition.label = null;
                        refresh();
                    } else {
                        prompt("Type the choice label (MiniMessage)", transition.label, input -> {
                            ParameterTypes.TEXT.parse(input);
                            transition.label = input;
                            return null;
                        });
                    }
                }));
        set(15, Items.of(Material.TRIPWIRE_HOOK).name("<gold><bold>Conditions").lore("<gray>" + transition.conditions.size() + " condition(s)")
                .lore("<dark_gray>All must pass for this transition").lore("")
                .lore(hint("Click", "edit"))
                .button(click -> new ElementListMenu(ctx, session, viewer, this, Registries.Kind.CONDITION, transition.conditions, "Conditions").open()));
        backButton(18);
        fillBottomRow();
    }
}
