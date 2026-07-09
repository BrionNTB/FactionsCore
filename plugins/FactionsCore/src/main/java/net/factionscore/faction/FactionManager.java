package net.factionscore.faction;

import net.factionscore.storage.Database;
import org.powernukkitx.Server;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.Position;
import org.powernukkitx.utils.Config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory source of truth for every faction, claim and relation, backed by SQLite. All the hot
 * paths (claim lookups on block break/place/damage, relation checks on PvP) are plain
 * ConcurrentHashMap reads; SQL only happens on structural changes (create/disband/claim/kick/...)
 * and on load/save.
 */
public final class FactionManager {

    private final Database database;
    private final Config config;

    private final Map<String, Faction> factionsById = new ConcurrentHashMap<>();
    private final Map<String, Faction> factionsByLowerName = new ConcurrentHashMap<>();
    private final Map<UUID, String> memberIndex = new ConcurrentHashMap<>();
    private final Map<ClaimKey, String> claims = new ConcurrentHashMap<>();

    public FactionManager(Database database, Config config) {
        this.database = database;
        this.config = config;
        load();
    }

    // ---------------------------------------------------------------- lookups

    public Optional<Faction> get(String id) {
        return Optional.ofNullable(factionsById.get(id));
    }

    public Optional<Faction> getByName(String name) {
        return Optional.ofNullable(factionsByLowerName.get(name.toLowerCase()));
    }

    public Optional<Faction> getByMember(UUID uuid) {
        String id = memberIndex.get(uuid);
        return id == null ? Optional.empty() : get(id);
    }

    public Optional<Faction> getClaimOwner(String levelName, int chunkX, int chunkZ) {
        String id = claims.get(new ClaimKey(levelName, chunkX, chunkZ));
        return id == null ? Optional.empty() : get(id);
    }

    public Optional<Faction> getClaimOwner(Position position) {
        Level level = position.getLevel();
        if (level == null) {
            return Optional.empty();
        }
        return getClaimOwner(level.getName(), position.getFloorX() >> 4, position.getFloorZ() >> 4);
    }

    public java.util.Collection<Faction> all() {
        return factionsById.values();
    }

    // ---------------------------------------------------------------- lifecycle

    public synchronized Faction create(String name, UUID leader) throws FactionException {
        if (getByMember(leader).isPresent()) {
            throw new FactionException("You are already in a faction.");
        }
        if (getByName(name).isPresent()) {
            throw new FactionException("A faction with that name already exists.");
        }
        String id = UUID.randomUUID().toString();
        Faction faction = new Faction(id, name, leader);
        faction.setPower(config.getDouble("factions.power.starting", 10.0));

        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO factions (id, name, leader, power, created_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, name);
            statement.setString(3, leader.toString());
            statement.setDouble(4, faction.getPower());
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
            insertMember(id, leader, FactionRole.LEADER);
        } catch (SQLException e) {
            throw new FactionException("Storage error creating faction: " + e.getMessage());
        }

