package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.quest.TransitionTarget;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.gui.PaginatedMenu;
import dev.voidscape.journeys.core.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Picks a stage id, optionally also offering "complete quest" and "fail quest" as targets. */
final class StagePickerMenu extends PaginatedMenu<String> {

    private final EditorSessions.Session session;
    private final boolean includeEnds;
    private final Consumer<String> onPick;

    StagePickerMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent,
                    boolean includeEnds, Consumer<String> onPick) {
        super(viewer, parent);
        this.session = session;
        this.includeEnds = includeEnds;
        this.onPick = onPick;
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>Pick " + (includeEnds ? "target" : "stage"));
    }

    @Override
    protected List<String> entries() {
        List<String> entries = new ArrayList<>(session.draft.stages.keySet());
        if (includeEnds) {
            entries.add(TransitionTarget.COMPLETE_TOKEN);
            entries.add(TransitionTarget.FAIL_TOKEN);
        }
        return entries;
    }

    @Override
    protected Button entry(String id, int index) {
        Items item = switch (id) {
            case TransitionTarget.COMPLETE_TOKEN -> Items.of(Material.EMERALD_BLOCK).name("<green><bold>Complete the quest");
            case TransitionTarget.FAIL_TOKEN -> Items.of(Material.REDSTONE_BLOCK).name("<red><bold>Fail the quest");
            default -> Items.of(Material.WHITE_BANNER).name(Text.parse(session.draft.stages.get(id).displayName)).lore("<dark_gray>" + id);
        };
        return item.button(click -> onPick.accept(id));
    }
}
