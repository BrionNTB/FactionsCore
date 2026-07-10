package net.factionscore.kit;

import net.factionscore.storage.Database;
import org.powernukkitx.item.Item;
import org.powernukkitx.utils.Config;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Config-defined kits with per-player cooldowns persisted in SQLite (so relogging doesn't reset
 * them). Kit contents are plain item id + count pairs; anything richer (enchanted kit gear) is
 * better done by giving enchant books in the kit and letting players apply them.
 */
public final class KitManager {

    public record Kit(String name, long cooldownMs, String permission, List<Item> items) {
    }

    private final Database database;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(Database database, Config config) {
        this.database = database;
        load(config);
    }

    private void load(Config config) {
        var section = config.getSection("kits");
        if (section == null) {
            return;
        }
        for (String kitName : section.keySet()) {
            String base = "kits." + kitName;
            long cooldownMs = (long) (config.getDouble(base + ".cooldown-hours", 24) * 3_600_000L);
            String permission = config.getString(base + ".permission", "");
            List<Item> items = new ArrayList<>();
            for (Map<?, ?> entry : config.getMapList(base + ".items")) {
                Object id = entry.get("id");
                Object count = entry.get("count");
                if (id == null) continue;
                Item item = Item.get(id.toString());
                item.setCount(count instanceof Number n ? n.intValue() : 1);
                if (!item.isNull()) {
                    items.add(item);
                }
            }
            kits.put(kitName.toLowerCase(), new Kit(kitName, cooldownMs, permission, items));
        }
    }

    public Map<String, Kit> kits() {
        return kits;
    }

    public Kit get(String name) {
        return kits.get(name.toLowerCase());
    }

    /** @return remaining cooldown in ms, or 0 if claimable now. */
    public long remainingCooldown(UUID player, Kit kit) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT claimed_at FROM kit_claims WHERE player_uuid = ? AND kit_name = ?")) {
            statement.setString(1, player.toString());
            statement.setString(2, kit.name().toLowerCase());
            ResultSet rs = statement.executeQuery();
            if (!rs.next()) {
                return 0;
            }
            long elapsed = System.currentTimeMillis() - rs.getLong("claimed_at");
            return Math.max(0, kit.cooldownMs() - elapsed);
        } catch (SQLException e) {
            return 0;
        }
    }

    public void markClaimed(UUID player, Kit kit) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO kit_claims (player_uuid, kit_name, claimed_at) VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, kit_name) DO UPDATE SET claimed_at = excluded.claimed_at
                """)) {
            statement.setString(1, player.toString());
            statement.setString(2, kit.name().toLowerCase());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }
}
