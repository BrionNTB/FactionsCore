package net.factionscore.anticheat;

import net.factionscore.storage.Database;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class AntiCheatCommand extends Command {

    private final Database database;

    public AntiCheatCommand(Database database) {
        super("anticheat", "Anti-cheat inspection and management", "/anticheat help", new String[]{"ac"});
        this.setPermission("factionscore.command.anticheat");
        this.database = database;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("history")) {
            sender.sendMessage(TextFormat.YELLOW + "/anticheat history <player>");
            return true;
        }
        var target = Server.getInstance().getOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(TextFormat.RED + "Unknown player.");
            return true;
        }
        String uuid = target.getUniqueId().toString();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT check_name, flagged_at, detail FROM anticheat_flags WHERE player_uuid = ? ORDER BY flagged_at DESC LIMIT 20")) {
            statement.setString(1, uuid);
            ResultSet rs = statement.executeQuery();
            sender.sendMessage(TextFormat.GOLD + "--- Recent flags for " + args[1] + " ---");
            boolean any = false;
            while (rs.next()) {
                any = true;
                sender.sendMessage(TextFormat.YELLOW + format.format(new Date(rs.getLong("flagged_at")))
                        + TextFormat.GRAY + " " + rs.getString("check_name") + ": " + rs.getString("detail"));
            }
            if (!any) {
                sender.sendMessage(TextFormat.GRAY + "(no flags)");
            }
        } catch (SQLException e) {
            sender.sendMessage(TextFormat.RED + "Storage error: " + e.getMessage());
        }
        return true;
    }
}
