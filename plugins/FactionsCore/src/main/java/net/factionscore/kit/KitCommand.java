package net.factionscore.kit;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.item.Item;
import org.powernukkitx.utils.TextFormat;

public final class KitCommand extends Command {

    private final KitManager kitManager;

    public KitCommand(KitManager kitManager) {
        super("kit", "Claim a kit", "/kit [name]", new String[]{"kits"});
        this.setPermission("factionscore.command.kit");
        this.kitManager = kitManager;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Only players can claim kits.");
            return true;
        }
        if (args.length == 0) {
            list(player);
            return true;
        }
        KitManager.Kit kit = kitManager.get(args[0]);
        if (kit == null) {
            player.sendMessage(TextFormat.RED + "No such kit. Use /kit to list them.");
            return true;
        }
        if (!kit.permission().isEmpty() && !player.hasPermission(kit.permission())) {
            player.sendMessage(TextFormat.RED + "You don't have access to that kit.");
            return true;
        }
        long remaining = kitManager.remainingCooldown(player.getUniqueId(), kit);
        if (remaining > 0) {
            player.sendMessage(TextFormat.RED + "You can claim " + kit.name() + " again in " + formatDuration(remaining) + ".");
            return true;
        }
        kitManager.markClaimed(player.getUniqueId(), kit);
        for (Item item : kit.items()) {
            Item[] leftover = player.getInventory().addItem(item.clone());
            for (Item drop : leftover) {
                player.getLevel().dropItem(player, drop);
            }
        }
        player.sendMessage(TextFormat.GREEN + "Claimed kit " + kit.name() + "!");
        return true;
    }

    private void list(Player player) {
        player.sendMessage(TextFormat.GOLD + "--- Kits ---");
        for (KitManager.Kit kit : kitManager.kits().values()) {
            if (!kit.permission().isEmpty() && !player.hasPermission(kit.permission())) {
                continue;
            }
            long remaining = kitManager.remainingCooldown(player.getUniqueId(), kit);
            String status = remaining > 0 ? TextFormat.RED + " (ready in " + formatDuration(remaining) + ")" : TextFormat.GREEN + " (ready)";
            player.sendMessage(TextFormat.YELLOW + "/kit " + kit.name() + status);
        }
    }

    private String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
