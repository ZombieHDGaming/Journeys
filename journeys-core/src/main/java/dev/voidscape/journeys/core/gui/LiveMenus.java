package dev.voidscape.journeys.core.gui;

import dev.voidscape.journeys.core.engine.QuestChanges;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Keeps open {@linkplain Menu#live() live} menus current: instantly when Journeys changes a player's quests (via
 * {@link QuestChanges}), and on a timer to catch changes Journeys cannot see, such as permissions or levels.
 *
 * Open menus are found through each player's open inventory, so there is no registry to leak when menus close.
 */
public final class LiveMenus {

    private final Plugin plugin;
    private @Nullable BukkitTask timer;

    public LiveMenus(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start(QuestChanges changes, int intervalSeconds) {
        changes.subscribe(LiveMenus::refresh);
        if (intervalSeconds > 0) {
            long period = intervalSeconds * 20L;
            timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(LiveMenus::refresh), period, period);
        }
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
        }
    }

    public static void refresh(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu && menu.live()) {
            menu.refresh();
        }
    }
}
