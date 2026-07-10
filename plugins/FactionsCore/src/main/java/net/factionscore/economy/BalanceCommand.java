package net.factionscore.economy;

import org.powernukkitx.IPlayer;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class BalanceCommand extends Command {

    private final EconomyManager economy;

    public BalanceCommand(EconomyManager economy) {
        super("balance", "Check your (or another player's) balance", "/balance [player]", new String[]{"bal", "money"});
        this.setPermission("factionscore.command.balance");
        this.economy = economy;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length >= 1) {
            IPlayer target = Server.getInstance().getOfflinePlayer(args[0]);
            if (target == null || target.getName() == null) {
                sender.sendMessage(TextFormat.RED + "Unknown player.");
                return true;
            }
            sender.sendMessage(TextFormat.GOLD + target.getName() + ": " + TextFormat.GREEN + EconomyManager.format(economy.getBalance(target.getUniqueId())));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Usage: /balance <player>");
            return true;
        }
        player.sendMessage(TextFormat.GOLD + "Balance: " + TextFormat.GREEN + EconomyManager.format(economy.getBalance(player.getUniqueId())));
        return true;
    }
}
