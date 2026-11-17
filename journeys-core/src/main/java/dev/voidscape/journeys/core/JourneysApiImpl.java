package dev.voidscape.journeys.core;

import dev.voidscape.journeys.api.JourneysApi;
import dev.voidscape.journeys.api.block.PlacedBlocks;
import dev.voidscape.journeys.api.holder.HolderService;
import dev.voidscape.journeys.api.progress.QuestService;
import dev.voidscape.journeys.api.quest.QuestRepository;
import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.ObjectiveTracker;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.RewardType;
import dev.voidscape.journeys.api.type.TypeRegistry;
import dev.voidscape.journeys.core.type.Registries;

/** The object handed to other plugins. */
record JourneysApiImpl(
        QuestRepository quests,
        QuestService questService,
        ObjectiveTracker objectiveTracker,
        HolderService holders,
        PlacedBlocks placedBlocks,
        Registries registries
) implements JourneysApi {

    @Override
    public TypeRegistry<ObjectiveType> objectiveTypes() {
        return registries.objectives();
    }

    @Override
    public TypeRegistry<ConditionType> conditionTypes() {
        return registries.conditions();
    }

    @Override
    public TypeRegistry<RewardType> rewardTypes() {
        return registries.rewards();
    }
}
