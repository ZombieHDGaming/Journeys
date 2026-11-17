package dev.voidscape.journeys.core.gui;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.Nullable;

/**
 * A chest menu. Subclasses describe their contents in {@link #build()}, which runs again on every {@link #refresh()},
 * so menus always render current data instead of caching it.
 *
 * <p>Menus are identified by being the inventory's holder; {@link MenuListener} routes clicks and cancels every
 * interaction with the menu's slots.</p>
 */
public abstract class Menu implements InventoryHolder {

    protected final Player viewer;
    protected final @Nullable Menu parent;
    private final Map<Integer, Button> buttons = new HashMap<>();
    private @Nullable Inventory inventory;

    protected Menu(Player viewer, @Nullable Menu parent) {
        this.viewer = viewer;
        this.parent = parent;
    }

    protected abstract Component title();

    /** Number of rows, 1 to 6. */
    protected abstract int rows();

    /** Places buttons with {@link #set(int, Button)}. */
    protected abstract void build();

    /**
     * Whether this menu shows state that can change while it is open, and should be redrawn by {@link LiveMenus}.
     * Off by default; editor menus show the viewer's own draft and only change when they click.
     */
    protected boolean live() {
        return false;
    }

    public final void open() {
        inventory = Bukkit.createInventory(this, rows() * 9, title());
        render();
        viewer.openInventory(inventory);
    }

    /** Rebuilds the contents in place. */
    public final void refresh() {
        if (inventory == null) {
            open();
            return;
        }
        render();
    }

    private void render() {
        buttons.clear();
        build();
        Inventory inv = getInventory();
        inv.clear();
        buttons.forEach((slot, button) -> inv.setItem(slot, button.item()));
    }

    protected final void set(int slot, Button button) {
        if (slot >= 0 && slot < rows() * 9) {
            buttons.put(slot, button);
        }
    }

    /** Fills empty slots of the bottom row with a neutral pane. */
    protected final void fillBottomRow() {
        int start = (rows() - 1) * 9;
        Button filler = Items.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).display();
        for (int slot = start; slot < start + 9; slot++) {
            buttons.putIfAbsent(slot, filler);
        }
    }

    /** Adds a back button to the parent menu, or a close button for root menus. */
    protected final void backButton(int slot) {
        if (parent != null) {
            set(slot, Items.of(Material.ARROW).name("<gray>Back").button(click -> parent.open()));
        } else {
            set(slot, Items.of(Material.BARRIER).name("<red>Close").button(click -> viewer.closeInventory()));
        }
    }

    final void handleClick(InventoryClickEvent event) {
        Button button = buttons.get(event.getRawSlot());
        if (button != null && button.action() != null) {
            button.action().onClick(new Button.Click(viewer, event.getClick()));
        }
    }

    @Override
    public final Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Menu has not been opened");
        }
        return inventory;
    }
}
