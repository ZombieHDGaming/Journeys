package dev.voidscape.journeys.core.config;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed view of {@code config.yml}.
 *
 * @param storage         progress storage settings
 * @param autosaveSeconds how often changed progress is flushed to storage
 * @param progressActionBar whether objective progress is shown in the action bar
 * @param liveRefreshSeconds how often open player menus redraw to catch outside changes; 0 disables the timer
 * @param bossBarProgressSeconds how long on-progress boss bars stay visible after a change
 */
public record PluginConfig(StorageSettings storage, int autosaveSeconds, boolean progressActionBar, int liveRefreshSeconds,
                           int bossBarProgressSeconds) {

    public static PluginConfig from(FileConfiguration config) {
        ConfigurationSection storage = config.getConfigurationSection("storage");
        if (storage == null) {
            storage = config.createSection("storage");
        }
        return new PluginConfig(
                StorageSettings.from(storage),
                Math.max(10, config.getInt("autosave-seconds", 300)),
                config.getBoolean("progress-action-bar", true),
                Math.max(0, config.getInt("gui.live-refresh-seconds", 2)),
                Math.max(1, config.getInt("bossbar.on-progress-seconds", 5))
        );
    }

    public enum StorageType {
        SQLITE,
        MYSQL,
        MARIADB;

        static StorageType parse(String raw) {
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown storage type '" + raw + "'. Use sqlite, mysql or mariadb.");
            }
        }
    }

    public record StorageSettings(
            StorageType type,
            String sqliteFile,
            String host,
            int port,
            String database,
            String username,
            String password,
            int poolSize,
            String tablePrefix
    ) {

        static StorageSettings from(ConfigurationSection section) {
            String prefix = section.getString("table-prefix", "journeys_");
            if (!prefix.matches("[A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("storage.table-prefix may only contain letters, digits and underscores");
            }
            return new StorageSettings(
                    StorageType.parse(section.getString("type", "sqlite")),
                    section.getString("sqlite.file", "progress.db"),
                    section.getString("sql.host", "localhost"),
                    section.getInt("sql.port", 3306),
                    section.getString("sql.database", "journeys"),
                    section.getString("sql.username", "root"),
                    section.getString("sql.password", ""),
                    Math.max(2, section.getInt("sql.pool-size", 6)),
                    prefix
            );
        }
    }
}
