package net.factionscore.anticheat;

import net.factionscore.storage.Database;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.event.player.PlayerKickEvent;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

public final class PunishmentManager {

    private final Database database;
    private final Config config;

    public PunishmentManager(Database database, Config config) {
        this.database = database;
        this.config = config;
    }

    public void recordFlag(Player player, String checkName, String detail) {
        UUID uuid = player.getUniqueId();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO anticheat_flags (player_uuid, check_name, flagged_at, detail) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, checkName);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, detail);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }

        int total = countFlags(uuid);
        notifyStaff(player, checkName, detail, total);
        escalate(player, total);
    }

    private int countFlags(UUID uuid) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT COUNT(*) FROM anticheat_flags WHERE player_uuid = ?")) {
            statement.setString(1, uuid.toString());
            var rs = statement.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private void notifyStaff(Player player, String checkName, String detail, int total) {
        if (!config.getBoolean("anticheat.xray.notify-staff", true)) return;
        String message = TextFormat.RED + "[AC] " + player.getName() + " flagged by " + checkName
                + TextFormat.GRAY + " (" + detail + ") " + TextFormat.YELLOW + "[" + total + " total]";
        for (Player online : Server.getInstance().getOnlinePlayers().values()) {
            if (online.hasPermission("factionscore.command.anticheat")) {
                online.sendMessage(message);
            }
        }
    }

    private void escalate(Player player, int totalFlags) {
        if (player.hasPermission("factionscore.bypass.anticheat")) {
            return;
        }
        int kickAt = config.getInt("anticheat.punishment.flags-to-kick", 8);
        int banAt = config.getInt("anticheat.punishment.flags-to-ban", 15);

        if (totalFlags >= banAt) {
            int hours = config.getInt("anticheat.punishment.ban-duration-hours", 72);
            Date expiry = new Date(System.currentTimeMillis() + hours * 3_600_000L);
            Server.getInstance().getNameBans().addBan(player.getName(), "Anti-cheat: repeated flags (" + totalFlags + ")", expiry, "FactionsCore");
            player.kick(PlayerKickEvent.Reason.NAME_BANNED, "Banned by FactionsCore anti-cheat.");
        } else if (totalFlags >= kickAt) {
            player.kick(PlayerKickEvent.Reason.KICKED_BY_ADMIN, "Kicked by FactionsCore anti-cheat, repeated suspicious activity.");
        }
    }
}
