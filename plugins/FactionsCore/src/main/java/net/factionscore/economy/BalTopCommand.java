package net.factionscore.economy;

import org.powernukkitx.IPlayer;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class BalTopCommand extends Command {

    private final EconomyManager economy;

    public BalTopCommand(EconomyManager economy) {
        super("baltop", "Richest players leaderboard", "/baltop");
        this.setPermission("factionscore.command.balance");
        this.economy = economy;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        sender.sendMessage(TextFormat.GOLD + "--- Balance leaderboard ---");
        int rank = 1;
        for (EconomyManager.TopEntry entry : economy.top(10)) {
            IPlayer player = Server.getInstance().getOfflinePlayer(entry.player());
            String name = player != null && player.getName() != null ? player.getName() : entry.player().toString().substring(0, 8);
            sender.sendMessage(TextFormat.YELLOW + "#" + rank++ + " " + name + TextFormat.GRAY + " - " + TextFormat.GREEN + EconomyManager.format(entry.balance()));
        }
        return true;
    }
}
