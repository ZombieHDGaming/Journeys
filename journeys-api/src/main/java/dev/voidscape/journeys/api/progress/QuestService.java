package dev.voidscape.journeys.api.progress;

import dev.voidscape.journeys.api.holder.QuestHolder;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Starts, advances and reads quest progress. Mutating methods must be called on the server thread. */
public interface QuestService {

    /**
     * Checks whether the quest could be started right now, without starting it.
     *
     * @param initiator player requirements are checked against; with null, requirements are skipped
     */
    StartResult canStart(QuestHolder holder, String questId, @Nullable Player initiator);

    /**
     * Starts a quest.
     *
     * @param initiator player requirements are checked against; with null, requirements are skipped
     * @param notify    whether to tell the initiator why the quest could not be started
     */
    StartResult start(QuestHolder holder, String questId, @Nullable Player initiator, boolean notify);

    /** Abandons an active quest. Returns false if it was not active. */
    boolean abandon(QuestHolder holder, String questId);

    /**
     * Picks a transition on a {@link dev.voidscape.journeys.api.quest.BranchMode#CHOICE} stage whose objectives are
     * done.
     *
     * @param transitionIndex index into the stage's transitions
     * @return false if no choice is pending, the index is invalid, or the transition's conditions fail
     */
    boolean choose(QuestHolder holder, String questId, int transitionIndex, Player chooser);

    Optional<QuestProgress> progress(QuestHolder holder, String questId);

    /** Every quest the holder has a record for, including completed ones. Empty if not loaded. */
    Collection<QuestProgress> allProgress(QuestHolder holder);

    /** Whether the holder's progress is loaded and can be read or changed. */
    boolean isLoaded(QuestHolder holder);
}
