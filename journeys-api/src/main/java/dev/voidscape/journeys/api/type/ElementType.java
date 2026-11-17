package dev.voidscape.journeys.api.type;

import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.text.Component;

/**
 * Common shape of every pluggable quest element: objectives, conditions and rewards.
 *
 * <p>Types describe their configuration through {@link #parameters()}. The quest editor GUI, the YAML loader and
 * the validator all work from that description, so a new type needs no editor code of its own.</p>
 */
public interface ElementType extends Keyed {

    /** Unique key, e.g. {@code journeys:kill_entity} or {@code myplugin:catch_fish}. */
    @Override
    Key key();

    /** Name shown in the editor. */
    Component displayName();

    /** Extra lines shown in the editor's type picker. */
    default List<Component> description() {
        return List.of();
    }

    /** Item key used as the icon in the editor, e.g. {@code minecraft:iron_sword}. */
    Key icon();

    /** Parameters this type accepts. */
    List<Parameter<?>> parameters();

    /**
     * Human-readable summary of a configured instance, used in quest logs and editor lore,
     * e.g. "Kill 10 zombie". The default is the display name.
     */
    default Component describe(ParameterValues values) {
        return displayName();
    }
}
