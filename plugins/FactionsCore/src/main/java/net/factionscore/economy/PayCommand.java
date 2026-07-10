package net.factionscore.economy;

import org.powernukkitx.IPlayer;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class PayCommand extends Command {

    private final EconomyManager economy;

    public PayCommand(EconomyManager economy) {
        super("pay", "Send money to another player", "/pay <player> <amount>");
        this.setPermission("factionscore.command.balance");
        this.economy = economy;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Only players can use /pay.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Usage: /pay <player> <amount>");
            return true;
        }
        IPlayer target = Server.getInstance().getOfflinePlayer(args[0]);
        if (target == null || target.getName() == null) {
            sender.sendMessage(TextFormat.RED + "Unknown player.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(TextFormat.RED + "You can't pay yourself.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextFormat.RED + "Invalid amount.");
            return true;
        }
        if (amount <= 0 || !Double.isFinite(amount)) {
            sender.sendMessage(TextFormat.RED + "Amount must be positive.");
            return true;
        }
        if (!economy.withdraw(player.getUniqueId(), amount)) {
            sender.sendMessage(TextFormat.RED + "You don't have that much.");
            return true;
        }
        economy.deposit(target.getUniqueId(), amount);
        player.sendMessage(TextFormat.GREEN + "Paid " + target.getName() + " " + EconomyManager.format(amount) + ".");
        Player online = Server.getInstance().getPlayerExact(target.getName());
        if (online != null) {
            online.sendMessage(TextFormat.GREEN + player.getName() + " paid you " + EconomyManager.format(amount) + ".");
        }
        return true;
    }
}
