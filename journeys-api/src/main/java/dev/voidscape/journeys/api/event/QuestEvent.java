package dev.voidscape.journeys.api.event;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import org.bukkit.event.Event;

/** Base class of quest events. All are fired on the server thread. */
public abstract class QuestEvent extends Event {

    private final QuestHolder holder;
    private final QuestDefinition quest;

    protected QuestEvent(QuestHolder holder, QuestDefinition quest) {
        this.holder = holder;
        this.quest = quest;
    }

    public QuestHolder holder() {
        return holder;
    }

    public QuestDefinition quest() {
        return quest;
    }
}