        factionsById.put(id, faction);
        factionsByLowerName.put(name.toLowerCase(), faction);
        memberIndex.put(leader, id);
        return faction;
    }

    public synchronized void disband(Faction faction) {
        try (PreparedStatement statement = database.connection().prepareStatement("DELETE FROM factions WHERE id = ?")) {
            statement.setString(1, faction.getId());
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // best effort; in-memory state is still cleared below
        }
        factionsById.remove(faction.getId());
        factionsByLowerName.remove(faction.getName().toLowerCase());
        faction.members().keySet().forEach(memberIndex::remove);
        claims.entrySet().removeIf(e -> e.getValue().equals(faction.getId()));
    }

    public synchronized void join(Faction faction, UUID player, FactionRole role) {
        faction.members().put(player, role);
        memberIndex.put(player, faction.getId());
        insertMember(faction.getId(), player, role);
    }

    public synchronized void leave(Faction faction, UUID player) {
        faction.members().remove(player);
        memberIndex.remove(player);
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM faction_members WHERE faction_id = ? AND player_uuid = ?")) {
            statement.setString(1, faction.getId());
            statement.setString(2, player.toString());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
        if (faction.members().isEmpty()) {
            disband(faction);
        }
    }

    private void insertMember(String factionId, UUID player, FactionRole role) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO faction_members (faction_id, player_uuid, role) VALUES (?, ?, ?) " +
                        "ON CONFLICT(faction_id, player_uuid) DO UPDATE SET role = excluded.role")) {
            statement.setString(1, factionId);
            statement.setString(2, player.toString());
            statement.setString(3, role.name());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    // ---------------------------------------------------------------- claims

    public synchronized boolean claim(Faction faction, String levelName, int chunkX, int chunkZ) {
        ClaimKey key = new ClaimKey(levelName, chunkX, chunkZ);
        if (claims.containsKey(key)) {
            return false;
        }
        double chunkCost = config.getDouble("factions.claims.chunk-cost", 1.0);
        long owned = claims.values().stream().filter(id -> id.equals(faction.getId())).count();
        if (owned + 1 > faction.maxClaims(chunkCost)) {
            return false;
        }
        claims.put(key, faction.getId());
        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO faction_claims (level_name, chunk_x, chunk_z, faction_id) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, levelName);
            statement.setInt(2, chunkX);
            statement.setInt(3, chunkZ);
            statement.setString(4, faction.getId());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
        return true;
    }

    public synchronized boolean unclaim(Faction faction, String levelName, int chunkX, int chunkZ) {
        ClaimKey key = new ClaimKey(levelName, chunkX, chunkZ);
        if (!faction.getId().equals(claims.get(key))) {
            return false;
        }
        claims.remove(key);
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM faction_claims WHERE level_name = ? AND chunk_x = ? AND chunk_z = ?")) {
            statement.setString(1, levelName);
            statement.setInt(2, chunkX);
            statement.setInt(3, chunkZ);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
        return true;
    }

    // ---------------------------------------------------------------- relations

    public synchronized void setRelation(Faction a, Faction b, RelationType type) {
        a.relations().put(b.getId(), type);
        b.relations().put(a.getId(), type);
        persistRelation(a.getId(), b.getId(), type);
        persistRelation(b.getId(), a.getId(), type);
    }

    private void persistRelation(String factionId, String otherId, RelationType type) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO faction_relations (faction_id, other_faction_id, relation) VALUES (?, ?, ?) " +
                        "ON CONFLICT(faction_id, other_faction_id) DO UPDATE SET relation = excluded.relation")) {
            statement.setString(1, factionId);
            statement.setString(2, otherId);
            statement.setString(3, type.name());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    // ---------------------------------------------------------------- power

    public synchronized void applyPowerRegenTick() {
        double perHour = config.getDouble("factions.power.regen-per-hour", 1.0);
        double maxPerMember = config.getDouble("factions.power.max-per-member", 10.0);
        // called once per (config) interval representing one hour's worth of regen
        for (Faction faction : factionsById.values()) {
            double cap = faction.members().size() * maxPerMember;
            if (faction.getPower() < cap) {
                faction.addPower(Math.min(perHour, cap - faction.getPower()));
                persistPower(faction);
            }
        }
    }

    public synchronized void onMemberDeath(Faction faction) {
        double loss = config.getDouble("factions.power.loss-per-death", 4.0);
        faction.addPower(-loss);
        persistPower(faction);
    }

    private void persistPower(Faction faction) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE factions SET power = ? WHERE id = ?")) {
            statement.setDouble(1, faction.getPower());
            statement.setString(2, faction.getId());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    // ---------------------------------------------------------------- home

    public void setHome(Faction faction, Position position) {
        faction.setHome(position);
        Level level = position.getLevel();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE factions SET home_level = ?, home_x = ?, home_y = ?, home_z = ? WHERE id = ?")) {
            statement.setString(1, level == null ? null : level.getName());
            statement.setDouble(2, position.x);
            statement.setDouble(3, position.y);
            statement.setDouble(4, position.z);
            statement.setString(5, faction.getId());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    // ---------------------------------------------------------------- load

    private void load() {
        Connection connection = database.connection();
        Map<String, Faction> loaded = new ConcurrentHashMap<>();
        try (Statement dummy = connection.createStatement();
             ResultSet rs = dummy.executeQuery("SELECT id, name, power, home_level, home_x, home_y, home_z FROM factions")) {
            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                Faction faction = new Faction(id, name, null);
                faction.setPower(rs.getDouble("power"));
                String homeLevel = rs.getString("home_level");
                if (homeLevel != null) {
                    Level level = Server.getInstance().getLevelByName(homeLevel);
                    if (level != null) {
                        faction.setHome(new Position(rs.getDouble("home_x"), rs.getDouble("home_y"), rs.getDouble("home_z"), level));
                    }
                }
                loaded.put(id, faction);
                factionsByLowerName.put(name.toLowerCase(), faction);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load factions", e);
        }

        try (Statement dummy = connection.createStatement();
             ResultSet rs = dummy.executeQuery("SELECT faction_id, player_uuid, role FROM faction_members")) {
            while (rs.next()) {
                Faction faction = loaded.get(rs.getString("faction_id"));
                if (faction == null) continue;
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                faction.members().put(uuid, FactionRole.valueOf(rs.getString("role")));
                memberIndex.put(uuid, faction.getId());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load faction members", e);
        }

        try (Statement dummy = connection.createStatement();
             ResultSet rs = dummy.executeQuery("SELECT faction_id, other_faction_id, relation FROM faction_relations")) {
            while (rs.next()) {
                Faction faction = loaded.get(rs.getString("faction_id"));
                if (faction == null) continue;
                faction.relations().put(rs.getString("other_faction_id"), RelationType.valueOf(rs.getString("relation")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load faction relations", e);
        }

        try (Statement dummy = connection.createStatement();
             ResultSet rs = dummy.executeQuery("SELECT level_name, chunk_x, chunk_z, faction_id FROM faction_claims")) {
            while (rs.next()) {
                claims.put(new ClaimKey(rs.getString("level_name"), rs.getInt("chunk_x"), rs.getInt("chunk_z")), rs.getString("faction_id"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load faction claims", e);
        }

        factionsById.putAll(loaded);
    }

    public static final class FactionException extends Exception {
        public FactionException(String message) {
            super(message);
        }
    }
}
