package dev.voidscape.journeys.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Yes/no dialog. Cancelling returns to the parent menu. */
public final class ConfirmMenu extends Menu {

    private final Component question;
    private final Runnable onConfirm;

    public ConfirmMenu(Player viewer, @Nullable Menu parent, Component question, Runnable onConfirm) {
        super(viewer, parent);
        this.question = question;
        this.onConfirm = onConfirm;
    }

    @Override
    protected Component title() {
        return question;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        set(11, Items.of(Material.LIME_CONCRETE).name("<green><bold>Confirm").button(click -> onConfirm.run()));
        set(13, Items.of(Material.PAPER).name(question).display());
        set(15, Items.of(Material.RED_CONCRETE).name("<red><bold>Cancel").button(click -> {
            if (parent != null) {
                parent.open();
            } else {
                viewer.closeInventory();
            }
        }));
    }
}
