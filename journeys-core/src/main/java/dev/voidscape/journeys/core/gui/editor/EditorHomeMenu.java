package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestIds;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.PaginatedMenu;
import dev.voidscape.journeys.core.quest.QuestValidator;
import dev.voidscape.journeys.core.text.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Entry point of the editor: every quest, plus creating new ones and resuming an unsaved draft. */
public final class EditorHomeMenu extends PaginatedMenu<EditorHomeMenu.Entry> {

    /** A loaded quest, or a quest file that failed to load. */
    record Entry(String id, Optional<QuestDefinition> quest, List<QuestValidator.Issue> issues) {
    }

    private final JourneysContext ctx;

    public EditorHomeMenu(JourneysContext ctx, Player viewer) {
        super(viewer, null);
        this.ctx = ctx;
    }

    /** Opens the editor for a quest directly, as {@code /journeys editor <quest>} does. */
    public static void openQuest(JourneysContext ctx, Player player, QuestDefinition quest) {
        EditorHomeMenu home = new EditorHomeMenu(ctx, player);
        home.edit(quest);
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>Journeys » Quests");
    }

    @Override
    protected List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        Map<String, List<QuestValidator.Issue>> problems = ctx.quests().problems();
        for (QuestDefinition quest : ctx.quests().all()) {
            entries.add(new Entry(quest.id(), Optional.of(quest), problems.getOrDefault(quest.id(), List.of())));
        }
        problems.forEach((id, issues) -> {
            if (ctx.quests().get(id).isEmpty()) {
                entries.add(new Entry(id, Optional.empty(), issues));
            }
        });
        entries.sort(Comparator.comparing(Entry::id));
        return entries;
    }

    @Override
    protected Button entry(Entry entry, int index) {
        if (entry.quest().isEmpty()) {
            Items item = Items.of(Material.BARRIER).name("<red>" + entry.id() + " <dark_red>(failed to load)");
            entry.issues().stream().limit(8).forEach(issue -> item.lore(Component.text(issue.toString())));
            item.lore("").lore("<gray>Fix the file and run <white>/journeys reload");
            return item.display();
        }
        QuestDefinition quest = entry.quest().get();
        Items item = Items.of(quest.icon()).name(Text.parse(quest.displayName()))
                .lore("<dark_gray>" + quest.id())
                .lore("<gray>" + quest.stages().size() + " stage(s)");
        long warnings = entry.issues().size();
        if (warnings > 0) {
            item.lore("<yellow>" + warnings + " warning(s)");
        }
        ctx.editor().session(viewer.getUniqueId())
                .filter(s -> s.originalId.equals(quest.id()))
                .ifPresent(s -> item.lore("<aqua>You have unsaved changes").glow(true));
        if (ctx.editor().isLocked(quest.id()) && ctx.editor().session(viewer.getUniqueId()).filter(s -> s.originalId.equals(quest.id())).isEmpty()) {
            item.lore("<red>Being edited by someone else");
        }
        item.lore("").lore(hint("Click", "edit"));
        return item.button(click -> edit(quest));
    }

    private void edit(QuestDefinition quest) {
        Optional<EditorSessions.Session> current = ctx.editor().session(viewer.getUniqueId());
        if (current.isPresent() && !current.get().originalId.equals(quest.id())) {
            viewer.sendMessage(Text.parse("<yellow>Discarded your unsaved draft of <white>" + current.get().originalId));
            ctx.editor().discard(viewer.getUniqueId());
        }
        switch (ctx.editor().edit(viewer.getUniqueId(), quest)) {
            case EditorSessions.OpenResult.Opened(EditorSessions.Session session) -> new QuestEditorMenu(ctx, session, viewer, this).open();
            case EditorSessions.OpenResult.Locked(java.util.UUID by) -> {
                OfflinePlayer other = Bukkit.getOfflinePlayer(by);
                viewer.sendMessage(Text.parse("<red>" + (other.getName() == null ? "Someone" : other.getName()) + " is editing this quest."));
            }
        }
    }

    @Override
    protected void controls() {
        set(bottom() + 4, Items.of(Material.EMERALD).name("<green><bold>Create quest")
                .lore(hint("Click", "enter a new quest id"))
                .button(click -> createQuest()));

        ctx.editor().session(viewer.getUniqueId()).filter(s -> s.isNew).ifPresent(session ->
                set(bottom() + 6, Items.of(Material.WRITABLE_BOOK).name("<aqua>Resume new quest <white>" + session.originalId)
                        .lore(hint("Click", "continue editing the unsaved quest"))
                        .button(click -> new QuestEditorMenu(ctx, session, viewer, this).open())));
    }

    private void createQuest() {
        viewer.closeInventory();
        ctx.prompts().ask(viewer, Text.parse("<gold>» </gold>Type an id for the new quest (lowercase letters, digits, _ and -)"), input -> {
            String id = input.toLowerCase(java.util.Locale.ROOT);
            if (!QuestIds.isValid(id)) {
                viewer.sendMessage(Text.parse("<red>✖ '" + Text.escape(input) + "' is not a valid id."));
                open();
                return;
            }
            if (ctx.quests().exists(id) || ctx.editor().isLocked(id)) {
                viewer.sendMessage(Text.parse("<red>✖ A quest with that id already exists."));
                open();
                return;
            }
            ctx.editor().discard(viewer.getUniqueId());
            new QuestEditorMenu(ctx, ctx.editor().create(viewer.getUniqueId(), id), viewer, this).open();
        }, this::open);
    }

    private static String hint(String click, String action) {
        return EditorMenu.hint(click, action);
    }
}
