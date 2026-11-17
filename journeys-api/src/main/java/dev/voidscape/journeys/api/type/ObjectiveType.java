package dev.voidscape.journeys.api.type;

/**
 * Something a player has to do, e.g. kill entities or talk to an NPC.
 *
 * <p>Objective types report progress themselves. The usual pattern is to also implement
 * {@link org.bukkit.event.Listener}: Journeys registers registered types that are listeners automatically and
 * unregisters them with the type. Inside the handler, call
 * {@link ObjectiveTracker#progress(org.bukkit.entity.Player, ObjectiveType, java.util.function.Predicate, int)}
 * with a predicate that checks the configured parameters against the event.</p>
 */
public interface ObjectiveType extends ElementType {

    /**
     * Whether the objective has a meaningful goal amount (kill 10 zombies) or is a one-off (reach a location).
     * One-off objectives always use an amount of 1 and the editor hides the amount control.
     */
    default boolean countable() {
        return true;
    }
}
