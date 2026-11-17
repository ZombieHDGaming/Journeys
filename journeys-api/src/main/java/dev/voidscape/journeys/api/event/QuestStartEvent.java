package dev.voidscape.journeys.api.event;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Fired before a quest starts. Cancelling it makes the start return {@code CANCELLED}. */
public final class QuestStartEvent extends QuestEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final @Nullable Player initiator;
    private boolean cancelled;

    public QuestStartEvent(QuestHolder holder, QuestDefinition quest, @Nullable Player initiator) {
        super(holder, quest);
        this.initiator = initiator;
    }

    /** The player who started the quest, or null when started by the console or the API. */
    public @Nullable Player initiator() {
        return initiator;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
