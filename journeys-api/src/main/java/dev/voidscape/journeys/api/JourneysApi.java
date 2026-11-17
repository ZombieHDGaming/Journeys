package dev.voidscape.journeys.api;

import dev.voidscape.journeys.api.holder.HolderService;
import dev.voidscape.journeys.api.progress.QuestService;
import dev.voidscape.journeys.api.quest.QuestRepository;
import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.ObjectiveTracker;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.RewardType;
import dev.voidscape.journeys.api.type.TypeRegistry;

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

    TypeRegistry<ObjectiveType> objectiveTypes();

    TypeRegistry<ConditionType> conditionTypes();

    TypeRegistry<RewardType> rewardTypes();
}
