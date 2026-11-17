package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.quest.BranchMode;
import dev.voidscape.journeys.api.quest.ObjectiveMode;
import dev.voidscape.journeys.api.quest.QuestIds;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.editor.QuestDraft.StageDraft;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.text.Text;
import dev.voidscape.journeys.core.type.Registries;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Edits one stage: text, how objectives combine, how the next stage is chosen, and the lists below it. */
final class StageEditorMenu extends EditorMenu {

    private final StageDraft stage;

    StageEditorMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent, StageDraft stage) {
        super(ctx, session, viewer, parent);
        this.stage = stage;
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>" + session.originalId + " » " + stage.id);
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void build() {
        set(10, Items.of(Material.NAME_TAG).name("<yellow>Display name").lore(Text.parse(stage.displayName)).lore("")
                .lore(hint("Click", "change (MiniMessage)"))
                .button(click -> prompt("Type the stage's display name (MiniMessage)", stage.displayName, input -> {
                    ParameterTypes.TEXT.parse(input);
                    stage.displayName = input;
                    return null;
                })));
        set(11, descriptionButton(stage.description, this::refresh));
        set(12, Items.of(Material.PAPER).name("<yellow>Stage id").lore("<gray>" + stage.id).lore("")
                .lore(hint("Click", "rename; transitions are updated"))
                .button(click -> prompt("Type a new id for this stage", stage.id, input -> {
                    String id = input.toLowerCase(Locale.ROOT);
                    if (!QuestIds.isValid(id)) {
                        return "Ids may only contain lowercase letters, digits, _ and -";
                    }
                    if (draft().stages.containsKey(id)) {
                        return "A stage with that id already exists";
                    }
                    draft().renameStage(stage.id, id);
                    return null;
                })));

        set(14, Items.of(Material.HOPPER).name("<yellow>Objective mode")
                .lore("<white>" + stage.objectiveMode.name().toLowerCase(Locale.ROOT))
                .lore("<dark_gray>all: every objective, any order")
                .lore("<dark_gray>any: one objective is enough")
                .lore("<dark_gray>sequential: in listed order")
                .lore("").lore(hint("Click", "cycle"))
                .button(click -> {
                    ObjectiveMode[] modes = ObjectiveMode.values();
                    stage.objectiveMode = modes[(stage.objectiveMode.ordinal() + 1) % modes.length];
                    refresh();
                }));
        set(15, Items.of(stage.branchMode == BranchMode.CHOICE ? Material.OAK_SIGN : Material.REDSTONE_TORCH).name("<yellow>Branching")
                .lore("<white>" + stage.branchMode.name().toLowerCase(Locale.ROOT))
                .lore("<dark_gray>automatic: first transition whose conditions pass")
                .lore("<dark_gray>choice: the player picks a transition")
                .lore("").lore(hint("Click", "toggle"))
                .button(click -> {
                    stage.branchMode = stage.branchMode == BranchMode.AUTOMATIC ? BranchMode.CHOICE : BranchMode.AUTOMATIC;
                    refresh();
                }));

        set(29, Items.of(Material.TARGET).name("<gold><bold>Objectives").lore("<gray>" + stage.objectives.size() + " objective(s)")
                .lore("").lore(hint("Click", "edit"))
                .button(click -> new ObjectiveListMenu(ctx, session, viewer, this, stage).open()));
        set(31, Items.of(Material.TRIPWIRE_HOOK).name("<gold><bold>Transitions")
                .lore(stage.transitions.isEmpty() ? "<gray>None: the quest completes after this stage" : "<gray>" + stage.transitions.size() + " transition(s)")
                .lore("").lore(hint("Click", "edit branches"))
                .button(click -> new TransitionListMenu(ctx, session, viewer, this, stage).open()));
        set(33, Items.of(Material.CHEST).name("<gold><bold>Stage rewards").lore("<gray>" + stage.rewards.size() + " reward(s)")
                .lore("").lore(hint("Click", "edit"))
                .button(click -> new ElementListMenu(ctx, session, viewer, this, Registries.Kind.REWARD, stage.rewards, "Stage rewards").open()));

        backButton(36);
        fillBottomRow();
    }
}
