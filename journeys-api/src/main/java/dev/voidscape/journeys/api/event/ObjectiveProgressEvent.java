package dev.voidscape.journeys.api.event;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestStage;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** Fired before progress is added to an objective. The amount can be changed, e.g. for boosters. */
public final class ObjectiveProgressEvent extends QuestEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final QuestStage stage;
    private final ConfiguredObjective objective;
    private final int current;
    private int amount;
    private boolean cancelled;

    public ObjectiveProgressEvent(QuestHolder holder, QuestDefinition quest, Player player, QuestStage stage,
                                  ConfiguredObjective objective, int current, int amount) {
        super(holder, quest);
        this.player = player;
        this.stage = stage;
        this.objective = objective;
        this.current = current;
        this.amount = amount;
    }

    /** The player whose action caused the progress. */
    public Player player() {
        return player;
    }

    public QuestStage stage() {
        return stage;
    }

    public ConfiguredObjective objective() {
        return objective;
    }

    /** Progress before this event. */
    public int current() {
        return current;
    }

    public int amount() {
        return amount;
    }

    /** Sets how much progress is added. Values below 1 cancel the progress. */
    public void amount(int amount) {
        this.amount = amount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled || amount < 1;
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
