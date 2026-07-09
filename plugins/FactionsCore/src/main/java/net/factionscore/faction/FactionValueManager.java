package net.factionscore.faction;

import net.factionscore.storage.Database;
import org.powernukkitx.level.Level;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.utils.Config;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Tracks how much a faction's territory is "worth" beyond raw power: claimed land plus the mob
 * spawners inside it, each spawner's contribution scaling with its stack level (a stacked spawner
 * is worth proportionally more, same as it produces proportionally more mobs/loot). Spawner
 * ownership+level is persisted per-block so restarts, stacking, and breaking all keep the ledger
 * accurate without ever needing to rescan a faction's whole claimed area.
 */
public final class FactionValueManager {

    private final Database database;
    private final FactionManager factions;
    private final Config config;

    public FactionValueManager(Database database, FactionManager factions, Config config) {
        this.database = database;
        this.factions = factions;
        this.config = config;
    }

    public double totalValue(Faction faction) {
        double landValue = claimCount(faction) * config.getDouble("factions.value.land-per-claim", 500);
        return landValue + faction.getSpawnerValue();
    }

    private long claimCount(Faction faction) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT COUNT(*) FROM faction_claims WHERE faction_id = ?")) {
            statement.setString(1, faction.getId());
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    /** Call when a mob spawner is placed inside claimed territory. */
    public void onSpawnerPlaced(Level level, Vector3 pos, Faction owner) {
        double unit = config.getDouble("factions.value.spawner-value", 5000);
        owner.setSpawnerValue(owner.getSpawnerValue() + unit);
        persistFactionValue(owner);
        upsertTrackedSpawner(level, pos, owner.getId(), 1);
    }

    /** Call after a spawner already tracked for a faction gets its stack level raised. */
    public void onSpawnerStackIncreased(Level level, Vector3 pos, int newLevel) {
        String factionId = trackedFactionId(level, pos);
        if (factionId == null) return;
        factions.get(factionId).ifPresent(owner -> {
            double unit = config.getDouble("factions.value.spawner-value", 5000);
            owner.setSpawnerValue(owner.getSpawnerValue() + unit);
            persistFactionValue(owner);
            upsertTrackedSpawner(level, pos, factionId, newLevel);
        });
    }

    /** Call when a tracked spawner is broken; removes all value it had accumulated (base * stack level). */
    public void onSpawnerBroken(Level level, Vector3 pos) {
        String factionId = trackedFactionId(level, pos);
        if (factionId == null) return;
        int stackLevel = trackedStackLevel(level, pos);
        factions.get(factionId).ifPresent(owner -> {
            double unit = config.getDouble("factions.value.spawner-value", 5000);
            owner.setSpawnerValue(owner.getSpawnerValue() - unit * stackLevel);
            persistFactionValue(owner);
        });
        removeTrackedSpawner(level, pos);
    }

    private void persistFactionValue(Faction faction) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE factions SET spawner_value = ? WHERE id = ?")) {
            statement.setDouble(1, faction.getSpawnerValue());
            statement.setString(2, faction.getId());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void upsertTrackedSpawner(Level level, Vector3 pos, String factionId, int stackLevel) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO faction_tracked_spawners (level_name, x, y, z, faction_id, stack_level)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(level_name, x, y, z) DO UPDATE SET stack_level = excluded.stack_level, faction_id = excluded.faction_id
                """)) {
            statement.setString(1, level.getName());
            statement.setInt(2, pos.getFloorX());
            statement.setInt(3, pos.getFloorY());
            statement.setInt(4, pos.getFloorZ());
            statement.setString(5, factionId);
            statement.setInt(6, stackLevel);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void removeTrackedSpawner(Level level, Vector3 pos) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM faction_tracked_spawners WHERE level_name = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, level.getName());
            statement.setInt(2, pos.getFloorX());
            statement.setInt(3, pos.getFloorY());
            statement.setInt(4, pos.getFloorZ());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private String trackedFactionId(Level level, Vector3 pos) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT faction_id FROM faction_tracked_spawners WHERE level_name = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, level.getName());
            statement.setInt(2, pos.getFloorX());
            statement.setInt(3, pos.getFloorY());
            statement.setInt(4, pos.getFloorZ());
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            return null;
        }
    }

    private int trackedStackLevel(Level level, Vector3 pos) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT stack_level FROM faction_tracked_spawners WHERE level_name = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, level.getName());
            statement.setInt(2, pos.getFloorX());
            statement.setInt(3, pos.getFloorY());
            statement.setInt(4, pos.getFloorZ());
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getInt(1) : 1;
        } catch (SQLException e) {
            return 1;
        }
    }
}
