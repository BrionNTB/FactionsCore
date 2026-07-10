package net.factionscore.crate;

import net.factionscore.storage.Database;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.item.Item;
import org.powernukkitx.level.Level;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chest-based crates: an admin binds a placed chest to a crate tier, players redeem the matching
 * key on it and a reward rolls from the tier's config loot table (weighted by per-entry chance;
 * entries can be items or console commands, so keys can pay out anything -- spawners, money via
 * command, enchant books via /enchant givebook, more keys...).
 */
public final class CrateManager {

    private final Database database;
    private final Config config;

    /** Admins who ran /crate set|remove and whose next chest click completes the action. */
    private final Map<UUID, String> pendingSelection = new ConcurrentHashMap<>();

    public CrateManager(Database database, Config config) {
        this.database = database;
        this.config = config;
    }

    public void armSelection(UUID admin, String action) {
        pendingSelection.put(admin, action);
    }

    public String consumeSelection(UUID admin) {
        return pendingSelection.remove(admin);
    }

    public boolean hasPendingSelection(UUID admin) {
        return pendingSelection.containsKey(admin);
    }

    public Optional<String> crateTypeAt(Level level, Vector3 pos) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT crate_type FROM crate_locations WHERE level_name = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, level.getName());
            statement.setInt(2, pos.getFloorX());
            statement.setInt(3, pos.getFloorY());
            statement.setInt(4, pos.getFloorZ());
            ResultSet rs = statement.executeQuery();
            return rs.next() ? Optional.of(rs.getString("crate_type")) : Optional.empty();
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public void register(Level level, Vector3 pos, String crateType) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO crate_locations (level_name, x, y, z, crate_type) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(level_name, x, y, z) DO UPDATE SET crate_type = excluded.crate_type
                """)) {
            statement.setString(1, level.getName());
            statement.setInt(2, pos.getFloorX());
            statement.setInt(3, pos.getFloorY());
            statement.setInt(4, pos.getFloorZ());
            statement.setString(5, crateType.toLowerCase());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void unregister(Level level, Vector3 pos) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM crate_locations WHERE level_name = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, level.getName());
            statement.setInt(2, pos.getFloorX());
            statement.setInt(3, pos.getFloorY());
            statement.setInt(4, pos.getFloorZ());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public boolean isKnownType(String crateType) {
        var section = config.getSection("crates");
        return section != null && section.containsKey(crateType.toLowerCase());
    }

    public Optional<KeyItem> createKey(String crateType, int count) {
        KeyItem key = switch (crateType.toLowerCase()) {
            case "common" -> new CommonKeyItem();
            case "rare" -> new RareKeyItem();
            case "legendary" -> new LegendaryKeyItem();
            default -> null;
        };
        if (key == null) {
            return Optional.empty();
        }
        key.setCount(count);
        return Optional.of(key);
    }

    public void openReward(Player player, String crateType) {
        List<Map<?, ?>> loot = config.getMapList("crates." + crateType.toLowerCase() + ".loot");
        if (loot.isEmpty()) {
            player.sendMessage(TextFormat.RED + "This crate has no loot configured.");
            return;
        }

        double totalWeight = 0;
        for (Map<?, ?> entry : loot) {
            totalWeight += entry.get("chance") instanceof Number n ? n.doubleValue() : 1.0;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        Map<?, ?> chosen = loot.get(loot.size() - 1);
        for (Map<?, ?> entry : loot) {
            roll -= entry.get("chance") instanceof Number n ? n.doubleValue() : 1.0;
            if (roll <= 0) {
                chosen = entry;
                break;
            }
        }

        Object command = chosen.get("command");
        if (command != null) {
            String resolved = command.toString().replace("{player}", player.getName());
            try {
                Server.getInstance().executeCommand(Server.getInstance().getConsoleSender(), resolved);
            } catch (Exception ignored) {
            }
            player.sendMessage(TextFormat.GOLD + "[Crate] " + TextFormat.GREEN + "You won a special reward!");
            return;
        }

        Object id = chosen.get("id");
        if (id == null) {
            return;
        }
        int count = chosen.get("count") instanceof Number n ? n.intValue() : 1;
        Item item = Item.get(id.toString());
        item.setCount(count);
        if (item.isNull()) {
            return;
        }
        for (Item leftover : player.getInventory().addItem(item)) {
            player.getLevel().dropItem(player, leftover);
        }
        player.sendMessage(TextFormat.GOLD + "[Crate] " + TextFormat.GREEN + "You won " + count + "x " + id + "!");
    }
}
