package dev.voidscape.journeys.core.engine;

import dev.voidscape.journeys.api.event.ObjectiveProgressEvent;
import dev.voidscape.journeys.api.event.ObjectiveRegressEvent;
import dev.voidscape.journeys.api.event.QuestEndEvent;
import dev.voidscape.journeys.api.event.QuestStageChangeEvent;
import dev.voidscape.journeys.api.event.QuestStartEvent;
import dev.voidscape.journeys.api.holder.HolderService;
import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.progress.QuestProgress;
import dev.voidscape.journeys.api.progress.QuestService;
import dev.voidscape.journeys.api.progress.QuestStatus;
import dev.voidscape.journeys.api.progress.StartResult;
import dev.voidscape.journeys.api.quest.BranchMode;
import dev.voidscape.journeys.api.quest.ConfiguredCondition;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.ConfiguredReward;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestRepository;
import dev.voidscape.journeys.api.quest.QuestSettings;
import dev.voidscape.journeys.api.quest.QuestStage;
import dev.voidscape.journeys.api.quest.StageTransition;
import dev.voidscape.journeys.api.quest.TransitionTarget;
import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.ObjectiveTracker;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.ParameterValues;
import dev.voidscape.journeys.api.type.RewardType;
import dev.voidscape.journeys.core.config.Messages;
import dev.voidscape.journeys.core.quest.QuestTexts;
import dev.voidscape.journeys.core.storage.ProgressStorage;
import dev.voidscape.journeys.core.storage.QuestRecord;
import dev.voidscape.journeys.core.text.Text;
import dev.voidscape.journeys.core.type.Registries;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Runs quests: owns loaded progress, applies objective progress, follows transitions and persists changes.
 *
 * <h2>Threading</h2>
 * All state lives on the server thread. Storage calls run on a small IO executor; work for the same holder is chained
 * so a load issued while a save is still in flight always sees the saved data (for example on a fast relog).
 */
public final class QuestEngine implements QuestService, ObjectiveTracker, Listener {

    /** Guards against stage graphs that loop through objective-less stages forever. */
    private static final int MAX_CHAINED_TRANSITIONS = 64;

    private final Plugin plugin;
    private final Logger logger;
    private final QuestRepository quests;
    private final Registries registries;
    private final HolderService holders;
    private final ProgressStorage storage;
    private final Messages messages;
    private final QuestTexts texts;
    private final boolean progressActionBar;
    private final ExecutorService io;

    private final Map<UUID, LoadedHolder> loaded = new HashMap<>();
    private final Set<UUID> loading = new HashSet<>();
    private final Map<UUID, CompletableFuture<?>> pendingIo = new ConcurrentHashMap<>();
    private final Set<String> reportedErrors = ConcurrentHashMap.newKeySet();
    private @Nullable BukkitTask autosaveTask;

    private static final class LoadedHolder {
        QuestHolder holder;
        final Map<String, QuestState> quests = new HashMap<>();

        LoadedHolder(QuestHolder holder) {
            this.holder = holder;
        }
    }

