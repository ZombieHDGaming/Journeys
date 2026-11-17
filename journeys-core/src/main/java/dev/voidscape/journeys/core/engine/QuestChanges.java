package dev.voidscape.journeys.core.engine;

import dev.voidscape.journeys.api.event.ObjectiveProgressEvent;
import dev.voidscape.journeys.api.event.ObjectiveRegressEvent;
import dev.voidscape.journeys.api.event.QuestEndEvent;
import dev.voidscape.journeys.api.event.QuestEvent;
import dev.voidscape.journeys.api.event.QuestStageChangeEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Collects players whose quest state changed and notifies subscribers once, on the next tick. Running on the next
 * tick means the change has been applied (progress events fire just before it is) and that a burst of changes, such
 * as a stage completing and the next one starting, produces a single update.
 */
public final class QuestChanges implements Listener {

    private final Plugin plugin;
    private final Set<UUID> pending = new LinkedHashSet<>();
    private final List<Consumer<Player>> subscribers = new ArrayList<>();

    public QuestChanges(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Called on the server thread with each online player whose quests changed. */
    public void subscribe(Consumer<Player> subscriber) {
        subscribers.add(subscriber);
    }

    /** Marks a player as changed, e.g. after their tracked quest changes. */
    public void mark(UUID playerId) {
        if (!plugin.isEnabled()) {
            return;
        }
        boolean first = pending.isEmpty();
        pending.add(playerId);
        if (first) {
            Bukkit.getScheduler().runTask(plugin, this::flush);
        }
    }

    private void flush() {
        List<UUID> ids = List.copyOf(pending);
        pending.clear();
        for (UUID id : ids) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                subscribers.forEach(subscriber -> subscriber.accept(player));
            }
        }
    }

    private void markMembers(QuestEvent event) {
        event.holder().members().forEach(this::mark);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStage(QuestStageChangeEvent event) {
        markMembers(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(QuestEndEvent event) {
        markMembers(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProgress(ObjectiveProgressEvent event) {
        markMembers(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegress(ObjectiveRegressEvent event) {
        markMembers(event);
    }
}
