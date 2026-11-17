package dev.voidscape.journeys.core.storage.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.voidscape.journeys.api.progress.QuestStatus;
import dev.voidscape.journeys.core.config.PluginConfig.StorageSettings;
import dev.voidscape.journeys.core.storage.ProgressStorage;
import dev.voidscape.journeys.core.storage.QuestPath;
import dev.voidscape.journeys.core.storage.QuestRecord;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQL storage for SQLite, MySQL and MariaDB.
 *
 * <p>Schema changes are applied as numbered migrations tracked in {@code <prefix>schema_version}. To change the
 * schema, append a migration to {@link #migrations()}; never edit one that has shipped.</p>
 */
public final class SqlProgressStorage implements ProgressStorage {

    private final HikariDataSource dataSource;
    private final SqlDialect dialect;
    private final String prefix;
    private final Logger logger;

    public SqlProgressStorage(HikariDataSource dataSource, SqlDialect dialect, String prefix, Logger logger) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.prefix = prefix;
        this.logger = logger;
    }

    /** Builds the storage described by the config. */
    public static SqlProgressStorage create(StorageSettings settings, Path dataFolder, Logger logger) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("Journeys");
        SqlDialect dialect;
        switch (settings.type()) {
            case SQLITE -> {
                dialect = SqlDialect.SQLITE;
                config.setDriverClassName("org.sqlite.JDBC");
                config.setJdbcUrl("jdbc:sqlite:" + dataFolder.resolve(settings.sqliteFile()).toAbsolutePath());
                // SQLite allows a single writer; one connection avoids SQLITE_BUSY entirely.
                config.setMaximumPoolSize(1);
                config.setConnectionInitSql("PRAGMA journal_mode=WAL");
            }
            case MYSQL, MARIADB -> {
                dialect = SqlDialect.MYSQL;
                // The MariaDB driver speaks to MySQL as well, so only one driver is shipped.
                config.setDriverClassName("org.mariadb.jdbc.Driver");
                config.setJdbcUrl("jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/" + settings.database());
                config.setUsername(settings.username());
                config.setPassword(settings.password());
                config.setMaximumPoolSize(settings.poolSize());
            }
            default -> throw new IllegalStateException("Unhandled storage type " + settings.type());
        }
        return new SqlProgressStorage(new HikariDataSource(config), dialect, settings.tablePrefix(), logger);
    }

    private String table(String name) {
        return prefix + name;
    }

    /** Ordered schema migrations. Index + 1 is the schema version. */
    private List<String[]> migrations() {
        return List.<String[]>of(
                new String[]{
                        "CREATE TABLE IF NOT EXISTS " + table("quest_progress") + " ("
                                + "holder_id CHAR(36) NOT NULL,"
                                + "quest_id VARCHAR(64) NOT NULL,"
                                + "status VARCHAR(16) NOT NULL,"
                                + "stage_id VARCHAR(64),"
                                + "stage_complete BOOLEAN NOT NULL DEFAULT FALSE,"
                                + "started_at BIGINT NOT NULL,"
                                + "updated_at BIGINT NOT NULL,"
                                + "completions INT NOT NULL DEFAULT 0,"
                                + "last_completed_at BIGINT NOT NULL DEFAULT 0,"
                                + "PRIMARY KEY (holder_id, quest_id))",
                        "CREATE TABLE IF NOT EXISTS " + table("objective_progress") + " ("
                                + "holder_id CHAR(36) NOT NULL,"
                                + "quest_id VARCHAR(64) NOT NULL,"
                                + "objective_id VARCHAR(64) NOT NULL,"
                                + "progress INT NOT NULL,"
                                + "PRIMARY KEY (holder_id, quest_id, objective_id))"
                },
                // 2: stage and branch history, used by the quest_stage and quest_branch conditions.
                new String[]{
                        "ALTER TABLE " + table("quest_progress") + " ADD COLUMN path TEXT"
                }
        );
    }

    @Override
    public void init() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("schema_version") + " (version INT NOT NULL)");
            int current = 0;
            try (ResultSet rs = statement.executeQuery("SELECT MAX(version) FROM " + table("schema_version"))) {
                if (rs.next()) {
                    current = rs.getInt(1);
                }
            }
            List<String[]> migrations = migrations();
            if (current > migrations.size()) {
                throw new SQLException("Database schema version " + current + " is newer than this version of Journeys supports ("
                        + migrations.size() + "). Update the plugin.");
            }
            for (int version = current + 1; version <= migrations.size(); version++) {
                connection.setAutoCommit(false);
                try {
                    for (String sql : migrations.get(version - 1)) {
                        statement.executeUpdate(sql);
                    }
                    statement.executeUpdate("DELETE FROM " + table("schema_version"));
                    statement.executeUpdate("INSERT INTO " + table("schema_version") + " (version) VALUES (" + version + ")");
                    connection.commit();
                    logger.info("Applied storage migration " + version);
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    @Override
    public List<QuestRecord> load(UUID holderId) throws SQLException {
        String holder = holderId.toString();
        Map<String, Map<String, Integer>> objectives = new HashMap<>();
        List<QuestRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT quest_id, objective_id, progress FROM " + table("objective_progress") + " WHERE holder_id = ?")) {
                ps.setString(1, holder);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        objectives.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>()).put(rs.getString(2), rs.getInt(3));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT quest_id, status, stage_id, stage_complete, started_at, updated_at, completions, last_completed_at, path FROM "
                            + table("quest_progress") + " WHERE holder_id = ?")) {
                ps.setString(1, holder);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String questId = rs.getString(1);
                        QuestStatus status;
                        try {
                            status = QuestStatus.valueOf(rs.getString(2));
                        } catch (IllegalArgumentException e) {
                            logger.warning("Skipping progress of " + holder + " on " + questId + ": unknown status " + rs.getString(2));
                            continue;
                        }
                        QuestPath.Path path = QuestPath.decode(rs.getString(9));
                        records.add(new QuestRecord(holderId, questId, status, rs.getString(3), rs.getBoolean(4),
                                rs.getLong(5), rs.getLong(6), rs.getInt(7), rs.getLong(8),
                                objectives.getOrDefault(questId, Map.of()), path.visited(), path.taken()));
                    }
                }
            }
        }
        return records;
    }

    @Override
    public void save(Collection<QuestRecord> records) throws SQLException {
        if (records.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement upsert = connection.prepareStatement(dialect.upsertQuest(table("quest_progress")));
                 PreparedStatement clear = connection.prepareStatement(
                         "DELETE FROM " + table("objective_progress") + " WHERE holder_id = ? AND quest_id = ?");
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO " + table("objective_progress") + " (holder_id, quest_id, objective_id, progress) VALUES (?, ?, ?, ?)")) {
                for (QuestRecord record : records) {
                    String holder = record.holderId().toString();
                    upsert.setString(1, holder);
                    upsert.setString(2, record.questId());
                    upsert.setString(3, record.status().name());
                    if (record.stageId() == null) {
                        upsert.setNull(4, Types.VARCHAR);
                    } else {
                        upsert.setString(4, record.stageId());
                    }
                    upsert.setBoolean(5, record.stageComplete());
                    upsert.setLong(6, record.startedAt());
                    upsert.setLong(7, record.updatedAt());
                    upsert.setInt(8, record.completions());
                    upsert.setLong(9, record.lastCompletedAt());
                    upsert.setString(10, QuestPath.encode(record.visitedStages(), record.transitionsTaken()));
                    upsert.addBatch();

                    clear.setString(1, holder);
                    clear.setString(2, record.questId());
                    clear.addBatch();

                    for (Map.Entry<String, Integer> objective : record.objectives().entrySet()) {
                        insert.setString(1, holder);
                        insert.setString(2, record.questId());
                        insert.setString(3, objective.getKey());
                        insert.setInt(4, objective.getValue());
                        insert.addBatch();
                    }
                }
                upsert.executeBatch();
                clear.executeBatch();
                insert.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
