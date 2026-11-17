package dev.voidscape.journeys.core.storage.sql;

/** The few SQL differences between the supported databases. */
public enum SqlDialect {

    SQLITE {
        @Override
        String upsertQuest(String table) {
            return "INSERT INTO " + table + " (holder_id, quest_id, status, stage_id, stage_complete, started_at, updated_at, completions, last_completed_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON CONFLICT (holder_id, quest_id) DO UPDATE SET status = excluded.status, stage_id = excluded.stage_id,"
                    + " stage_complete = excluded.stage_complete, started_at = excluded.started_at, updated_at = excluded.updated_at,"
                    + " completions = excluded.completions, last_completed_at = excluded.last_completed_at";
        }
    },

    /** MySQL and MariaDB. */
    MYSQL {
        @Override
        String upsertQuest(String table) {
            return "INSERT INTO " + table + " (holder_id, quest_id, status, stage_id, stage_complete, started_at, updated_at, completions, last_completed_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE status = VALUES(status), stage_id = VALUES(stage_id), stage_complete = VALUES(stage_complete),"
                    + " started_at = VALUES(started_at), updated_at = VALUES(updated_at), completions = VALUES(completions),"
                    + " last_completed_at = VALUES(last_completed_at)";
        }
    };

    abstract String upsertQuest(String table);
}
