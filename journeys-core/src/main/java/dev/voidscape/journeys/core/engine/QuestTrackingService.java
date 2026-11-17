package dev.voidscape.journeys.core.engine;

import dev.voidscape.journeys.api.event.QuestEndEvent;
import dev.voidscape.journeys.api.event.QuestStageChangeEvent;
import dev.voidscape.journeys.api.holder.HolderService;
import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.progress.QuestProgress;
import dev.voidscape.journeys.api.progress.QuestTracking;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestRepository;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Stores each player's tracked quest in their persistent data, so it follows them across restarts without a database
 * table. On a network with shared MySQL progress, tracking is per server.
 */
public final class QuestTrackingService implements QuestTracking, Listener {

    private final NamespacedKey key;
    private final QuestEngine engine;
    private final QuestRepository quests;
    private final HolderService holders;
    private final QuestChanges changes;

    public QuestTrackingService(Plugin plugin, QuestEngine engine, QuestRepository quests, HolderService holders, QuestChanges changes) {
        this.key = new NamespacedKey(plugin, "tracked_quest");
        this.engine = engine;
        this.quests = quests;
        this.holders = holders;
        this.changes = changes;
    }

    @Override
    public Optional<String> tracked(Player player) {
        String id = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null || engine.activeHolder(player.getUniqueId(), id).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(id);
    }

    @Override
    public boolean track(Player player, @Nullable String questId) {
        if (questId == null) {
            player.getPersistentDataContainer().remove(key);
        } else if (engine.activeHolder(player.getUniqueId(), questId).isEmpty()) {
            return false;
        } else {
            player.getPersistentDataContainer().set(key, PersistentDataType.STRING, questId);
        }
        changes.mark(player.getUniqueId());
        return true;
    }

    /** A quest just started: track it if it has a boss bar, or if the player tracks nothing yet. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStart(QuestStageChangeEvent event) {
        if (event.from() != null) {
            return;
        }
        boolean bossBar = event.quest().settings().bossBar().enabled();
        for (UUID member : event.holder().members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && (bossBar || tracked(player).isEmpty())) {
                track(player, event.quest().id());
            }
        }
    }

    /** The tracked quest ended: move on to the player's most recent other active quest, preferring boss bar ones. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(QuestEndEvent event) {
        for (UUID member : event.holder().members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && event.quest().id().equals(player.getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                track(player, fallback(player));
            }
        }
    }

    private @Nullable String fallback(Player player) {
        Comparator<QuestProgress> order = Comparator
                .comparing((QuestProgress p) -> quests.get(p.questId()).map(QuestDefinition::settings).map(s -> s.bossBar().enabled()).orElse(false))
                .thenComparing(QuestProgress::startedAt);
        QuestProgress best = null;
        for (QuestHolder holder : holders.holdersOf(player.getUniqueId())) {
            for (QuestProgress progress : engine.allProgress(holder)) {
                if (progress.status().isActive() && quests.get(progress.questId()).isPresent()
                        && (best == null || order.compare(progress, best) > 0)) {
                    best = progress;
                }
            }
        }
        return best == null ? null : best.questId();
    }
}
