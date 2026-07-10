package net.factionscore.shop;

import net.factionscore.storage.Database;
import org.powernukkitx.utils.Config;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Prices for mob-loot/crop-loot items, each with a demand multiplier that drifts every
 * {@code sell.demand.fluctuate-hours} (default 4h) via a clamped random walk -- nudges up or down
 * a bounded percentage rather than re-rolling from scratch, so prices feel like they're trending
 * rather than teleporting.
 */
public final class SellManager {

    private final Database database;
    private final Config config;
    private final Map<String, Double> basePrices = new LinkedHashMap<>();
    private final Map<String, Double> demandMultiplier = new ConcurrentHashMap<>();

    public SellManager(Database database, Config config) {
        this.database = database;
        this.config = config;
        loadBasePrices();
        loadDemand();
    }

    private void loadBasePrices() {
        var section = config.getSection("sell.prices");
        if (section == null) {
            return;
        }
        for (var entry : section.entrySet()) {
            if (entry.getValue() instanceof Number n) {
                basePrices.put(entry.getKey(), n.doubleValue());
            }
        }
    }

    private void loadDemand() {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT item_id, multiplier FROM sell_demand")) {
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                demandMultiplier.put(rs.getString("item_id"), rs.getDouble("multiplier"));
            }
        } catch (SQLException ignored) {
        }
    }

    public Map<String, Double> basePrices() {
        return basePrices;
    }

    public double multiplierOf(String itemId) {
        return demandMultiplier.getOrDefault(itemId, 1.0);
    }

    public double priceOf(String itemId) {
        return basePrices.getOrDefault(itemId, 0.0) * multiplierOf(itemId);
    }

    /** Random-walks every tracked item's demand multiplier; called on a ~4h repeating task. */
    public void fluctuateDemand() {
        double maxSwing = config.getDouble("sell.demand.max-swing-per-tick", 0.20);
        double min = config.getDouble("sell.demand.min-multiplier", 0.4);
        double max = config.getDouble("sell.demand.max-multiplier", 1.8);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (String itemId : basePrices.keySet()) {
            double current = multiplierOf(itemId);
            double delta = (random.nextDouble() * 2 - 1) * maxSwing;
            double updated = Math.max(min, Math.min(max, current * (1 + delta)));
            demandMultiplier.put(itemId, updated);
            persistDemand(itemId, updated);
        }
    }

    private void persistDemand(String itemId, double multiplier) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO sell_demand (item_id, multiplier, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(item_id) DO UPDATE SET multiplier = excluded.multiplier, updated_at = excluded.updated_at
                """)) {
            statement.setString(1, itemId);
            statement.setDouble(2, multiplier);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public double getBalance(UUID player) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT balance FROM player_balance WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getDouble("balance") : 0.0;
        } catch (SQLException e) {
            return 0.0;
        }
    }

    public double addBalance(UUID player, double amount) {
        double newBalance = getBalance(player) + amount;
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO player_balance (player_uuid, balance) VALUES (?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET balance = excluded.balance
                """)) {
            statement.setString(1, player.toString());
            statement.setDouble(2, newBalance);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
        return newBalance;
    }
}
