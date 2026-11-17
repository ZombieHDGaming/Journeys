package dev.voidscape.journeys.core.gui.player;

import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.config.Categories;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.gui.PaginatedMenu;
import dev.voidscape.journeys.core.text.Text;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** First page of the board when quests span several categories. */
final class CategoryMenu extends PaginatedMenu<Categories.Category> {

    private final JourneysContext ctx;
    /** Category id to {available, total}, recomputed on each build. */
    private final Map<String, int[]> counts = new LinkedHashMap<>();

    CategoryMenu(JourneysContext ctx, Player viewer, @Nullable Menu parent) {
        super(viewer, parent);
        this.ctx = ctx;
    }

    @Override
    protected boolean live() {
        return true;
    }

    @Override
    protected Component title() {
        return ctx.messages().get(viewer, "gui.board.title");
    }

    @Override
    protected List<Categories.Category> entries() {
        counts.clear();
        for (BoardEntries.Entry entry : BoardEntries.visible(ctx, viewer, null)) {
            int[] count = counts.computeIfAbsent(entry.quest().settings().board().category(), id -> new int[2]);
            if (entry.status() == BoardEntries.Status.AVAILABLE) {
                count[0]++;
            }
            count[1]++;
        }
        return ctx.categories().sorted(counts.keySet());
    }

    @Override
    protected Button entry(Categories.Category category, int index) {
        int[] count = counts.getOrDefault(category.id(), new int[2]);
        return Items.of(category.icon())
                .name(ctx.placeholders().parse(viewer, category.name()))
                .lore(ctx.placeholders().items(viewer, category.description()))
                .lore(Component.empty())
                .lore(ctx.messages().item(viewer, "gui.board.category-count",
                        Text.unparsed("available", Integer.toString(count[0])),
                        Text.unparsed("total", Integer.toString(count[1]))))
                .glow(count[0] > 0)
                .button(click -> new QuestBoardMenu(ctx, viewer, this, category.id()).open());
    }
}
