package dev.voidscape.journeys.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.progress.StartResult;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.core.JourneysContext;
import dev.voidscape.journeys.core.gui.editor.EditorHomeMenu;
import dev.voidscape.journeys.core.gui.player.QuestBoardMenu;
import dev.voidscape.journeys.core.gui.player.QuestLogMenu;
import dev.voidscape.journeys.core.quest.QuestValidator;
import dev.voidscape.journeys.core.text.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** {@code /journeys} (aliases {@code /quests}, {@code /quest}), built with Brigadier. */
public final class JourneysCommand {

    public static final String ROOT = "journeys";
    public static final List<String> ALIASES = List.of("quests", "quest");

    private final JourneysContext ctx;
    private final Runnable reload;

    public JourneysCommand(JourneysContext ctx, Runnable reload) {
        this.ctx = ctx;
        this.reload = reload;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(ROOT)
                .requires(permission("journeys.command.log"))
                .executes(this::openLog)
                .then(Commands.literal("log")
                        .requires(permission("journeys.command.log"))
                        .executes(this::openLog))
                .then(Commands.literal("board")
                        .requires(permission("journeys.command.board"))
                        .executes(c -> openBoard(c, null))
                        .then(Commands.argument("category", StringArgumentType.word())
                                .suggests(categories())
                                .executes(c -> openBoard(c, StringArgumentType.getString(c, "category")))))
                .then(Commands.literal("abandon")
                        .requires(permission("journeys.command.abandon"))
                        .then(Commands.argument("quest", StringArgumentType.word())
                                .suggests(activeQuests())
                                .executes(this::abandon)))
                .then(Commands.literal("choose")
                        .requires(permission("journeys.command.choose"))
                        .then(Commands.argument("quest", StringArgumentType.word())
                                .then(Commands.argument("option", IntegerArgumentType.integer(0))
                                        .executes(this::choose))))
                .then(Commands.literal("start")
                        .requires(permission("journeys.command.start"))
                        .then(Commands.argument("quest", StringArgumentType.word())
                                .suggests(allQuests())
                                .executes(c -> start(c, null))
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(c -> start(c, c.getArgument("player", PlayerSelectorArgumentResolver.class)
                                                .resolve(c.getSource()).getFirst())))))
                .then(Commands.literal("editor")
                        .requires(permission("journeys.editor"))
                        .executes(this::openEditor)
                        .then(Commands.argument("quest", StringArgumentType.word())
                                .suggests(allQuests())
                                .executes(this::openEditorFor)))
                .then(Commands.literal("list")
                        .requires(permission("journeys.command.list"))
                        .executes(this::list))
                .then(Commands.literal("reload")
                        .requires(permission("journeys.command.reload"))
                        .executes(c -> {
                            reload.run();
                            ctx.messages().send(c.getSource().getSender(), "command.reloaded",
                                    Text.unparsed("count", Integer.toString(ctx.quests().all().size())));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static Predicate<CommandSourceStack> permission(String node) {
        return source -> source.getSender().hasPermission(node);
    }

    private SuggestionProvider<CommandSourceStack> allQuests() {
        return (c, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            ctx.quests().all().stream().map(QuestDefinition::id).filter(id -> id.startsWith(remaining)).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> categories() {
        return (c, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            ctx.quests().all().stream()
                    .map(q -> q.settings().board().category())
                    .distinct()
                    .filter(id -> id.startsWith(remaining))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private int openBoard(CommandContext<CommandSourceStack> c, @Nullable String category) {
        requirePlayer(c).ifPresent(player -> QuestBoardMenu.open(ctx, player, category == null ? null : category.toLowerCase(Locale.ROOT), null));
        return Command.SINGLE_SUCCESS;
    }

    private SuggestionProvider<CommandSourceStack> activeQuests() {
        return (c, builder) -> {
            if (c.getSource().getExecutor() instanceof Player player) {
                String remaining = builder.getRemainingLowerCase();
                for (QuestHolder holder : ctx.holders().holdersOf(player.getUniqueId())) {
                    ctx.engine().allProgress(holder).stream()
                            .filter(p -> p.status().isActive())
                            .map(p -> p.questId())
                            .filter(id -> id.startsWith(remaining))
                            .forEach(builder::suggest);
                }
            }
            return builder.buildFuture();
        };
    }

    private Optional<Player> requirePlayer(CommandContext<CommandSourceStack> c) {
        if (c.getSource().getExecutor() instanceof Player player) {
            return Optional.of(player);
        }
        ctx.messages().send(c.getSource().getSender(), "command.players-only");
        return Optional.empty();
    }

    private int openLog(CommandContext<CommandSourceStack> c) {
        requirePlayer(c).ifPresent(player -> new QuestLogMenu(ctx, player).open());
        return Command.SINGLE_SUCCESS;
    }

    private int abandon(CommandContext<CommandSourceStack> c) {
        Optional<Player> player = requirePlayer(c);
        if (player.isEmpty()) {
            return 0;
        }
        String questId = StringArgumentType.getString(c, "quest");
        Optional<QuestHolder> holder = ctx.engine().activeHolder(player.get().getUniqueId(), questId);
        Optional<QuestDefinition> quest = ctx.quests().get(questId);
        if (holder.isEmpty() || quest.isEmpty()) {
            ctx.messages().send(player.get(), "command.not-active", Text.unparsed("quest", questId));
            return 0;
        }
        if (!quest.get().settings().abandonable() && !player.get().hasPermission("journeys.admin")) {
            ctx.messages().send(player.get(), "command.not-abandonable", Text.parsed("quest", quest.get().displayName()));
            return 0;
        }
        ctx.engine().abandon(holder.get(), questId);
        return Command.SINGLE_SUCCESS;
    }

    private int choose(CommandContext<CommandSourceStack> c) {
        Optional<Player> player = requirePlayer(c);
        if (player.isEmpty()) {
            return 0;
        }
        String questId = StringArgumentType.getString(c, "quest");
        int option = IntegerArgumentType.getInteger(c, "option");
        boolean chosen = ctx.engine().activeHolder(player.get().getUniqueId(), questId)
                .map(holder -> ctx.engine().choose(holder, questId, option, player.get()))
                .orElse(false);
        if (!chosen) {
            ctx.messages().send(player.get(), "command.choose-failed");
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int start(CommandContext<CommandSourceStack> c, Player target) throws CommandSyntaxException {
        CommandSender sender = c.getSource().getSender();
        Player player = target;
        if (player == null) {
            Optional<Player> self = requirePlayer(c);
            if (self.isEmpty()) {
                return 0;
            }
            player = self.get();
        }
        String questId = StringArgumentType.getString(c, "quest");
        Optional<QuestDefinition> quest = ctx.quests().get(questId);
        if (quest.isEmpty()) {
            ctx.messages().send(sender, "start.unknown-quest", Text.unparsed("quest", questId));
            return 0;
        }
        QuestHolder holder = ctx.holders().holderFor(player.getUniqueId(), quest.get().settings().scope());
        StartResult result = ctx.engine().start(holder, questId, player, true);
        if (sender != player) {
            ctx.messages().send(sender, "command.start-result",
                    Text.parsed("quest", quest.get().displayName()),
                    Text.unparsed("player", player.getName()),
                    Text.unparsed("result", result.name().toLowerCase(Locale.ROOT)));
        }
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private int openEditor(CommandContext<CommandSourceStack> c) {
        requirePlayer(c).ifPresent(player -> new EditorHomeMenu(ctx, player).open());
        return Command.SINGLE_SUCCESS;
    }

    private int openEditorFor(CommandContext<CommandSourceStack> c) {
        Optional<Player> player = requirePlayer(c);
        if (player.isEmpty()) {
            return 0;
        }
        String questId = StringArgumentType.getString(c, "quest");
        Optional<QuestDefinition> quest = ctx.quests().get(questId);
        if (quest.isEmpty()) {
            ctx.messages().send(player.get(), "start.unknown-quest", Text.unparsed("quest", questId));
            return 0;
        }
        EditorHomeMenu.openQuest(ctx, player.get(), quest.get());
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> c) {
        CommandSender sender = c.getSource().getSender();
        Map<String, List<QuestValidator.Issue>> problems = ctx.quests().problems();
        ctx.messages().send(sender, "command.list-header", Text.unparsed("count", Integer.toString(ctx.quests().all().size())));
        for (QuestDefinition quest : ctx.quests().all()) {
            int warnings = problems.getOrDefault(quest.id(), List.of()).size();
            ctx.messages().send(sender, "command.list-entry",
                    Text.unparsed("id", quest.id()),
                    Text.parsed("quest", quest.displayName()),
                    Text.unparsed("stages", Integer.toString(quest.stages().size())),
                    Text.unparsed("warnings", warnings == 0 ? "" : " (" + warnings + " warnings)"));
        }
        problems.forEach((id, issues) -> {
            if (ctx.quests().get(id).isEmpty()) {
                ctx.messages().send(sender, "command.list-broken", Text.unparsed("id", id),
                        Text.unparsed("problem", issues.isEmpty() ? "" : issues.getFirst().toString()));
            }
        });
        return Command.SINGLE_SUCCESS;
    }
}
