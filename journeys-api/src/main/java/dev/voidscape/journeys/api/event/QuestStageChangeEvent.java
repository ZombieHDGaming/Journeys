package dev.voidscape.journeys.api.event;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestStage;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Fired after a holder enters a stage, including the start stage. */
public final class QuestStageChangeEvent extends QuestEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final @Nullable QuestStage from;
    private final QuestStage to;

    public QuestStageChangeEvent(QuestHolder holder, QuestDefinition quest, @Nullable QuestStage from, QuestStage to) {
        super(holder, quest);
        this.from = from;
        this.to = to;
    }

    /** The previous stage, or null when the quest just started. */
    public @Nullable QuestStage from() {
        return from;
    }

    public QuestStage to() {
        return to;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
