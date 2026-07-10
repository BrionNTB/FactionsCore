package net.factionscore.bounty;

import net.factionscore.economy.EconomyManager;
import org.powernukkitx.IPlayer;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class BountyCommand extends Command {

    private final BountyManager bounties;

    public BountyCommand(BountyManager bounties) {
        super("bounty", "Place or view kill bounties", "/bounty <set|list>");
        this.setPermission("factionscore.command.bounty");
        this.bounties = bounties;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("set")) {
            set(sender, args);
            return true;
        }
        sender.sendMessage(TextFormat.YELLOW + "/bounty set <player> <amount> | /bounty list");
        return true;
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Only players can place bounties.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(TextFormat.RED + "Usage: /bounty set <player> <amount>");
            return;
        }
        IPlayer target = Server.getInstance().getOfflinePlayer(args[1]);
        if (target == null || target.getName() == null) {
            sender.sendMessage(TextFormat.RED + "Unknown player.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(TextFormat.RED + "You can't put a bounty on yourself.");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextFormat.RED + "Invalid amount.");
            return;
        }
        if (amount <= 0 || !Double.isFinite(amount)) {
            sender.sendMessage(TextFormat.RED + "Amount must be positive.");
            return;
        }
        if (!bounties.place(player.getUniqueId(), target.getUniqueId(), target.getName(), amount)) {
            sender.sendMessage(TextFormat.RED + "You don't have " + EconomyManager.format(amount) + ".");
            return;
        }
        Server.getInstance().broadcastMessage(TextFormat.GOLD + player.getName() + " placed a "
                + TextFormat.RED + EconomyManager.format(amount) + TextFormat.GOLD + " bounty on " + target.getName()
                + "! Total pot: " + TextFormat.RED + EconomyManager.format(bounties.amountOn(target.getUniqueId())));
    }

    private void list(CommandSender sender) {
        var all = bounties.all();
        sender.sendMessage(TextFormat.GOLD + "--- Active bounties (" + all.size() + ") ---");
        for (BountyManager.Bounty bounty : all) {
            sender.sendMessage(TextFormat.YELLOW + bounty.targetName() + TextFormat.GRAY + " - " + TextFormat.RED + EconomyManager.format(bounty.amount()));
        }
        if (all.isEmpty()) {
            sender.sendMessage(TextFormat.GRAY + "(none -- place one with /bounty set <player> <amount>)");
        }
    }
}
