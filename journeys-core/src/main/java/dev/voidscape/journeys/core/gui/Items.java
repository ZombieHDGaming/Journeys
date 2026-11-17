package dev.voidscape.journeys.core.gui;

import dev.voidscape.journeys.core.text.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/** Fluent builder for menu items. All text is rendered without the default item italics. */
public final class Items {

    private final ItemStack stack;
    private Component name;
    private final List<Component> lore = new ArrayList<>();
    private boolean glow;

    private Items(Material material) {
        this.stack = ItemStack.of(material.isItem() && !material.isAir() ? material : Material.PAPER);
        this.name = Component.empty();
    }

    public static Items of(Material material) {
        return new Items(material);
    }

    /** Resolves an item key, falling back to paper for unknown keys or non-items. */
    public static Items of(Key key) {
        return new Items(material(key));
    }

    public static Material material(Key key) {
        Material material = Registry.MATERIAL.get(new NamespacedKey(key.namespace(), key.value()));
        return material == null || !material.isItem() || material.isAir() ? Material.PAPER : material;
    }

    public Items name(Component name) {
        this.name = Text.noItalic(name);
        return this;
    }

    /** Name from MiniMessage. */
    public Items name(String miniMessage) {
        return name(Text.parse(miniMessage));
    }

    public Items lore(Component line) {
        lore.add(Text.noItalic(line));
        return this;
    }

    public Items lore(String miniMessage) {
        return lore(Text.parse(miniMessage));
    }

    public Items lore(List<Component> lines) {
        lines.forEach(this::lore);
        return this;
    }

    public Items amount(int amount) {
        stack.setAmount(Math.clamp(amount, 1, stack.getMaxStackSize()));
        return this;
    }

    public Items glow(boolean glow) {
        this.glow = glow;
        return this;
    }

    public ItemStack build() {
        stack.editMeta(meta -> {
            meta.displayName(name);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }

    public Button button(Button.Action action) {
        return Button.of(build(), action);
    }

    public Button display() {
        return Button.display(build());
    }
}
