package dev.voidscape.journeys.api.type;

import dev.voidscape.journeys.api.holder.QuestHolder;
import org.bukkit.entity.Player;

/** A check used for quest requirements and branch transitions, e.g. "has permission" or "completed quest". */
public interface ConditionType extends ElementType {

    /**
     * Evaluates the condition.
     *
     * @param player the player the check is made for (the one starting the quest or triggering the branch)
     * @param holder the holder that owns the quest progress
     * @param values configured parameters
     */
    boolean test(Player player, QuestHolder holder, ParameterValues values);
}
