package dev.voidscape.journeys.api.event;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.progress.QuestStatus;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import org.bukkit.event.HandlerList;

/** Fired after a quest stops being active: completed, failed or abandoned. */
public final class QuestEndEvent extends QuestEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final QuestStatus result;

    public QuestEndEvent(QuestHolder holder, QuestDefinition quest, QuestStatus result) {
        super(holder, quest);
        if (result.isActive()) {
            throw new IllegalArgumentException("result must not be ACTIVE");
        }
        this.result = result;
    }

    /** {@code COMPLETED}, {@code FAILED} or {@code ABANDONED}. */
    public QuestStatus result() {
        return result;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
