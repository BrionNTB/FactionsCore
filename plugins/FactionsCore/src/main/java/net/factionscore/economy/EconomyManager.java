package net.factionscore.economy;

import net.factionscore.storage.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The single source of truth for player money (the {@code player_balance} table). /sell deposits
 * into it, /pay moves it around, bounties escrow from it, KOTH/crates can pay out of it.
 */
public final class EconomyManager {

    private final Database database;

    public EconomyManager(Database database) {
        this.database = database;
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

    public double deposit(UUID player, double amount) {
        return setBalance(player, getBalance(player) + amount);
    }

    /** @return true if the player had enough and the amount was taken. */
    public synchronized boolean withdraw(UUID player, double amount) {
        double balance = getBalance(player);
        if (balance < amount) {
            return false;
        }
        setBalance(player, balance - amount);
        return true;
    }

    private double setBalance(UUID player, double balance) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO player_balance (player_uuid, balance) VALUES (?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET balance = excluded.balance
                """)) {
            statement.setString(1, player.toString());
            statement.setDouble(2, balance);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
        return balance;
    }

    public record TopEntry(UUID player, double balance) {
    }

    public List<TopEntry> top(int limit) {
        List<TopEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT player_uuid, balance FROM player_balance ORDER BY balance DESC LIMIT ?")) {
            statement.setInt(1, limit);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                entries.add(new TopEntry(UUID.fromString(rs.getString("player_uuid")), rs.getDouble("balance")));
            }
        } catch (SQLException ignored) {
        }
        return entries;
    }

    public static String format(double amount) {
        return "$" + String.format("%,.2f", amount);
    }
}
