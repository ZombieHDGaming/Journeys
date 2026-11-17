package dev.voidscape.journeys.core.gui;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * A menu listing entries over pages. The last row holds navigation; subclasses add their own controls there with
 * {@link #controls()} using slots 46 to 52 (or the equivalent in smaller menus).
 */
public abstract class PaginatedMenu<T> extends Menu {

    private int page;

    protected PaginatedMenu(Player viewer, @Nullable Menu parent) {
        super(viewer, parent);
    }

    @Override
    protected int rows() {
        return 6;
    }

    protected abstract List<T> entries();

    protected abstract Button entry(T value, int index);

    /** Extra buttons in the bottom row. */
    protected void controls() {
    }

    /** First slot of the bottom row. */
    protected final int bottom() {
        return (rows() - 1) * 9;
    }

    @Override
    protected final void build() {
        List<T> entries = entries();
        int perPage = (rows() - 1) * 9;
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);

        int from = page * perPage;
        for (int i = from; i < Math.min(entries.size(), from + perPage); i++) {
            set(i - from, entry(entries.get(i), i));
        }

        int bottom = bottom();
        backButton(bottom);
        if (page > 0) {
            set(bottom + 3, Items.of(Material.SPECTRAL_ARROW).name("<yellow>Previous page")
                    .lore("<gray>Page " + page + " of " + pages)
                    .button(click -> {
                        page--;
                        refresh();
                    }));
        }
        if (page < pages - 1) {
            set(bottom + 5, Items.of(Material.SPECTRAL_ARROW).name("<yellow>Next page")
                    .lore("<gray>Page " + (page + 2) + " of " + pages)
                    .button(click -> {
                        page++;
                        refresh();
                    }));
        }
        controls();
        fillBottomRow();
    }
}
