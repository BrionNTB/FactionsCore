package net.factionscore.bounty;

import net.factionscore.economy.EconomyManager;
import net.factionscore.storage.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Player-funded kill contracts. The money is escrowed out of the placer's balance the moment the
 * bounty is set (so a placer can't set bounties they can't cover, and logging out doesn't dodge
 * payment); stacking another bounty on the same target adds to the pot.
 */
public final class BountyManager {

    public record Bounty(UUID target, String targetName, double amount) {
    }

    private final Database database;
    private final EconomyManager economy;

    public BountyManager(Database database, EconomyManager economy) {
        this.database = database;
        this.economy = economy;
    }

    /** @return false if the placer can't afford it. */
    public synchronized boolean place(UUID placer, UUID target, String targetName, double amount) {
        if (!economy.withdraw(placer, amount)) {
            return false;
        }
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO bounties (target_uuid, target_name, amount, placed_by) VALUES (?, ?, ?, ?)
                ON CONFLICT(target_uuid) DO UPDATE SET amount = amount + excluded.amount, placed_by = excluded.placed_by
                """)) {
            statement.setString(1, target.toString());
            statement.setString(2, targetName);
            statement.setDouble(3, amount);
            statement.setString(4, placer.toString());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            economy.deposit(placer, amount); // refund on storage failure
            return false;
        }
    }

    /** Removes the bounty and pays it to the killer. @return the paid amount, 0 if no bounty. */
    public synchronized double claim(UUID target, UUID killer) {
        double amount = amountOn(target);
        if (amount <= 0) {
            return 0;
        }
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM bounties WHERE target_uuid = ?")) {
            statement.setString(1, target.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            return 0;
        }
        economy.deposit(killer, amount);
        return amount;
    }

    public double amountOn(UUID target) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT amount FROM bounties WHERE target_uuid = ?")) {
            statement.setString(1, target.toString());
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getDouble("amount") : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public List<Bounty> all() {
        List<Bounty> bounties = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT target_uuid, target_name, amount FROM bounties ORDER BY amount DESC LIMIT 20")) {
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                bounties.add(new Bounty(UUID.fromString(rs.getString("target_uuid")), rs.getString("target_name"), rs.getDouble("amount")));
            }
        } catch (SQLException ignored) {
        }
        return bounties;
    }
}
