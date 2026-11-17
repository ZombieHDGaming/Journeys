package dev.voidscape.journeys.core;

import dev.voidscape.journeys.api.Journeys;
import dev.voidscape.journeys.api.JourneysApi;
import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.ElementType;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.RewardType;
import dev.voidscape.journeys.core.block.PlacedBlockTracker;
import dev.voidscape.journeys.core.command.JourneysCommand;
import dev.voidscape.journeys.core.config.Categories;
import dev.voidscape.journeys.core.config.Messages;
import dev.voidscape.journeys.core.config.PluginConfig;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.engine.QuestEngine;
import dev.voidscape.journeys.core.gui.ChatPrompts;
import dev.voidscape.journeys.core.gui.MenuListener;
import dev.voidscape.journeys.core.gui.player.QuestBoardMenu;
import dev.voidscape.journeys.core.holder.SimpleHolderService;
import dev.voidscape.journeys.core.integration.IntegrationManager;
import dev.voidscape.journeys.core.quest.QuestValidator;
import dev.voidscape.journeys.core.quest.YamlQuestRepository;
import dev.voidscape.journeys.core.storage.ProgressStorage;
import dev.voidscape.journeys.core.storage.sql.SqlProgressStorage;
import dev.voidscape.journeys.core.type.Registries;
import dev.voidscape.journeys.core.type.SimpleTypeRegistry;
import dev.voidscape.journeys.core.type.builtin.ConditionTypes;
import dev.voidscape.journeys.core.type.builtin.ObjectiveTypes;
import dev.voidscape.journeys.core.type.builtin.RewardTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Wires the plugin together. Startup order matters:
 * <ol>
 *   <li>config, messages, storage (a storage failure disables the plugin rather than losing progress)</li>
 *   <li>registries with the built-in types, the engine, the API and bundled integrations</li>
 *   <li>one tick later, once every plugin has enabled and registered its types: load quests and start the engine</li>
 * </ol>
 */
public final class JourneysPlugin extends JavaPlugin {

    private static final List<String> EXAMPLE_QUESTS = List.of("quests/example_woodcutter.yml", "quests/example_lumber_baron.yml");

    private @Nullable ProgressStorage storage;
    private @Nullable PlacedBlockTracker placedBlocks;
    private @Nullable QuestEngine engine;
    private @Nullable IntegrationManager integrations;
    private @Nullable Registries registries;
    private @Nullable JourneysApi api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginConfig config;
        try {
            config = PluginConfig.from(getConfig());
        } catch (IllegalArgumentException e) {
            getLogger().severe("Invalid config.yml: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Messages messages = new Messages(this);
        messages.load();
        Categories categories = new Categories(this);
        categories.load();

        try {
            storage = SqlProgressStorage.create(config.storage(), getDataPath(), getLogger());
            storage.init();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not connect to " + config.storage().type() + " storage. Journeys is disabled to protect quest progress.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SimpleHolderService holders = new SimpleHolderService(getLogger());
        registries = new Registries(
                new SimpleTypeRegistry<ObjectiveType>("objective", this::listen, this::unlisten),
                new SimpleTypeRegistry<ConditionType>("condition", this::listen, this::unlisten),
                new SimpleTypeRegistry<RewardType>("reward", this::listen, this::unlisten));
        QuestValidator validator = new QuestValidator(registries);
        YamlQuestRepository quests = new YamlQuestRepository(getDataPath().resolve("quests"), validator, getLogger());
        QuestEngine engine = new QuestEngine(this, quests, registries, holders, storage, messages, config.progressActionBar());
        this.engine = engine;
        holders.onMembershipChange(engine::loadPlayer);

        PlacedBlockTracker placedBlocks = new PlacedBlockTracker(this);
        this.placedBlocks = placedBlocks;
        getServer().getPluginManager().registerEvents(placedBlocks, this);

        ObjectiveTypes.all(engine, placedBlocks).forEach(registries.objectives()::register);
        ConditionTypes.all(() -> engine, holders, quests).forEach(registries.conditions()::register);
        RewardTypes.all().forEach(registries.rewards()::register);

        // Menus need the full context, which is built below; the API only calls this after enable.
        AtomicReference<JourneysContext> context = new AtomicReference<>();
        api = new JourneysApiImpl(quests, engine, engine, holders, placedBlocks,
                (player, category) -> QuestBoardMenu.open(context.get(), player, category, null), registries);
        Journeys.register(api);
        getServer().getServicesManager().register(JourneysApi.class, api, this, ServicePriority.Normal);

        integrations = new IntegrationManager(this);
        integrations.enableAll(api);

        ChatPrompts prompts = new ChatPrompts(this, messages);
        getServer().getPluginManager().registerEvents(prompts, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        JourneysContext ctx = new JourneysContext(this, engine, quests, registries, holders, messages, categories, prompts,
                new EditorSessions(quests, validator));
        context.set(ctx);
        Runnable reload = () -> {
            categories.load();
            reload(messages, quests);
        };
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(new JourneysCommand(ctx, reload).build(), "Quest log, editor and admin commands", JourneysCommand.ALIASES));

        writeExampleQuest();
        // Deferred so plugins that depend on Journeys have registered their types before quests are validated.
        getServer().getScheduler().runTask(this, () -> {
            loadQuests(quests);
            quests.onChange(engine::reconcileAll);
            engine.start(config.autosaveSeconds());
        });
    }

    @Override
    public void onDisable() {
        if (integrations != null) {
            integrations.disableAll();
        }
        if (engine != null) {
            engine.shutdown();
        }
        if (storage != null) {
            storage.close();
        }
        if (placedBlocks != null) {
            placedBlocks.flushAll();
        }
        if (registries != null) {
            ((SimpleTypeRegistry<?>) registries.objectives()).clear();
            ((SimpleTypeRegistry<?>) registries.conditions()).clear();
            ((SimpleTypeRegistry<?>) registries.rewards()).clear();
        }
        if (api != null) {
            getServer().getServicesManager().unregister(JourneysApi.class, api);
        }
        Journeys.unregister();
    }

    /** Reloads messages and quest files. Storage and other config.yml settings need a restart. */
    private void reload(Messages messages, YamlQuestRepository quests) {
        messages.load();
        loadQuests(quests);
    }

    private void loadQuests(YamlQuestRepository quests) {
        try {
            int count = quests.load();
            getLogger().info("Loaded " + count + " quest(s)");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not read the quests folder", e);
        }
    }

    private void writeExampleQuest() {
        Path folder = getDataPath().resolve("quests");
        if (!Files.exists(folder)) {
            EXAMPLE_QUESTS.forEach(file -> saveResource(file, false));
        }
    }

    private void listen(ElementType type) {
        if (type instanceof Listener listener) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void unlisten(ElementType type) {
        if (type instanceof Listener listener) {
            HandlerList.unregisterAll(listener);
        }
    }
}
