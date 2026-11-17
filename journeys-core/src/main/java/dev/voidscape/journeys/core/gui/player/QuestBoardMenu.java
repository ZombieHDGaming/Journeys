package dev.voidscape.journeys.core.gui.player;

import dev.voidscape.journeys.api.progress.StartResult;
import dev.voidscape.journeys.api.quest.BoardSettings;
import dev.voidscape.journeys.api.quest.ConfiguredCondition;
import dev.voidscape.journeys.api.quest.ConfiguredReward;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.engine.QuestEngine;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.gui.PaginatedMenu;
import dev.voidscape.journeys.core.text.Text;
import dev.voidscape.journeys.core.util.Durations;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Lists the quests of one category (or all of them) with their status for the viewer: available, active, on
 * cooldown, locked with a requirement checklist, or completed. Available quests can be started by clicking them.
 */
public final class QuestBoardMenu extends PaginatedMenu<BoardEntries.Entry> {

    /** Opens the right first page: the category list when more than one category has quests, else the quests. */
    public static void open(JourneysContext ctx, Player player, @Nullable String category, @Nullable Menu parent) {
        if (category != null) {
            new QuestBoardMenu(ctx, player, parent, category).open();
            return;
        }
        Set<String> categories = new LinkedHashSet<>();
        for (BoardEntries.Entry entry : BoardEntries.visible(ctx, player, null)) {
            categories.add(entry.quest().settings().board().category());
        }
        if (categories.size() > 1) {
            new CategoryMenu(ctx, player, parent).open();
        } else {
            new QuestBoardMenu(ctx, player, parent, categories.isEmpty() ? null : categories.iterator().next()).open();
        }
    }

    private final JourneysContext ctx;
    private final @Nullable String category;

    QuestBoardMenu(JourneysContext ctx, Player viewer, @Nullable Menu parent, @Nullable String category) {
        super(viewer, parent);
        this.ctx = ctx;
        this.category = category;
    }

    @Override
    protected boolean live() {
        return true;
    }

    @Override
    protected Component title() {
        if (category == null) {
            return ctx.messages().get(viewer, "gui.board.title");
        }
        return ctx.messages().get(viewer, "gui.board.title-category", ctx.placeholders().parsed(viewer, "category", ctx.categories().get(category).name()));
    }

    @Override
    protected List<BoardEntries.Entry> entries() {
        return BoardEntries.visible(ctx, viewer, category);
    }

    @Override
    protected Button entry(BoardEntries.Entry entry, int index) {
        QuestDefinition quest = entry.quest();
        boolean startable = quest.settings().board().mode() == BoardSettings.BoardMode.START;
        Items item = (entry.status() == BoardEntries.Status.LOCKED ? Items.of(Material.IRON_BARS) : Items.of(quest.icon()))
                .name(ctx.placeholders().parse(viewer, quest.displayName()))
                .lore(ctx.placeholders().items(viewer, quest.description()))
                .lore(Component.empty())
                .glow(entry.status() == BoardEntries.Status.AVAILABLE && startable);

        switch (entry.status()) {
            case AVAILABLE -> item.lore(ctx.messages().item(startable ? "gui.board.available" : "gui.board.available-elsewhere"));
            case ACTIVE -> item.lore(ctx.messages().item(viewer, "gui.board.active"));
            case COOLDOWN -> item.lore(ctx.messages().item(viewer, "gui.board.cooldown", Text.unparsed("time",
                    Durations.format(ctx.engine().cooldownRemaining(quest, entry.holder()).withNanos(0)))));
            case COMPLETED -> item.lore(ctx.messages().item(viewer, "gui.board.completed"));
            case LOADING -> item.lore(ctx.messages().item(viewer, "gui.board.loading"));
            case LOCKED -> {
                item.lore(ctx.messages().item(viewer, "gui.board.locked"));
                item.lore(requirementChecklist(entry));
            }
        }

        if (!quest.rewards().isEmpty() && entry.status() != BoardEntries.Status.COMPLETED) {
            item.lore(Component.empty()).lore(ctx.messages().item(viewer, "gui.board.rewards-header"));
            for (ConfiguredReward reward : quest.rewards()) {
                item.lore(ctx.messages().item(viewer, "gui.board.reward", Text.component("reward", ctx.engine().texts().reward(reward))));
            }
        }

        return item.button(click -> {
            switch (entry.status()) {
                case AVAILABLE -> {
                    if (!startable) {
                        return;
                    }
                    StartResult result = ctx.engine().start(entry.holder(), quest.id(), viewer, true);
                    if (result.success()) {
                        viewer.closeInventory();
                    } else {
                        refresh();
                    }
                }
                case ACTIVE -> new QuestLogMenu(ctx, viewer).open();
                default -> {
                }
            }
        });
    }

    private List<Component> requirementChecklist(BoardEntries.Entry entry) {
        QuestDefinition quest = entry.quest();
        List<Component> lines = new ArrayList<>();
        if (quest.settings().requirePermission()) {
            boolean met = QuestEngine.hasQuestPermission(quest, viewer);
            lines.add(ctx.messages().item(met ? "gui.board.requirement-met" : "gui.board.requirement-missing",
                    Text.component("requirement", ctx.messages().get(viewer, "gui.board.permission-requirement"))));
        }
        for (ConfiguredCondition condition : quest.requirements()) {
            boolean met = ctx.engine().test(condition, viewer, entry.holder());
            Component text = condition.description() != null
                    ? ctx.placeholders().parse(viewer, condition.description())
                    : ctx.engine().texts().condition(condition);
            lines.add(ctx.messages().item(met ? "gui.board.requirement-met" : "gui.board.requirement-missing",
                    Text.component("requirement", text)));
        }
        return lines;
    }

    @Override
    protected void controls() {
        set(bottom() + 4, Items.of(Material.WRITABLE_BOOK).name(ctx.messages().item(viewer, "gui.board.open-log"))
                .button(click -> new QuestLogMenu(ctx, viewer).open()));
        if (entries().isEmpty()) {
            set(22, Items.of(Material.PAPER).name(ctx.messages().item(viewer, "gui.board.empty")).display());
        }
    }
}
