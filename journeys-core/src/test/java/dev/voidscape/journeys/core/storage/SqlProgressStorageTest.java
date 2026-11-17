package dev.voidscape.journeys.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.voidscape.journeys.api.progress.QuestStatus;
import dev.voidscape.journeys.core.storage.sql.SqlDialect;
import dev.voidscape.journeys.core.storage.sql.SqlProgressStorage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlProgressStorageTest {

    @TempDir
    Path dir;

    private SqlProgressStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        storage = open();
        storage.init();
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    private SqlProgressStorage open() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dir.resolve("test.db"));
        config.setMaximumPoolSize(1);
        return new SqlProgressStorage(new HikariDataSource(config), SqlDialect.SQLITE, "test_", Logger.getLogger("test"));
    }

    @Test
    void savesAndLoadsRecords() throws Exception {
        UUID holder = UUID.randomUUID();
        QuestRecord active = new QuestRecord(holder, "q1", QuestStatus.ACTIVE, "gather", false, 10, 20, 0, 0, Map.of("logs", 4, "planks", 1),
                List.of("start", "gather"), Map.of("start", "gather"));
        QuestRecord done = new QuestRecord(holder, "q2", QuestStatus.COMPLETED, null, false, 1, 2, 3, 2, Map.of(),
                List.of("a", "b"), Map.of("a", "b", "b", "@complete"));
        storage.save(List.of(active, done));

        List<QuestRecord> loaded = storage.load(holder);
        assertEquals(2, loaded.size());
        assertTrue(loaded.contains(active));
        assertTrue(loaded.contains(done));
        assertTrue(storage.load(UUID.randomUUID()).isEmpty());
    }

    @Test
    void savingAgainReplacesObjectiveProgress() throws Exception {
        UUID holder = UUID.randomUUID();
        storage.save(List.of(new QuestRecord(holder, "q", QuestStatus.ACTIVE, "a", false, 1, 1, 0, 0, Map.of("x", 1, "y", 2), List.of(), Map.of())));
        QuestRecord next = new QuestRecord(holder, "q", QuestStatus.ACTIVE, "b", true, 1, 5, 0, 0, Map.of("z", 7),
                List.of("a", "b"), Map.of("a", "b"));
        storage.save(List.of(next));

        assertEquals(List.of(next), storage.load(holder));
    }

    @Test
    void migrationsAreIdempotent() throws Exception {
        UUID holder = UUID.randomUUID();
        storage.save(List.of(new QuestRecord(holder, "q", QuestStatus.FAILED, null, false, 1, 1, 0, 0, Map.of(), List.of(), Map.of())));
        storage.close();

        storage = open();
        storage.init();
        assertEquals(1, storage.load(holder).size());
    }
}
