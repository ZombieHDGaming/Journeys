package dev.voidscape.journeys.core;

import dev.voidscape.journeys.api.JourneysApi;
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
import dev.voidscape.journeys.core.type.Registries;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** The object handed to other plugins. */
record JourneysApiImpl(
        QuestRepository quests,
        QuestService questService,
        ObjectiveTracker objectiveTracker,
        HolderService holders,
        QuestTracking tracking,
        Placeholders placeholders,
        PlacedBlocks placedBlocks,
        BiConsumer<Player, @Nullable String> boardOpener,
        Registries registries
) implements JourneysApi {

    @Override
    public void openBoard(Player player, @Nullable String category) {
        boardOpener.accept(player, category);
    }

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
