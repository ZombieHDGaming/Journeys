package dev.voidscape.journeys.core.bossbar;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.progress.QuestProgress;
import dev.voidscape.journeys.api.quest.BossBarSettings;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestStage;
import dev.voidscape.journeys.core.config.Messages;
import dev.voidscape.journeys.core.engine.QuestChanges;
import dev.voidscape.journeys.core.engine.QuestEngine;
import dev.voidscape.journeys.core.engine.QuestTrackingService;
import dev.voidscape.journeys.core.text.PlaceholderService;
import dev.voidscape.journeys.core.text.Text;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Shows each player one boss bar for their tracked quest, when that quest has its boss bar enabled.
 *
 * <p>Bars update on the next tick after any quest change and once a second otherwise, which also keeps
 * PlaceholderAPI values in the title fresh and hides on-progress bars when their time is up.</p>
 */
public final class QuestBossBars implements Listener {

    private final Plugin plugin;
    private final QuestEngine engine;
    private final QuestTrackingService tracking;
    private final Messages messages;
    private final PlaceholderService placeholders;
    private final long showMillis;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Set<UUID> showing = new HashSet<>();
    private final Map<UUID, Long> visibleUntil = new HashMap<>();
    private @Nullable BukkitTask timer;

    public QuestBossBars(Plugin plugin, QuestEngine engine, QuestTrackingService tracking, Messages messages,
                         PlaceholderService placeholders, int onProgressSeconds) {
        this.plugin = plugin;
        this.engine = engine;
        this.tracking = tracking;
        this.messages = messages;
        this.placeholders = placeholders;
        this.showMillis = Math.max(1, onProgressSeconds) * 1000L;
    }

    public void start(QuestChanges changes) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        changes.subscribe(this::changed);
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::update), 20, 20);
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            hide(player);
        }
        bars.clear();
    }

    /** Something changed for the player: on-progress bars become visible again. */
    private void changed(Player player) {
        visibleUntil.put(player.getUniqueId(), System.currentTimeMillis() + showMillis);
        update(player);
    }

    public void update(Player player) {
        Optional<String> questId = tracking.tracked(player);
        Optional<QuestHolder> holder = questId.flatMap(id -> engine.activeHolder(player.getUniqueId(), id));
        Optional<QuestDefinition> quest = questId.flatMap(id -> engine.questDefinition(id));
        if (holder.isEmpty() || quest.isEmpty() || !quest.get().settings().bossBar().enabled()) {
            hide(player);
            return;
        }
        BossBarSettings settings = quest.get().settings().bossBar();
        if (settings.mode() == BossBarSettings.Mode.ON_PROGRESS
                && System.currentTimeMillis() > visibleUntil.getOrDefault(player.getUniqueId(), 0L)) {
            hide(player);
            return;
        }
        Optional<QuestProgress> progress = engine.progress(holder.get(), quest.get().id());
        Optional<QuestStage> stage = progress.flatMap(QuestProgress::currentStage).flatMap(quest.get()::stage);
        if (progress.isEmpty() || stage.isEmpty()) {
            hide(player);
            return;
        }

        double completion = engine.stageCompletion(holder.get(), quest.get().id());
        Component title = title(player, quest.get(), stage.get(), progress.get(), completion);

        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                id -> BossBar.bossBar(Component.empty(), 0, settings.color(), settings.overlay()));
        bar.name(title);
        bar.progress((float) Math.clamp(completion, 0, 1));
        bar.color(settings.color());
        bar.overlay(settings.overlay());
        if (showing.add(player.getUniqueId())) {
            player.showBossBar(bar);
        }
    }

    private Component title(Player player, QuestDefinition quest, QuestStage stage, QuestProgress progress, double completion) {
        TagResolver questName = engine.questName(quest, player);
        TagResolver stageName = placeholders.parsed(player, "stage", stage.displayName());
        if (progress.stageComplete()) {
            return messages.get(player, "bossbar.title-waiting", questName, stageName);
        }
        // Show the first objective still to do; in an "any" stage every objective is an option, so the first will do.
        ConfiguredObjective current = null;
        for (ConfiguredObjective objective : stage.objectives()) {
            if (progress.objectiveProgress(objective.id()) < engine.texts().goal(objective)) {
                current = objective;
                break;
            }
        }
        if (current == null) {
            return messages.get(player, "bossbar.title-waiting", questName, stageName);
        }
        int goal = engine.texts().goal(current);
        return messages.get(player, "bossbar.title", questName, stageName,
                Text.component("objective", engine.texts().objective(current)),
                Text.unparsed("progress", Integer.toString(Math.min(goal, progress.objectiveProgress(current.id())))),
                Text.unparsed("goal", Integer.toString(goal)),
                Text.unparsed("percent", Integer.toString((int) Math.floor(completion * 100))));
    }

    private void hide(Player player) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar != null && showing.remove(player.getUniqueId())) {
            player.hideBossBar(bar);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        bars.remove(id);
        showing.remove(id);
        visibleUntil.remove(id);
    }
}
