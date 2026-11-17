package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.type.ElementType;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.gui.PaginatedMenu;
import dev.voidscape.journeys.core.text.Text;
import dev.voidscape.journeys.core.type.Registries;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Lists every registered type of a kind, including ones added by other plugins. */
final class TypePickerMenu extends PaginatedMenu<ElementType> {

    private final Registries registries;
    private final Registries.Kind kind;
    private final Consumer<ElementType> onPick;

    TypePickerMenu(Player viewer, @Nullable Menu parent, Registries registries, Registries.Kind kind, Consumer<ElementType> onPick) {
        super(viewer, parent);
        this.registries = registries;
        this.kind = kind;
        this.onPick = onPick;
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>Pick " + kind.name().toLowerCase(Locale.ROOT) + " type");
    }

    @Override
    protected List<ElementType> entries() {
        return List.copyOf(registries.of(kind).all());
    }

    @Override
    protected Button entry(ElementType type, int index) {
        Items item = Items.of(type.icon()).name(Text.parse("<yellow>").append(type.displayName()))
                .lore("<dark_gray>" + type.key().asString())
                .lore(type.description());
        if (!type.parameters().isEmpty()) {
            item.lore("").lore("<gray>Parameters:");
            type.parameters().forEach(p -> item.lore(Text.parse("<gray> • ").append(p.name())
                    .append(Text.parse(p.required() ? " <red>*" : ""))));
        }
        return item.button(click -> onPick.accept(type));
    }
}
