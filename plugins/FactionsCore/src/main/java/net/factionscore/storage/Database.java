package net.factionscore.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Single SQLite connection shared by every FactionsCore subsystem. SQLite is fine here: writes
 * are infrequent relative to the tick rate (claims/power/enchants/flags), and everything hot
 * (combat, movement, block checks) is served from in-memory structures that only get flushed to
 * this database periodically or on shutdown.
 */
public final class Database {

    private final Connection connection;

    public Database(File dataFolder, String fileName) throws SQLException {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create data folder " + dataFolder);
        }
        File dbFile = new File(dataFolder, fileName);
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA foreign_keys=ON");
        }
        createSchema();
    }

    public Connection connection() {
        return connection;
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS factions (
                    id TEXT PRIMARY KEY,
                    name TEXT UNIQUE NOT NULL,
                    leader TEXT,
                    power REAL NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    home_level TEXT,
                    home_x REAL,
                    home_y REAL,
                    home_z REAL,
                    spawner_value REAL NOT NULL DEFAULT 0
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS faction_members (
                    faction_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    role TEXT NOT NULL,
                    PRIMARY KEY (faction_id, player_uuid),
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS faction_relations (
                    faction_id TEXT NOT NULL,
                    other_faction_id TEXT NOT NULL,
                    relation TEXT NOT NULL,
                    PRIMARY KEY (faction_id, other_faction_id),
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS faction_claims (
                    level_name TEXT NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    faction_id TEXT NOT NULL,
                    PRIMARY KEY (level_name, chunk_x, chunk_z),
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS faction_tracked_spawners (
                    level_name TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    faction_id TEXT NOT NULL,
                    stack_level INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY (level_name, x, y, z),
                    FOREIGN KEY (faction_id) REFERENCES factions(id) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS anticheat_flags (
                    player_uuid TEXT NOT NULL,
                    check_name TEXT NOT NULL,
                    flagged_at INTEGER NOT NULL,
                    detail TEXT
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS item_custom_enchants (
                    item_key TEXT NOT NULL,
                    enchant_id TEXT NOT NULL,
                    level INTEGER NOT NULL
                )
                """);
        }
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // best effort on shutdown
        }
    }
}
