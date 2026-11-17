package dev.voidscape.journeys.api.quest;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

/** Loaded quest definitions. Reads are thread-safe; writes should happen on the server thread. */
public interface QuestRepository {

    Optional<QuestDefinition> get(String id);

    /** All valid, loaded quests. */
    Collection<QuestDefinition> all();

    /**
     * Saves a quest to disk and makes it live. Active progress on the quest is migrated to the new definition.
     *
     * @throws IOException if the file could not be written
     */
    void save(QuestDefinition quest) throws IOException;

    /**
     * Deletes a quest file. Existing progress records stay in storage but become inactive.
     *
     * @return false if no quest with that id existed
     */
    boolean delete(String id) throws IOException;
}
