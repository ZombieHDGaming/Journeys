package dev.voidscape.journeys.core.gui.player;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.progress.QuestProgress;
import dev.voidscape.journeys.api.quest.BranchMode;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestStage;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.ConfirmMenu;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.PaginatedMenu;
import dev.voidscape.journeys.core.text.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** The player's quest journal: active quests with objective progress, or their history. */
public final class QuestLogMenu extends PaginatedMenu<QuestLogMenu.Entry> {

    record Entry(QuestHolder holder, QuestDefinition quest, QuestProgress progress) {
    }

    private final JourneysContext ctx;
    private boolean history;

    public QuestLogMenu(JourneysContext ctx, Player viewer) {
        super(viewer, null);
        this.ctx = ctx;
    }

    @Override
    protected boolean live() {
        return true;
    }

    @Override
    protected Component title() {
        return ctx.messages().get(history ? "gui.log.title-history" : "gui.log.title-active");
    }

    @Override
    protected List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (QuestHolder holder : ctx.holders().holdersOf(viewer.getUniqueId())) {
            for (QuestProgress progress : ctx.engine().allProgress(holder)) {
                Optional<QuestDefinition> quest = ctx.quests().get(progress.questId());
                if (quest.isEmpty() || quest.get().settings().hidden()) {
                    continue;
                }
                boolean active = progress.status().isActive();
                if (active != history) {
                    entries.add(new Entry(holder, quest.get(), progress));
                }
            }
        }
        entries.sort(Comparator.comparing((Entry e) -> e.progress().startedAt()).reversed());
        return entries;
    }

    @Override
    protected Button entry(Entry entry, int index) {
        QuestDefinition quest = entry.quest();
        QuestProgress progress = entry.progress();
        Items item = Items.of(quest.icon()).name(Text.parse(quest.displayName()));
        item.lore(Text.items(quest.description()));

        if (history) {
            item.lore(Component.empty());
            item.lore(ctx.messages().lines("gui.log.history-entry",
                    Text.unparsed("status", progress.status().name().toLowerCase(java.util.Locale.ROOT)),
                    Text.unparsed("completions", Integer.toString(progress.completions()))));
            return item.display();
        }

        Optional<QuestStage> stage = progress.currentStage().flatMap(quest::stage);
        boolean choicePending = false;
        if (stage.isPresent()) {
            item.lore(Component.empty());
            item.lore(ctx.messages().item("gui.log.stage", Text.parsed("stage", stage.get().displayName())));
            for (ConfiguredObjective objective : stage.get().objectives()) {
                int goal = ctx.engine().texts().goal(objective);
                int current = Math.min(goal, progress.objectiveProgress(objective.id()));
                item.lore(ctx.messages().item(current >= goal ? "gui.log.objective-done" : "gui.log.objective",
                        Text.component("objective", ctx.engine().texts().objective(objective)),
                        Text.unparsed("progress", Integer.toString(current)),
                        Text.unparsed("goal", Integer.toString(goal))));
            }
            choicePending = progress.stageComplete() && stage.get().branchMode() == BranchMode.CHOICE;
            if (choicePending) {
                item.lore(Component.empty()).lore(ctx.messages().item("gui.log.choice-pending"));
                item.glow(true);
            }
        }
        boolean abandonable = quest.settings().abandonable();
        if (abandonable) {
            item.lore(ctx.messages().item("gui.log.abandon-hint"));
        }

        boolean choice = choicePending;
        return item.button(click -> {
            if (click.isShiftRight() && abandonable) {
                new ConfirmMenu(viewer, this, ctx.messages().get("gui.log.abandon-confirm", Text.parsed("quest", quest.displayName())), () -> {
                    ctx.engine().abandon(entry.holder(), quest.id());
                    open();
                }).open();
            } else if (choice) {
                new ChoiceMenu(ctx, viewer, this, entry.holder(), quest).open();
            }
        });
    }

    @Override
    protected void controls() {
        set(bottom() + 4, Items.of(history ? Material.WRITABLE_BOOK : Material.BOOK)
                .name(ctx.messages().item(history ? "gui.log.show-active" : "gui.log.show-history"))
                .button(click -> {
                    history = !history;
                    open(); // Re-open: the title changes with the tab.
                }));
        set(bottom() + 6, Items.of(Material.MAP).name(ctx.messages().item("gui.log.open-board"))
                .button(click -> QuestBoardMenu.open(ctx, viewer, null, this)));
    }
}
