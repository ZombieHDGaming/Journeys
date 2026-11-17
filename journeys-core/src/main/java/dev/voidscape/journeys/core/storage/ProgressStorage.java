package dev.voidscape.journeys.core.storage;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persists quest progress. Methods block and must never be called on the server thread; the engine runs them on its
 * own executor.
 */
public interface ProgressStorage extends AutoCloseable {

    /** Creates or migrates the schema. */
    void init() throws Exception;

    /** All records of a holder. */
    List<QuestRecord> load(UUID holderId) throws Exception;

    /** Inserts or replaces the given records, atomically. */
    void save(Collection<QuestRecord> records) throws Exception;

    @Override
    void close();
}
