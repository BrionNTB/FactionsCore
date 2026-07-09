package net.factionscore.enchant;

import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.item.Item;
import org.powernukkitx.item.enchantment.Enchantment;
import org.powernukkitx.utils.TextFormat;

public final class EnchantCommand extends Command {

    private final CustomEnchantRegistry registry;

    public EnchantCommand(CustomEnchantRegistry registry) {
        super("enchant", "Apply/inspect custom enchants", "/enchant help");
        this.registry = registry;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> list(sender);
            case "give" -> give(sender, args);
            case "info" -> info(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextFormat.YELLOW + "/enchant list");
        sender.sendMessage(TextFormat.YELLOW + "/enchant give <player> <enchant> <level>");
        sender.sendMessage(TextFormat.YELLOW + "/enchant info");
    }

    private void list(CommandSender sender) {
        sender.sendMessage(TextFormat.GOLD + "--- Custom enchants ---");
        for (var entry : registry.all().entrySet()) {
            sender.sendMessage(TextFormat.YELLOW + entry.getKey() + TextFormat.GRAY + " (max level " + entry.getValue().getMaxLevel() + ")");
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(TextFormat.RED + "Usage: /enchant give <player> <enchant> <level>");
            return;
        }
        Player target = Server.getInstance().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(TextFormat.RED + "Player not found.");
            return;
        }
        var enchant = registry.get(args[2]);
        if (enchant.isEmpty()) {
            sender.sendMessage(TextFormat.RED + "Unknown enchant: " + args[2]);
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextFormat.RED + "Invalid level.");
            return;
        }
        Item item = target.getInventory().getItemInMainHand();
        if (item.isNull()) {
            sender.sendMessage(TextFormat.RED + "That player isn't holding an item.");
            return;
        }
        Enchantment instance = enchant.get();
        instance.setLevel(level, false);
        item.addEnchantment(instance);
        target.getInventory().setItemInMainHand(item);
        sender.sendMessage(TextFormat.GREEN + "Applied " + args[2] + " " + level + " to " + target.getName() + "'s held item.");
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Only players can inspect their held item.");
            return;
        }
        Item item = player.getInventory().getItemInMainHand();
        sender.sendMessage(TextFormat.GOLD + "--- Custom enchants on held item ---");
        boolean any = false;
        for (var entry : registry.all().entrySet()) {
            int level = item.getCustomEnchantmentLevel(registry.identifierOf(entry.getKey()));
            if (level > 0) {
                any = true;
                sender.sendMessage(TextFormat.YELLOW + entry.getKey() + " " + level);
            }
        }
        if (!any) {
            sender.sendMessage(TextFormat.GRAY + "(none)");
        }
    }
}
