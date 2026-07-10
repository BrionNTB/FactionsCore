package net.factionscore.crate;

import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class CrateCommand extends Command {

    private final CrateManager crates;

    public CrateCommand(CrateManager crates) {
        super("crate", "Crate administration", "/crate <set|remove|give>");
        this.setPermission("factionscore.command.crate");
        this.crates = crates;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "set" -> set(sender, args);
            case "remove" -> remove(sender);
            case "give" -> give(sender, args);
            default -> {
                sender.sendMessage(TextFormat.YELLOW + "/crate set <common|rare|legendary>" + TextFormat.GRAY + " then click a chest");
                sender.sendMessage(TextFormat.YELLOW + "/crate remove" + TextFormat.GRAY + " then click a crate chest");
                sender.sendMessage(TextFormat.YELLOW + "/crate give <player> <type> [count]");
            }
        }
        return true;
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Run this in-game.");
            return;
        }
        if (args.length < 2 || !crates.isKnownType(args[1])) {
            sender.sendMessage(TextFormat.RED + "Usage: /crate set <common|rare|legendary> (must exist under crates: in config.yml)");
            return;
        }
        crates.armSelection(player.getUniqueId(), args[1].toLowerCase());
        player.sendMessage(TextFormat.GREEN + "Now right-click the chest to bind it as a " + args[1] + " crate.");
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Run this in-game.");
            return;
        }
        crates.armSelection(player.getUniqueId(), "remove");
        player.sendMessage(TextFormat.GREEN + "Now right-click the crate chest to unbind it.");
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextFormat.RED + "Usage: /crate give <player> <type> [count]");
            return;
        }
        Player target = Server.getInstance().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(TextFormat.RED + "Player not found.");
            return;
        }
        int count = 1;
        if (args.length >= 4) {
            try {
                count = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                sender.sendMessage(TextFormat.RED + "Invalid count.");
                return;
            }
        }
        var key = crates.createKey(args[2], count);
        if (key.isEmpty()) {
            sender.sendMessage(TextFormat.RED + "Unknown crate type: " + args[2]);
            return;
        }
        target.getInventory().addItem(key.get());
        sender.sendMessage(TextFormat.GREEN + "Gave " + target.getName() + " " + count + "x " + args[2] + " key.");
        target.sendMessage(TextFormat.GOLD + "You received " + count + "x " + args[2] + " crate key!");
    }
}
