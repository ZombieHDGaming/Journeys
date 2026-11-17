package dev.voidscape.journeys.core.gui.editor;

import dev.voidscape.journeys.api.holder.HolderScope;
import dev.voidscape.journeys.api.quest.BoardSettings;
import dev.voidscape.journeys.api.quest.QuestIds;
import dev.voidscape.journeys.api.quest.QuestSettings;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.editor.QuestDraft;
import dev.voidscape.journeys.core.gui.Button;
import dev.voidscape.journeys.core.gui.ConfirmMenu;
import dev.voidscape.journeys.core.gui.Items;
import dev.voidscape.journeys.core.gui.Menu;
import dev.voidscape.journeys.core.quest.QuestValidator;
import dev.voidscape.journeys.core.text.Text;
import dev.voidscape.journeys.core.type.Registries;
import dev.voidscape.journeys.core.util.Durations;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Top-level editor for one quest: text, settings, stages, requirements and rewards, plus save and delete. */
public final class QuestEditorMenu extends EditorMenu {

    public QuestEditorMenu(JourneysContext ctx, EditorSessions.Session session, Player viewer, @Nullable Menu parent) {
        super(ctx, session, viewer, parent);
    }

    @Override
    protected Component title() {
        return Text.parse("<dark_gray>Edit » " + session.originalId);
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void build() {
        QuestDraft d = draft();
        List<QuestValidator.Issue> issues = ctx.editor().validate(session);
        long errors = issues.stream().filter(i -> i.severity() == QuestValidator.Severity.ERROR).count();

        Items summary = Items.of(d.icon).name(Text.parse(d.displayName))
                .lore("<dark_gray>" + d.id + (session.isNew ? " (new)" : ""))
                .lore(Text.items(d.description))
                .lore("")
                .lore(errors > 0 ? "<red>" + errors + " error(s), " + (issues.size() - errors) + " warning(s)"
                        : issues.isEmpty() ? "<green>No problems found" : "<yellow>" + issues.size() + " warning(s)");
        set(4, summary.display());

        // Text and appearance
        set(10, Items.of(Material.NAME_TAG).name("<yellow>Display name")
                .lore(Text.parse(d.displayName)).lore("").lore(hint("Click", "change (MiniMessage)"))
                .button(click -> prompt("Type the quest's display name (MiniMessage)", d.displayName, input -> {
                    ParameterTypes.TEXT.parse(input);
                    d.displayName = input;
                    return null;
                })));
        set(11, descriptionButton(d.description, () -> refresh()));
        set(12, Items.of(d.icon).name("<yellow>Icon").lore("<gray>" + d.icon.asString()).lore("")
                .lore(hint("Left-click", "use the item in your main hand"))
                .lore(hint("Right-click", "type an item id"))
                .button(click -> {
                    if (click.isRight()) {
                        prompt("Type an item id, e.g. minecraft:diamond", d.icon.asString(), input -> {
                            d.icon = ParameterTypes.ITEM.parse(input).key();
                            return null;
                        });
                        return;
                    }
                    ItemStack held = viewer.getInventory().getItemInMainHand();
                    if (held.getType().isAir()) {
                        viewer.sendMessage(Text.parse("<red>Hold an item in your main hand first."));
                        return;
                    }
                    d.icon = held.getType().key();
                    refresh();
                }));

        // Structure
        set(14, Items.of(Material.LECTERN).name("<yellow>Start stage").lore("<gray>" + d.startStage).lore("")
                .lore(hint("Click", "choose"))
                .button(click -> new StagePickerMenu(ctx, session, viewer, this, false, target -> {
                    d.startStage = target;
                    open();
                }).open()));
        set(15, Items.of(Material.FILLED_MAP).name("<gold><bold>Stages").lore("<gray>" + d.stages.size() + " stage(s)").lore("")
                .lore(hint("Click", "edit stages, objectives and branches"))
                .button(click -> new StageListMenu(ctx, session, viewer, this).open()));
        set(16, propertiesButton());

        // Settings
        set(19, toggle("Repeatable", d.repeatable, () -> d.repeatable = !d.repeatable));
        set(20, Items.of(Material.CLOCK).name("<yellow>Cooldown").lore("<gray>" + Durations.format(d.cooldown)).lore("")
                .lore(hint("Click", "change, e.g. 1d12h or 0"))
                .button(click -> prompt("Type a cooldown, e.g. 30m, 1d12h, or 0 for none", Durations.format(d.cooldown), input -> {
                    d.cooldown = Durations.parse(input);
                    return null;
                })));
        set(21, Items.of(Material.REPEATER).name("<yellow>Max completions")
                .lore("<gray>" + (d.maxCompletions == 0 ? "unlimited" : Integer.toString(d.maxCompletions))).lore("")
                .lore(hint("Click", "change, 0 for unlimited"))
                .button(click -> prompt("Type the maximum number of completions (0 = unlimited)", Integer.toString(d.maxCompletions), input -> {
                    int value = ParameterTypes.INTEGER.parse(input);
                    if (value < 0) {
                        return "Must be 0 or more";
                    }
                    d.maxCompletions = value;
                    return null;
                })));
        set(22, Items.of(d.scope == HolderScope.PARTY ? Material.PLAYER_HEAD : Material.ARMOR_STAND).name("<yellow>Progress scope")
                .lore("<gray>" + d.scope.name().toLowerCase(Locale.ROOT))
                .lore("<dark_gray>party: shared with the player's party, if a party plugin is hooked")
                .lore("").lore(hint("Click", "toggle"))
                .button(click -> {
                    d.scope = d.scope == HolderScope.SOLO ? HolderScope.PARTY : HolderScope.SOLO;
                    refresh();
                }));
        set(23, toggle("Players can abandon", d.abandonable, () -> d.abandonable = !d.abandonable));
        set(24, toggle("Hidden from quest log", d.hidden, () -> d.hidden = !d.hidden));

        // Requirements and rewards
        set(30, Items.of(Material.TRIPWIRE_HOOK).name("<gold><bold>Requirements").lore("<gray>" + d.requirements.size() + " condition(s)")
                .lore("").lore(hint("Click", "edit"))
                .button(click -> new ElementListMenu(ctx, session, viewer, this, Registries.Kind.CONDITION, d.requirements, "Requirements").open()));
        set(32, Items.of(Material.CHEST).name("<gold><bold>Completion rewards").lore("<gray>" + d.rewards.size() + " reward(s)")
                .lore("").lore(hint("Click", "edit"))
                .button(click -> new ElementListMenu(ctx, session, viewer, this, Registries.Kind.REWARD, d.rewards, "Rewards").open()));

        // Quest board and access
        set(37, Items.of(Material.BOOKSHELF).name("<yellow>Board category")
                .lore(Text.parse(ctx.categories().get(d.category).name())).lore("<dark_gray>" + d.category)
                .lore("<dark_gray>Names and icons are set in categories.yml").lore("")
                .lore(hint("Click", "change"))
                .button(click -> prompt("Type a category id, e.g. story, daily, mining"
                        + (ctx.categories().ids().isEmpty() ? "" : " <gray>(configured: " + String.join(", ", ctx.categories().ids()) + ")"),
                        d.category, input -> {
                    String id = input.toLowerCase(Locale.ROOT);
                    if (!QuestIds.isValid(id)) {
                        return "Category ids may only contain lowercase letters, digits, _ and -";
                    }
                    d.category = id;
                    return null;
                })));
        set(38, Items.of(switch (d.boardMode) {
                    case HIDDEN -> Material.GRAY_DYE;
                    case VIEW -> Material.ITEM_FRAME;
                    case START -> Material.MAP;
                }).name("<yellow>On the quest board")
                .lore("<white>" + d.boardMode.name().toLowerCase(Locale.ROOT))
                .lore("<dark_gray>hidden: not listed")
                .lore("<dark_gray>view: listed, started elsewhere (NPC, command)")
                .lore("<dark_gray>start: listed and started by clicking it")
                .lore("").lore(hint("Click", "cycle"))
                .button(click -> {
                    BoardSettings.BoardMode[] modes = BoardSettings.BoardMode.values();
                    d.boardMode = modes[(d.boardMode.ordinal() + 1) % modes.length];
                    refresh();
                }));
        set(39, toggle("Show on board while locked", d.showWhenLocked, () -> d.showWhenLocked = !d.showWhenLocked));
        set(41, Items.of(d.requirePermission ? Material.IRON_DOOR : Material.OAK_DOOR).name("<yellow>Require permission")
                .lore(d.requirePermission ? "<green>Yes" : "<red>No")
                .lore("<dark_gray>Players need " + QuestSettings.permissionNode(d.id))
                .lore("").lore(hint("Click", "toggle"))
                .button(click -> {
                    d.requirePermission = !d.requirePermission;
                    refresh();
                }));

        set(43, Items.of(Material.DRAGON_HEAD).name("<yellow>Boss bar")
                .lore(d.bossBar.enabled() ? "<green>Enabled" : "<red>Disabled")
                .lore("").lore(hint("Click", "edit"))
                .button(click -> new BossBarEditorMenu(ctx, session, viewer, this).open()));

        // Actions
        backButton(45);
        set(47, Items.of(Material.SPYGLASS).name("<aqua>Validate").lore(hint("Click", "list problems in chat"))
                .button(click -> printIssues(ctx.editor().validate(session))));
        set(49, Items.of(errors > 0 ? Material.GRAY_DYE : Material.LIME_DYE).name(errors > 0 ? "<gray>Save (fix errors first)" : "<green><bold>Save")
                .lore(hint("Click", "validate and write to quests/" + d.id + ".yml"))
                .button(click -> save()));
        set(51, Items.of(Material.ORANGE_DYE).name("<gold>Discard changes")
                .button(click -> new ConfirmMenu(viewer, this, Text.parse("Discard unsaved changes?"), () -> {
                    ctx.editor().discard(viewer.getUniqueId());
                    reopenParentOrClose();
                }).open()));
        if (!session.isNew) {
            set(53, Items.of(Material.LAVA_BUCKET).name("<red>Delete quest").lore("<gray>Progress records stay in storage.")
                    .button(click -> new ConfirmMenu(viewer, this, Text.parse("<red>Delete " + session.originalId + "?"), () -> {
                        try {
                            ctx.quests().delete(session.originalId);
                            ctx.editor().discard(viewer.getUniqueId());
                            viewer.sendMessage(Text.parse("<green>Deleted quest " + session.originalId));
                        } catch (IOException e) {
                            ctx.plugin().getLogger().log(Level.SEVERE, "Could not delete quest " + session.originalId, e);
                            viewer.sendMessage(Text.parse("<red>Could not delete the quest file; see console."));
                        }
                        reopenParentOrClose();
                    }).open()));
        }
        fillBottomRow();
    }

    private void save() {
        try {
            List<QuestValidator.Issue> issues = ctx.editor().save(session);
            if (QuestValidator.hasErrors(issues)) {
                viewer.sendMessage(Text.parse("<red>Not saved. Fix these first:"));
                printIssues(issues);
                return;
            }
            viewer.sendMessage(Text.parse("<green>Saved quest <white>" + session.draft.id));
            if (!issues.isEmpty()) {
                printIssues(issues);
            }
            reopenParentOrClose();
        } catch (IOException e) {
            ctx.plugin().getLogger().log(Level.SEVERE, "Could not save quest " + session.draft.id, e);
            viewer.sendMessage(Text.parse("<red>Could not write the quest file; see console."));
        }
    }

    private void reopenParentOrClose() {
        if (parent != null) {
            parent.open();
        } else {
            viewer.closeInventory();
        }
    }

    private void printIssues(List<QuestValidator.Issue> issues) {
        if (issues.isEmpty()) {
            viewer.sendMessage(Text.parse("<green>No problems found."));
            return;
        }
        for (QuestValidator.Issue issue : issues) {
            String icon = issue.severity() == QuestValidator.Severity.ERROR ? "<red>✖ " : "<yellow>⚠ ";
            viewer.sendMessage(Text.parse(icon + "<gray>" + Text.escape(issue.path()) + ": <white>" + Text.escape(issue.message())));
        }
    }

    private Button toggle(String name, boolean value, Runnable flip) {
        return Items.of(value ? Material.LIME_DYE : Material.GRAY_DYE).name("<yellow>" + name)
                .lore(value ? "<green>Yes" : "<red>No").lore("").lore(hint("Click", "toggle"))
                .button(click -> {
                    flip.run();
                    refresh();
                });
    }

    private Button propertiesButton() {
        QuestDraft d = draft();
        Items item = Items.of(Material.COMPARATOR).name("<yellow>Integration properties");
        if (d.properties.isEmpty()) {
            item.lore("<dark_gray>(none)");
        }
        d.properties.forEach((key, value) -> item.lore(Component.text(key + " = " + value)));
        item.lore("").lore("<dark_gray>e.g. fancynpcs.givers = guard_bob")
                .lore(hint("Click", "set key=value, or key= to remove"));
        return item.button(click -> prompt("Type key=value (or key= to remove)", null, input -> {
            int eq = input.indexOf('=');
            if (eq <= 0) {
                return "Use the form key=value";
            }
            String key = input.substring(0, eq).trim();
            String value = input.substring(eq + 1).trim();
            if (!key.matches("[a-z0-9._-]+")) {
                return "Keys may contain lowercase letters, digits, dots, dashes and underscores";
            }
            if (value.isEmpty()) {
                d.properties.remove(key);
            } else {
                d.properties.put(key, value);
            }
            return null;
        }));
    }
}
