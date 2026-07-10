package net.factionscore.shop;

import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class SellWandCommand extends Command {

    public SellWandCommand() {
        super("sellwand", "Give a sell wand", "/sellwand <player> [uses]");
        this.setPermission("factionscore.command.sellwand");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(TextFormat.RED + "Usage: /sellwand <player> [uses]");
            return true;
        }
        Player target = Server.getInstance().getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(TextFormat.RED + "Player not found.");
            return true;
        }
        int uses = 50;
        if (args.length >= 2) {
            try {
                uses = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                sender.sendMessage(TextFormat.RED + "Invalid uses.");
                return true;
            }
        }
        SellWandItem wand = new SellWandItem();
        wand.setUses(uses);
        target.getInventory().addItem(wand);
        sender.sendMessage(TextFormat.GREEN + "Gave " + target.getName() + " a sell wand with " + uses + " uses.");
        return true;
    }
}
