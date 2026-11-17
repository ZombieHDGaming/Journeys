package dev.voidscape.journeys.core.gui.player;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.StageTransition;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.text.Text;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Lets a player pick the next branch of a choice stage. */
public final class ChoiceMenu extends Menu {

    private final JourneysContext ctx;
    private final QuestHolder holder;
    private final QuestDefinition quest;

    public ChoiceMenu(JourneysContext ctx, Player viewer, @Nullable Menu parent, QuestHolder holder, QuestDefinition quest) {
        super(viewer, parent);
        this.ctx = ctx;
        this.holder = holder;
        this.quest = quest;
    }

    @Override
    protected boolean live() {
        return true;
    }

    @Override
    protected Component title() {
        return ctx.messages().get(viewer, "gui.choice.title", ctx.engine().questName(quest, viewer));
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        Map<Integer, StageTransition> choices = ctx.engine().availableChoices(holder, quest.id(), viewer);
        int slot = 9 + Math.max(0, (9 - choices.size()) / 2);
        for (Map.Entry<Integer, StageTransition> choice : choices.entrySet()) {
            String label = choice.getValue().label() == null ? choice.getValue().target().serialize() : choice.getValue().label();
            int index = choice.getKey();
            set(slot++, Items.of(Material.OAK_SIGN).name(ctx.placeholders().parse(viewer, label))
                    .lore(ctx.messages().item(viewer, "gui.choice.hint"))
                    .button(click -> {
                        viewer.closeInventory();
                        if (!ctx.engine().choose(holder, quest.id(), index, viewer)) {
                            ctx.messages().send(viewer, "command.choose-failed");
                        }
                    }));
        }
        backButton(18);
    }
}
