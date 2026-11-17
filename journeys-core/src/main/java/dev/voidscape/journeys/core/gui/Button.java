package dev.voidscape.journeys.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** An item in a menu slot plus what happens when it is clicked. */
public record Button(ItemStack item, @Nullable Action action) {

    @FunctionalInterface
    public interface Action {
        void onClick(Click click);
    }

    /** Details of a click on a button. */
    public record Click(Player player, ClickType type) {
        public boolean isLeft() {
            return type.isLeftClick() && !type.isShiftClick();
        }

        public boolean isRight() {
            return type.isRightClick() && !type.isShiftClick();
        }

        public boolean isShiftLeft() {
            return type == ClickType.SHIFT_LEFT;
        }

        public boolean isShiftRight() {
            return type == ClickType.SHIFT_RIGHT;
        }
    }

    public static Button display(ItemStack item) {
        return new Button(item, null);
    }

    public static Button of(ItemStack item, Action action) {
        return new Button(item, action);
    }
}