    public QuestEngine(Plugin plugin, QuestRepository quests, Registries registries, HolderService holders,
                       ProgressStorage storage, Messages messages, boolean progressActionBar) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.quests = quests;
        this.registries = registries;
        this.holders = holders;
        this.storage = storage;
        this.messages = messages;
        this.texts = new QuestTexts(registries);
        this.progressActionBar = progressActionBar;
        this.io = Executors.newFixedThreadPool(2, Thread.ofPlatform().name("Journeys IO ", 0).daemon(true).factory());
    }

    public QuestTexts texts() {
        return texts;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start(int autosaveSeconds) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long period = autosaveSeconds * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
        // Supports plugin reloads and players who joined before enable finished.
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId());
        }
    }

    /** Saves everything synchronously and stops the IO executor. Called on disable. */
    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        List<QuestRecord> dirty = new ArrayList<>();
        for (LoadedHolder holder : loaded.values()) {
            dirty.addAll(collectDirty(holder));
        }
        loaded.clear();
        // Let queued saves finish first so they cannot overwrite the final state.
        CompletableFuture.allOf(pendingIo.values().toArray(CompletableFuture[]::new))
                .handle((v, e) -> null)
                .join();
        if (!dirty.isEmpty()) {
            try {
                storage.save(dirty);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Could not save quest progress on shutdown", e);
            }
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Storage tasks did not finish within 10 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void tick() {
        for (LoadedHolder holder : List.copyOf(loaded.values())) {
            retryStalled(holder);
            save(holder);
        }
    }

    // ------------------------------------------------------------------ loading

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Run next tick so the quitting player no longer counts as an online member.
        Bukkit.getScheduler().runTask(plugin, this::releaseOfflineHolders);
    }

    /** Loads every holder the player belongs to. Also called when party membership changes. */
    public void loadPlayer(UUID playerId) {
        for (QuestHolder holder : holders.holdersOf(playerId)) {
            LoadedHolder existing = loaded.get(holder.id());
            if (existing != null) {
                existing.holder = holder;
            } else {
                load(holder);
            }
        }
        releaseOfflineHolders();
    }

    private void load(QuestHolder holder) {
        UUID id = holder.id();
        if (!loading.add(id)) {
            return;
        }
        chain(id, () -> storage.load(id)).whenComplete((records, error) -> runSync(() -> {
            loading.remove(id);
            if (error != null) {
                logger.log(Level.SEVERE, "Could not load quest progress of " + id, error);
                return;
            }
            if (onlineMembers(holder).isEmpty() || loaded.containsKey(id)) {
                return;
            }
            LoadedHolder entry = new LoadedHolder(holder);
            for (QuestRecord record : records) {
                entry.quests.put(record.questId(), QuestState.from(record));
            }
            loaded.put(id, entry);
            reconcile(entry);
            retryStalled(entry);
        }));
    }

    private void runSync(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void releaseOfflineHolders() {
        for (LoadedHolder holder : List.copyOf(loaded.values())) {
            boolean stillMember = false;
            for (Player member : onlineMembers(holder.holder)) {
                // A party holder is kept while any online member still belongs to it.
                if (holders.holdersOf(member.getUniqueId()).stream().anyMatch(h -> h.id().equals(holder.holder.id()))) {
                    stillMember = true;
                    break;
                }
            }
            if (!stillMember) {
                save(holder);
                loaded.remove(holder.holder.id());
            }
        }
    }

    private void save(LoadedHolder holder) {
        List<QuestRecord> dirty = collectDirty(holder);
        if (!dirty.isEmpty()) {
            chain(holder.holder.id(), () -> {
                storage.save(dirty);
                return null;
            }).exceptionally(error -> {
                logger.log(Level.SEVERE, "Could not save quest progress of " + holder.holder.id(), error);
                return null;
            });
        }
    }

    private static List<QuestRecord> collectDirty(LoadedHolder holder) {
        List<QuestRecord> dirty = new ArrayList<>();
        for (QuestState state : holder.quests.values()) {
            if (state.dirty) {
                dirty.add(state.toRecord());
                state.dirty = false;
            }
        }
        return dirty;
    }

    /** Runs storage work for a holder after any work already queued for it. */
    private <T> CompletableFuture<T> chain(UUID holderId, Callable<T> task) {
        CompletableFuture<?> previous = pendingIo.getOrDefault(holderId, CompletableFuture.completedFuture(null));
        CompletableFuture<T> next = previous.handle((v, e) -> null).thenApplyAsync(ignored -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, io);
        pendingIo.put(holderId, next);
        next.whenComplete((v, e) -> pendingIo.remove(holderId, next));
        return next;
    }

    /**
     * Brings loaded progress in line with the current definitions, after loading or after quests were edited or
     * reloaded. Progress on a stage that no longer exists restarts the quest's start stage.
     */
    public void reconcileAll() {
        for (LoadedHolder holder : loaded.values()) {
            reconcile(holder);
            retryStalled(holder);
        }
    }

    private void reconcile(LoadedHolder holder) {
        for (QuestState state : List.copyOf(holder.quests.values())) {
            if (!state.status.isActive()) {
                continue;
            }
            Optional<QuestDefinition> quest = quests.get(state.questId);
            if (quest.isEmpty()) {
                continue; // Kept as is; resumes if the quest comes back.
            }
            QuestDefinition def = quest.get();
            if (state.stageId == null || def.stage(state.stageId).isEmpty()) {
                logger.warning("Stage " + state.stageId + " of quest " + def.id() + " no longer exists; moving holder "
                        + state.holderId + " to the start stage.");
                enterStage(holder.holder, def, state, def.startStage(), null, null, 0);
                continue;
            }
            QuestStage stage = def.stage(state.stageId).orElseThrow();
            if (!state.stageComplete && QuestFlow.isStageComplete(stage, state.objectives, texts::goal)) {
                completeStage(holder.holder, def, stage, state, null, 0);
            }
        }
    }

    /** Re-checks automatic stages that were waiting for a transition's conditions to pass. */
    private void retryStalled(LoadedHolder holder) {
        for (QuestState state : List.copyOf(holder.quests.values())) {
            if (!state.status.isActive() || !state.stageComplete || state.stageId == null) {
                continue;
            }
            quests.get(state.questId).ifPresent(def -> def.stage(state.stageId).ifPresent(stage -> {
                if (stage.branchMode() == BranchMode.AUTOMATIC) {
                    resolveTransition(holder.holder, def, stage, state, null, 0);
                }
            }));
        }
    }

    // ------------------------------------------------------------------ QuestService

    @Override
    public boolean isLoaded(QuestHolder holder) {
        return loaded.containsKey(holder.id());
    }

    @Override
    public Optional<QuestProgress> progress(QuestHolder holder, String questId) {
        LoadedHolder entry = loaded.get(holder.id());
        if (entry == null) {
            return Optional.empty();
        }
        QuestState state = entry.quests.get(questId);
        return state == null ? Optional.empty() : Optional.of(state.snapshot());
    }

    @Override
    public Collection<QuestProgress> allProgress(QuestHolder holder) {
        LoadedHolder entry = loaded.get(holder.id());
        if (entry == null) {
            return List.of();
        }
        return entry.quests.values().stream().map(QuestState::snapshot).toList();
    }

    /** The holder of the player through which the quest is active, if any. */
    public Optional<QuestHolder> activeHolder(UUID playerId, String questId) {
        for (QuestHolder holder : holders.holdersOf(playerId)) {
            LoadedHolder entry = loaded.get(holder.id());
            if (entry != null) {
                QuestState state = entry.quests.get(questId);
                if (state != null && state.status.isActive()) {
                    return Optional.of(entry.holder);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public StartResult canStart(QuestHolder holder, String questId, @Nullable Player initiator) {
        Optional<QuestDefinition> quest = quests.get(questId);
        if (quest.isEmpty()) {
            return StartResult.UNKNOWN_QUEST;
        }
        LoadedHolder entry = loaded.get(holder.id());
        if (entry == null) {
            return StartResult.NOT_LOADED;
        }
        QuestDefinition def = quest.get();
        QuestState state = entry.quests.get(questId);
        if (state != null) {
            if (state.status.isActive()) {
                return StartResult.ALREADY_ACTIVE;
            }
            if (state.completions > 0 && !def.settings().repeatable()) {
                return StartResult.ALREADY_COMPLETED;
            }
            if (def.settings().maxCompletions() > 0 && state.completions >= def.settings().maxCompletions()) {
                return StartResult.MAX_COMPLETIONS;
            }
            if (cooldownRemaining(def, state).isPositive()) {
                return StartResult.ON_COOLDOWN;
            }
        }
        if (initiator != null && !meetsRequirements(def, initiator, holder)) {
            return StartResult.REQUIREMENTS_NOT_MET;
        }
        return StartResult.STARTED;
    }

    /** Time left before the quest can be started again; zero if none. */
    public Duration cooldownRemaining(QuestDefinition def, QuestHolder holder) {
        LoadedHolder entry = loaded.get(holder.id());
        QuestState state = entry == null ? null : entry.quests.get(def.id());
        return state == null ? Duration.ZERO : cooldownRemaining(def, state);
    }

    private static Duration cooldownRemaining(QuestDefinition def, QuestState state) {
        if (state.lastCompletedAt <= 0 || def.settings().cooldown().isZero()) {
            return Duration.ZERO;
        }
        long readyAt = state.lastCompletedAt + def.settings().cooldown().toMillis();
        long remaining = readyAt - System.currentTimeMillis();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    @Override
    public StartResult start(QuestHolder holder, String questId, @Nullable Player initiator, boolean notify) {
        StartResult result = canStart(holder, questId, initiator);
        Optional<QuestDefinition> quest = quests.get(questId);
        if (result == StartResult.STARTED) {
            QuestDefinition def = quest.orElseThrow();
            if (!new QuestStartEvent(holder, def, initiator).callEvent()) {
                result = StartResult.CANCELLED;
            } else {
                LoadedHolder entry = Objects.requireNonNull(loaded.get(holder.id()));
                QuestState state = entry.quests.computeIfAbsent(questId, id -> new QuestState(holder.id(), id));
                state.status = QuestStatus.ACTIVE;
                state.startedAt = System.currentTimeMillis();
                state.stageId = null;
                state.visited.clear();
                state.taken.clear();
                for (Player member : onlineMembers(holder)) {
                    messages.send(member, "quest.started", questName(def));
                }
                enterStage(holder, def, state, def.startStage(), null, initiator, 0);
            }
        }
        if (notify && initiator != null && result != StartResult.STARTED) {
            notifyStartFailure(initiator, quest.orElse(null), questId, result, holder);
        }
        return result;
    }

    private void notifyStartFailure(Player player, @Nullable QuestDefinition def, String questId, StartResult result, QuestHolder holder) {
        TagResolver quest = def == null ? Text.unparsed("quest", questId) : questName(def);
        String key = "start." + result.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        TagResolver cooldown = Text.unparsed("time", def == null ? "" : dev.voidscape.journeys.core.util.Durations.format(
                cooldownRemaining(def, holder).withNanos(0)));
        messages.send(player, key, quest, cooldown);
    }

    @Override
    public boolean abandon(QuestHolder holder, String questId) {
        LoadedHolder entry = loaded.get(holder.id());
        QuestState state = entry == null ? null : entry.quests.get(questId);
        if (state == null || !state.status.isActive()) {
            return false;
        }
        endQuest(holder, state, QuestStatus.ABANDONED);
        quests.get(questId).ifPresent(def -> {
            for (Player member : onlineMembers(holder)) {
                messages.send(member, "quest.abandoned", questName(def));
            }
            new QuestEndEvent(holder, def, QuestStatus.ABANDONED).callEvent();
        });
        return true;
    }

    @Override
    public boolean choose(QuestHolder holder, String questId, int transitionIndex, Player chooser) {
        LoadedHolder entry = loaded.get(holder.id());
        QuestState state = entry == null ? null : entry.quests.get(questId);
        Optional<QuestDefinition> quest = quests.get(questId);
        if (state == null || quest.isEmpty() || !state.status.isActive() || !state.stageComplete || state.stageId == null) {
            return false;
        }
        Optional<QuestStage> stage = quest.get().stage(state.stageId);
        if (stage.isEmpty() || stage.get().branchMode() != BranchMode.CHOICE
                || transitionIndex < 0 || transitionIndex >= stage.get().transitions().size()) {
            return false;
        }
        StageTransition transition = stage.get().transitions().get(transitionIndex);
        if (!conditionsPass(transition.conditions(), chooser, holder)) {
            return false;
        }
        follow(holder, quest.get(), stage.get(), state, transition, chooser, 0);
        return true;
    }

    /** Transitions of a pending choice that the player may pick, by index. Empty if no choice is pending. */
    public Map<Integer, StageTransition> availableChoices(QuestHolder holder, String questId, Player player) {
        LoadedHolder entry = loaded.get(holder.id());
        QuestState state = entry == null ? null : entry.quests.get(questId);
        if (state == null || !state.status.isActive() || !state.stageComplete || state.stageId == null) {
            return Map.of();
        }
        Optional<QuestStage> stage = quests.get(questId).flatMap(def -> def.stage(state.stageId));
        if (stage.isEmpty() || stage.get().branchMode() != BranchMode.CHOICE) {
            return Map.of();
        }
        Map<Integer, StageTransition> choices = new java.util.LinkedHashMap<>();
        List<StageTransition> transitions = stage.get().transitions();
        for (int i = 0; i < transitions.size(); i++) {
            if (conditionsPass(transitions.get(i).conditions(), player, holder)) {
                choices.put(i, transitions.get(i));
            }
        }
        return choices;
    }

    // ------------------------------------------------------------------ ObjectiveTracker

    @Override
    public int progress(Player player, ObjectiveType type, Predicate<ParameterValues> matcher, int amount) {
        if (amount < 1) {
            return 0;
        }
        int advanced = 0;
        for (QuestHolder holderRef : holders.holdersOf(player.getUniqueId())) {
            LoadedHolder entry = loaded.get(holderRef.id());
            if (entry == null) {
                continue;
            }
            for (QuestState state : List.copyOf(entry.quests.values())) {
                if (!state.status.isActive() || state.stageComplete || state.stageId == null) {
                    continue;
                }
                Optional<QuestDefinition> quest = quests.get(state.questId);
                Optional<QuestStage> stage = quest.flatMap(def -> def.stage(state.stageId));
                if (stage.isEmpty()) {
                    continue;
                }
                advanced += progressStage(entry.holder, quest.get(), stage.get(), state, player, type, matcher, amount);
            }
        }
        return advanced;
    }

    @Override
    public int regress(Player player, ObjectiveType type, Predicate<ParameterValues> matcher, int amount) {
        if (amount < 1) {
            return 0;
        }
        int regressed = 0;
        for (QuestHolder holderRef : holders.holdersOf(player.getUniqueId())) {
            LoadedHolder entry = loaded.get(holderRef.id());
            if (entry == null) {
                continue;
            }
            for (QuestState state : entry.quests.values()) {
                // Once a stage is complete its result stands; only progress still being worked on can be undone.
                if (!state.status.isActive() || state.stageComplete || state.stageId == null) {
                    continue;
                }
                Optional<QuestDefinition> quest = quests.get(state.questId);
                Optional<QuestStage> stage = quest.flatMap(def -> def.stage(state.stageId));
                if (stage.isEmpty()) {
                    continue;
                }
                for (ConfiguredObjective objective : stage.get().objectives()) {
                    int current = state.objectives.getOrDefault(objective.id(), 0);
                    if (current <= 0 || !objective.type().equals(type.key()) || !safeMatch(matcher, quest.get(), stage.get(), objective)) {
                        continue;
                    }
                    ObjectiveRegressEvent event = new ObjectiveRegressEvent(entry.holder, quest.get(), player, stage.get(),
                            objective, current, amount);
                    if (!event.callEvent()) {
                        continue;
                    }
                    int updated = Math.max(0, current - event.amount());
                    state.objectives.put(objective.id(), updated);
                    state.touch();
                    regressed++;
                    announceProgress(player, entry.holder, quest.get(), objective, updated, texts.goal(objective));
                }
            }
        }
        return regressed;
    }

    private boolean safeMatch(Predicate<ParameterValues> matcher, QuestDefinition def, QuestStage stage, ConfiguredObjective objective) {
        try {
            return matcher.test(objective.params());
        } catch (RuntimeException e) {
            reportOnce(def.id() + "/" + stage.id() + "/" + objective.id(),
                    "Objective " + objective.id() + " in quest " + def.id() + " stage " + stage.id() + " failed to match: " + e.getMessage());
            return false;
        }
    }

    private int progressStage(QuestHolder holder, QuestDefinition def, QuestStage stage, QuestState state, Player player,
                              ObjectiveType type, Predicate<ParameterValues> matcher, int amount) {
        int advanced = 0;
        for (ConfiguredObjective objective : stage.objectives()) {
            if (!objective.type().equals(type.key()) || !QuestFlow.accepts(stage, objective, state.objectives, texts::goal)) {
                continue;
            }
            if (!safeMatch(matcher, def, stage, objective)) {
                continue;
            }
            int current = state.objectives.getOrDefault(objective.id(), 0);
            int goal = texts.goal(objective);
            ObjectiveProgressEvent event = new ObjectiveProgressEvent(holder, def, player, stage, objective, current, amount);
            if (!event.callEvent()) {
                continue;
            }
            int updated = Math.min(goal, current + event.amount());
            state.objectives.put(objective.id(), updated);
            state.touch();
            advanced++;
            announceProgress(player, holder, def, objective, updated, goal);
        }
        if (advanced > 0 && QuestFlow.isStageComplete(stage, state.objectives, texts::goal)) {
            completeStage(holder, def, stage, state, player, 0);
        }
        return advanced;
    }

    private void announceProgress(Player player, QuestHolder holder, QuestDefinition def, ConfiguredObjective objective, int progress, int goal) {
        TagResolver resolvers = TagResolver.resolver(
                questName(def),
                Text.component("objective", texts.objective(objective)),
                Text.unparsed("progress", Integer.toString(progress)),
                Text.unparsed("goal", Integer.toString(goal)));
        if (progress >= goal) {
            for (Player member : onlineMembers(holder)) {
                messages.send(member, "quest.objective-complete", resolvers);
            }
        } else if (progressActionBar) {
            messages.actionBar(player, "quest.objective-progress", resolvers);
        }
    }

    // ------------------------------------------------------------------ flow

    private void enterStage(QuestHolder holder, QuestDefinition def, QuestState state, String stageId,
                            @Nullable QuestStage from, @Nullable Player trigger, int depth) {
        Optional<QuestStage> target = def.stage(stageId);
        if (target.isEmpty()) {
            logger.severe("Quest " + def.id() + " references missing stage " + stageId + "; failing it for " + holder.id());
            endQuest(holder, state, QuestStatus.FAILED);
            return;
        }
        QuestStage stage = target.get();
        state.stageId = stage.id();
        state.stageComplete = false;
        state.objectives.clear();
        state.visited.add(stage.id());
        state.touch();
        new QuestStageChangeEvent(holder, def, from, stage).callEvent();
        for (Player member : onlineMembers(holder)) {
            messages.send(member, "quest.stage-started", questName(def), Text.parsed("stage", stage.displayName()));
            for (String line : stage.description()) {
                member.sendMessage(Text.parse(line));
            }
        }
        if (QuestFlow.isStageComplete(stage, state.objectives, texts::goal)) {
            completeStage(holder, def, stage, state, trigger, depth + 1);
        }
    }

    private void completeStage(QuestHolder holder, QuestDefinition def, QuestStage stage, QuestState state,
                               @Nullable Player trigger, int depth) {
        state.stageComplete = true;
        state.touch();
        grant(holder, stage.rewards());
        resolveTransition(holder, def, stage, state, trigger, depth);
    }

    private void resolveTransition(QuestHolder holder, QuestDefinition def, QuestStage stage, QuestState state,
                                   @Nullable Player trigger, int depth) {
        if (depth > MAX_CHAINED_TRANSITIONS) {
            reportOnce("loop/" + def.id(), "Quest " + def.id() + " chained more than " + MAX_CHAINED_TRANSITIONS
                    + " stages without objectives; stopping at stage " + stage.id() + ". Check the quest for loops.");
            return;
        }
        if (stage.isFinal()) {
            completeQuest(holder, def, state);
            return;
        }
        Player evaluator = trigger != null ? trigger : onlineMembers(holder).stream().findFirst().orElse(null);
        if (evaluator == null) {
            return; // Retried when a member is online.
        }
        if (stage.branchMode() == BranchMode.CHOICE) {
            offerChoices(holder, def, stage);
            return;
        }
        OptionalInt index = QuestFlow.firstAvailable(stage.transitions(), t -> conditionsPass(t.conditions(), evaluator, holder));
        if (index.isPresent()) {
            follow(holder, def, stage, state, stage.transitions().get(index.getAsInt()), evaluator, depth);
        }
    }

    private void follow(QuestHolder holder, QuestDefinition def, QuestStage from, QuestState state, StageTransition transition,
                        @Nullable Player trigger, int depth) {
        state.taken.put(from.id(), transition.target().serialize());
        state.touch();
        switch (transition.target()) {
            case TransitionTarget.ToStage(String stageId) -> enterStage(holder, def, state, stageId, from, trigger, depth + 1);
            case TransitionTarget.Complete ignored -> completeQuest(holder, def, state);
            case TransitionTarget.Fail ignored -> {
                endQuest(holder, state, QuestStatus.FAILED);
                for (Player member : onlineMembers(holder)) {
                    messages.send(member, "quest.failed", questName(def));
                }
                new QuestEndEvent(holder, def, QuestStatus.FAILED).callEvent();
            }
        }
    }

    private void completeQuest(QuestHolder holder, QuestDefinition def, QuestState state) {
        state.completions++;
        state.lastCompletedAt = System.currentTimeMillis();
        endQuest(holder, state, QuestStatus.COMPLETED);
        grant(holder, def.rewards());
        for (Player member : onlineMembers(holder)) {
            messages.send(member, "quest.completed", questName(def));
        }
        new QuestEndEvent(holder, def, QuestStatus.COMPLETED).callEvent();
    }

    private static void endQuest(QuestHolder holder, QuestState state, QuestStatus status) {
        state.status = status;
        state.stageId = null;
        state.stageComplete = false;
        state.objectives.clear();
        state.touch();
    }

    private void offerChoices(QuestHolder holder, QuestDefinition def, QuestStage stage) {
        for (Player member : onlineMembers(holder)) {
            Map<Integer, StageTransition> choices = availableChoices(holder, def.id(), member);
            if (choices.isEmpty()) {
                continue;
            }
            messages.send(member, "quest.choice-header", questName(def), Text.parsed("stage", stage.displayName()));
            choices.forEach((index, transition) -> {
                String command = "/journeys choose " + def.id() + " " + index;
                Component line = messages.get("quest.choice-option",
                                Text.parsed("label", transition.label() == null ? transition.target().serialize() : transition.label()))
                        .clickEvent(ClickEvent.runCommand(command))
                        .hoverEvent(HoverEvent.showText(messages.get("quest.choice-hover")));
                member.sendMessage(line);
            });
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The quest's requirements plus, if enabled, its {@code journeys.quest.<id>} permission. */
    public boolean meetsRequirements(QuestDefinition def, Player player, QuestHolder holder) {
        return hasQuestPermission(def, player) && conditionsPass(def.requirements(), player, holder);
    }

    /** True when the quest does not require its permission, or the player has it. */
    public static boolean hasQuestPermission(QuestDefinition def, Player player) {
        return !def.settings().requirePermission() || player.hasPermission(QuestSettings.permissionNode(def.id()));
    }

    private boolean conditionsPass(List<ConfiguredCondition> conditions, Player player, QuestHolder holder) {
        for (ConfiguredCondition condition : conditions) {
            if (!test(condition, player, holder)) {
                return false;
            }
        }
        return true;
    }

    /** Evaluates one condition, including negation. Unknown or failing types count as not passed. */
    public boolean test(ConfiguredCondition condition, Player player, QuestHolder holder) {
        Optional<ConditionType> type = registries.conditions().get(condition.type());
        if (type.isEmpty()) {
            reportOnce("condition/" + condition.type(), "Unknown condition type " + condition.type().asString() + "; treating it as failed");
            return false;
        }
        boolean result;
        try {
            result = type.get().test(player, holder, condition.params());
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Condition " + condition.type().asString() + " threw; treating it as failed", e);
            return false;
        }
        return result != condition.negate();
    }

    private void grant(QuestHolder holder, List<ConfiguredReward> rewards) {
        if (rewards.isEmpty()) {
            return;
        }
        for (Player member : onlineMembers(holder)) {
            for (ConfiguredReward reward : rewards) {
                Optional<RewardType> type = registries.rewards().get(reward.type());
                if (type.isEmpty()) {
                    reportOnce("reward/" + reward.type(), "Unknown reward type " + reward.type().asString() + "; skipped");
                    continue;
                }
                try {
                    type.get().grant(member, reward.params());
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Reward " + reward.type().asString() + " failed for " + member.getName(), e);
                }
            }
        }
    }

    private static List<Player> onlineMembers(QuestHolder holder) {
        List<Player> players = new ArrayList<>();
        for (UUID member : holder.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private static TagResolver questName(QuestDefinition def) {
        return Text.parsed("quest", def.displayName());
    }

    private void reportOnce(String key, String message) {
        if (reportedErrors.add(key)) {
            logger.warning(message);
        }
    }
}
