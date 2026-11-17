package dev.voidscape.journeys.core.gui;

import dev.voidscape.journeys.api.event.ObjectiveProgressEvent;
import dev.voidscape.journeys.api.event.ObjectiveRegressEvent;
import dev.voidscape.journeys.api.event.QuestEndEvent;
import dev.voidscape.journeys.api.event.QuestEvent;
import dev.voidscape.journeys.api.event.QuestStageChangeEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Keeps open {@linkplain Menu#live() live} menus current.
 *
 * <ul>
 *   <li>Instantly, when Journeys changes a player's quests (including progress taken back): refreshes are batched and run on the next tick, after
 *   the change has been applied (some events fire just before it is).</li>
 *   <li>On a timer, to catch changes Journeys cannot see, such as permissions or experience levels.</li>
 * </ul>
 *
 * Open menus are found through each player's open inventory, so there is no registry to leak when menus close.
 */
public final class LiveMenus implements Listener {

    private final Plugin plugin;
    private final Set<UUID> pending = new HashSet<>();
    private @Nullable BukkitTask timer;

    public LiveMenus(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start(int intervalSeconds) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (intervalSeconds > 0) {
            long period = intervalSeconds * 20L;
            timer = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, period, period);
        }
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private static void refresh(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu && menu.live()) {
            menu.refresh();
        }
    }

    /** Refreshes the open menus of every member of the event's holder on the next tick. */
    private void schedule(QuestEvent event) {
        if (!plugin.isEnabled()) {
            return;
        }
        boolean first = pending.isEmpty();
        pending.addAll(event.holder().members());
        if (first) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (UUID id : pending) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        refresh(player);
                    }
                }
                pending.clear();
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStage(QuestStageChangeEvent event) {
        schedule(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(QuestEndEvent event) {
        schedule(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProgress(ObjectiveProgressEvent event) {
        schedule(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegress(ObjectiveRegressEvent event) {
        schedule(event);
    }
}
