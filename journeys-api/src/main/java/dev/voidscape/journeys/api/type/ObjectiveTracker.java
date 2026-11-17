package dev.voidscape.journeys.api.type;

import java.util.function.Predicate;
import org.bukkit.entity.Player;

/** Receives progress reports from objective types. Must be called on the server thread. */
public interface ObjectiveTracker {

    /**
     * Adds progress to every objective of {@code type} that is currently active for the player (through any quest
     * holder they belong to) and whose parameters satisfy {@code matcher}.
     *
     * @param player  the player who performed the action
     * @param type    the objective type reporting
     * @param matcher tests an objective's configured parameters against the action
     * @param amount  how much progress to add, usually 1
     * @return how many objectives were advanced
     */
    int progress(Player player, ObjectiveType type, Predicate<ParameterValues> matcher, int amount);
}
