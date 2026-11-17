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

    /**
     * Takes progress away, for objectives that count something which can be undone, such as placing a block that is
     * later broken. Only objectives in a stage that is still in progress are affected; progress never drops below 0.
     * Fires a cancellable {@link dev.voidscape.journeys.api.event.ObjectiveRegressEvent} for each objective first.
     *
     * @return how many objectives lost progress
     */
    int regress(Player player, ObjectiveType type, Predicate<ParameterValues> matcher, int amount);
}
