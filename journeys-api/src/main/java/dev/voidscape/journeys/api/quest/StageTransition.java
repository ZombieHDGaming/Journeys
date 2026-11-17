package dev.voidscape.journeys.api.quest;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An edge in the quest graph.
 *
 * @param target     where the edge leads
 * @param label      MiniMessage text shown for {@link BranchMode#CHOICE} stages; may be null for automatic ones
 * @param conditions all must pass for the transition to be available
 */
public record StageTransition(TransitionTarget target, @Nullable String label, List<ConfiguredCondition> conditions) {

    public StageTransition {
        Objects.requireNonNull(target, "target");
        conditions = List.copyOf(conditions);
    }
}
