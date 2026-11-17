package dev.voidscape.journeys.api.type;

import org.bukkit.entity.Player;

/** Something given to a player when a stage or quest completes, e.g. items, experience or a console command. */
public interface RewardType extends ElementType {

    /** Grants the reward to one player. Called once for every online member of the quest holder. */
    void grant(Player player, ParameterValues values);
}
