package dev.voidscape.journeys.integration.placeholderapi;

import dev.voidscape.journeys.api.JourneysApi;
import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.progress.QuestProgress;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestStage;
import dev.voidscape.journeys.api.type.ObjectiveType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * The {@code %journeys_...%} placeholders. See {@link #compute} for the list.
 *
 * <p>Quest state lives on the server thread, but plugins such as scoreboards may ask for placeholders from other
 * threads. Those requests are answered from a cache that a server-thread task refreshes every second; the first
 * async request for a placeholder returns an empty string until the cache has filled.</p>
 */
final class JourneysExpansion extends PlaceholderExpansion {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final List<String> QUEST_FIELDS = List.of("_stage_name", "_completions", "_progress", "_status", "_stage");

    private final JourneysApi api;
    private final Plugin plugin;
    private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();
    private @Nullable BukkitTask refresh;

    JourneysExpansion(JourneysApi api, Plugin plugin) {
        this.api = api;
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "journeys";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Stay registered across PlaceholderAPI reloads; Journeys unregisters it on disable. */
    @Override
    public boolean persist() {
        return true;
    }

    void startCacheRefresh() {
        refresh = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshCache, 20, 20);
    }

    void stopCacheRefresh() {
        if (refresh != null) {
            refresh.cancel();
        }
        cache.clear();
    }

    private void refreshCache() {
        for (UUID id : Set.copyOf(cache.keySet())) {
            Player player = Bukkit.getPlayer(id);
            Map<String, String> values = cache.get(id);
            if (player == null || values == null) {
                cache.remove(id);
                continue;
            }
            for (String params : Set.copyOf(values.keySet())) {
                String value = compute(player, params);
                values.put(params, value == null ? "" : value);
            }
        }
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer offline, String params) {
        Player player = offline == null ? null : offline.getPlayer();
        if (player == null) {
            return ""; // Progress is only loaded for online players.
        }
        if (Bukkit.isPrimaryThread()) {
            String value = compute(player, params);
            cache.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>()).put(params, value == null ? "" : value);
            return value;
        }
        return cache.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>()).computeIfAbsent(params, p -> "");
    }

    /**
     * <ul>
     *   <li>{@code active_count}, {@code completed_count}: active quests, and different quests completed</li>
     *   <li>{@code tracked}: tracked quest id; {@code tracked_name}, {@code tracked_stage}: display names</li>
     *   <li>{@code tracked_objective}: current objective; {@code tracked_objective_progress}: e.g. 3/10</li>
     *   <li>{@code tracked_progress}: percent through the current stage</li>
     *   <li>{@code quest_<id>_status}: active, completed, failed, abandoned or none</li>
     *   <li>{@code quest_<id>_stage}, {@code quest_<id>_stage_name}, {@code quest_<id>_completions},
     *   {@code quest_<id>_progress}</li>
     * </ul>
     * Names are returned with legacy (section sign) colors, which scoreboards and tab plugins understand.
     *
     * @return the value, or null for an unknown placeholder
     */
    @Nullable String compute(Player player, String params) {
        switch (params) {
            case "active_count" -> {
                return Long.toString(allProgress(player).stream().filter(p -> p.status().isActive()).count());
            }
            case "completed_count" -> {
                Set<String> done = new HashSet<>();
                allProgress(player).stream().filter(p -> p.completions() > 0).forEach(p -> done.add(p.questId()));
                return Integer.toString(done.size());
            }
            default -> {
            }
        }
        if (params.startsWith("tracked")) {
            Optional<String> tracked = api.tracking().tracked(player);
            String field = params.equals("tracked") ? "_id" : params.substring("tracked".length());
            return tracked.map(id -> questField(player, id, field)).orElse("");
        }
        if (params.startsWith("quest_")) {
            String rest = params.substring("quest_".length());
            for (String field : QUEST_FIELDS) {
                if (rest.endsWith(field) && rest.length() > field.length()) {
                    return questField(player, rest.substring(0, rest.length() - field.length()), field);
                }
            }
        }
        return null;
    }

    private @Nullable String questField(Player player, String questId, String field) {
        Optional<QuestDefinition> quest = api.quests().get(questId);
        if (quest.isEmpty()) {
            return "";
        }
        Optional<QuestHolder> holder = holderWithProgress(player, questId);
        Optional<QuestProgress> progress = holder.flatMap(h -> api.questService().progress(h, questId));
        Optional<QuestStage> stage = progress.filter(p -> p.status().isActive()).flatMap(QuestProgress::currentStage).flatMap(quest.get()::stage);
        return switch (field) {
            case "_id" -> questId;
            case "_name" -> legacy(player, quest.get().displayName());
            case "_status" -> progress.map(p -> p.status().name().toLowerCase(java.util.Locale.ROOT)).orElse("none");
            case "_stage" -> stage.map(QuestStage::id).orElse("");
            case "_stage_name" -> stage.map(s -> legacy(player, s.displayName())).orElse("");
            case "_completions" -> Integer.toString(progress.map(QuestProgress::completions).orElse(0));
            case "_progress" -> holder.map(h -> Integer.toString((int) Math.floor(api.questService().stageCompletion(h, questId) * 100))).orElse("0");
            case "_objective" -> stage.flatMap(s -> currentObjective(s, progress.get())).map(this::describe).orElse("");
            case "_objective_progress" -> stage.flatMap(s -> currentObjective(s, progress.get()))
                    .map(o -> Math.min(o.amount(), progress.get().objectiveProgress(o.id())) + "/" + o.amount()).orElse("");
            default -> null;
        };
    }

    /** The first unfinished objective of the stage. */
    private Optional<ConfiguredObjective> currentObjective(QuestStage stage, QuestProgress progress) {
        return stage.objectives().stream()
                .filter(o -> progress.objectiveProgress(o.id()) < goal(o))
                .findFirst();
    }

    private int goal(ConfiguredObjective objective) {
        return api.objectiveTypes().get(objective.type()).filter(t -> !t.countable()).isPresent() ? 1 : objective.amount();
    }

    private String describe(ConfiguredObjective objective) {
        if (objective.description() != null) {
            return LEGACY.serialize(MiniMessage.miniMessage().deserialize(objective.description()));
        }
        Component text = api.objectiveTypes().get(objective.type())
                .map(type -> safeDescribe(type, objective))
                .orElse(Component.text(objective.type().asString()));
        return LEGACY.serialize(text);
    }

    private static Component safeDescribe(ObjectiveType type, ConfiguredObjective objective) {
        try {
            return type.describe(objective.params());
        } catch (RuntimeException e) {
            return type.displayName();
        }
    }

    private String legacy(Player player, String miniMessage) {
        return LEGACY.serialize(MiniMessage.miniMessage().deserialize(api.placeholders().apply(player, miniMessage)));
    }

    /** The holder (solo or party) that has progress on the quest, preferring one where it is active. */
    private Optional<QuestHolder> holderWithProgress(Player player, String questId) {
        QuestHolder fallback = null;
        for (QuestHolder holder : api.holders().holdersOf(player.getUniqueId())) {
            Optional<QuestProgress> progress = api.questService().progress(holder, questId);
            if (progress.isPresent()) {
                if (progress.get().status().isActive()) {
                    return Optional.of(holder);
                }
                if (fallback == null) {
                    fallback = holder;
                }
            }
        }
        return Optional.ofNullable(fallback);
    }

    private List<QuestProgress> allProgress(Player player) {
        return api.holders().holdersOf(player.getUniqueId()).stream()
                .flatMap(holder -> api.questService().allProgress(holder).stream())
                .toList();
    }
}
