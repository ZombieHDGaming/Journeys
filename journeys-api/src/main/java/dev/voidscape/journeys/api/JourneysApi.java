package dev.voidscape.journeys.api;

import dev.voidscape.journeys.api.block.PlacedBlocks;
import dev.voidscape.journeys.api.holder.HolderService;
import dev.voidscape.journeys.api.progress.QuestService;
import dev.voidscape.journeys.api.progress.QuestTracking;
import dev.voidscape.journeys.api.quest.QuestRepository;
import dev.voidscape.journeys.api.text.Placeholders;
import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.ObjectiveTracker;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.RewardType;
import dev.voidscape.journeys.api.type.TypeRegistry;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Entry point for other plugins.
 *
 * <p>Typical extension: implement {@link ObjectiveType} (and {@link org.bukkit.event.Listener}), report progress
 * through {@link #objectiveTracker()}, and register it with {@link #objectiveTypes()}. Registered types show up in
 * the in-game quest editor automatically, driven by their declared parameters.</p>
 */
public interface JourneysApi {

    /** Quest definitions loaded from disk. */
    QuestRepository quests();

    /** Starting, advancing and querying quest progress. */
    QuestService questService();

    /** Report objective progress from your own listeners. */
    ObjectiveTracker objectiveTracker();

    /** Resolves who owns quest progress (a single player or a party). */
    HolderService holders();

    /** The quest each player follows, shown on their boss bar and in placeholders. */
    QuestTracking tracking();

    /** External placeholder support (PlaceholderAPI) for player-facing text. */
    Placeholders placeholders();

    /** Tracks player-placed blocks, for objectives that should not count place-and-break farming. */
    PlacedBlocks placedBlocks();

    /**
     * Opens the quest board for a player.
     *
     * @param category a category id to open directly, or null for the full board
     */
    void openBoard(Player player, @Nullable String category);

    TypeRegistry<ObjectiveType> objectiveTypes();

    TypeRegistry<ConditionType> conditionTypes();

    TypeRegistry<RewardType> rewardTypes();
}
