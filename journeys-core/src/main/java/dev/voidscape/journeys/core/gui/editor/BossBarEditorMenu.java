package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.quest.BossBarSettings;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.text.Text;
import java.util.Locale;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Boss bar settings of the quest being edited. */
final class BossBarEditorMenu extends EditorMenu {

    BossBarEditorMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent) {
        super(ctx, session, viewer, parent);
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>" + session.originalId + " » Boss bar");
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        BossBarSettings bar = draft().bossBar;

        set(10, Items.of(bar.enabled() ? Material.LIME_DYE : Material.GRAY_DYE).name("<yellow>Show a boss bar")
                .lore(bar.enabled() ? "<green>Yes" : "<red>No")
                .lore("<dark_gray>Shown while this is the player's tracked quest")
                .lore("").lore(hint("Click", "toggle"))
                .button(click -> set(new BossBarSettings(!bar.enabled(), bar.color(), bar.overlay(), bar.mode()))));

        set(12, Items.of(dye(bar.color())).name("<yellow>Color").lore("<white>" + lower(bar.color()))
                .lore("").lore(hint("Click", "cycle"))
                .button(click -> set(new BossBarSettings(bar.enabled(), next(bar.color()), bar.overlay(), bar.mode()))));

        set(14, Items.of(Material.IRON_BARS).name("<yellow>Style").lore("<white>" + lower(bar.overlay()))
                .lore("<dark_gray>progress is solid; notched_N splits it into N segments")
                .lore("").lore(hint("Click", "cycle"))
                .button(click -> set(new BossBarSettings(bar.enabled(), bar.color(), next(bar.overlay()), bar.mode()))));

        set(16, Items.of(bar.mode() == BossBarSettings.Mode.ALWAYS ? Material.SUNFLOWER : Material.CLOCK).name("<yellow>Visibility")
                .lore("<white>" + lower(bar.mode()).replace('_', '-'))
                .lore("<dark_gray>always: visible while the quest is active")
                .lore("<dark_gray>on-progress: appears briefly when it progresses")
                .lore("").lore(hint("Click", "toggle"))
                .button(click -> set(new BossBarSettings(bar.enabled(), bar.color(), bar.overlay(),
                        bar.mode() == BossBarSettings.Mode.ALWAYS ? BossBarSettings.Mode.ON_PROGRESS : BossBarSettings.Mode.ALWAYS))));

        backButton(18);
        fillBottomRow();
    }

    private void set(BossBarSettings settings) {
        draft().bossBar = settings;
        refresh();
    }

    private static <E extends Enum<E>> E next(E value) {
        E[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static Material dye(BossBar.Color color) {
        return switch (color) {
            case PINK -> Material.PINK_DYE;
            case BLUE -> Material.BLUE_DYE;
            case RED -> Material.RED_DYE;
            case GREEN -> Material.GREEN_DYE;
            case YELLOW -> Material.YELLOW_DYE;
            case PURPLE -> Material.PURPLE_DYE;
            case WHITE -> Material.WHITE_DYE;
        };
    }
}
