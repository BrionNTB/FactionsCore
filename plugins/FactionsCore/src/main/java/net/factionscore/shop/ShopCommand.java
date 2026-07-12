package net.factionscore.shop;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class ShopCommand extends Command {

    private final ShopManager shopManager;
    private final net.factionscore.misc.WarmupManager warmups;

    public ShopCommand(ShopManager shopManager, net.factionscore.misc.WarmupManager warmups) {
        super("shop", "Teleport to the shop", "/shop");
        this.setPermission("factionscore.command.shop");
        this.shopManager = shopManager;
        this.warmups = warmups;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Only players can use /shop.");
            return true;
        }
        if (shopManager.getShopLocation().isEmpty()) {
            player.sendMessage(TextFormat.RED + "The shop location hasn't been set yet. Ask an admin to run /setshop.");
            return true;
        }
        warmups.start(player, "Shop", () -> {
            if (!player.isOnline()) return;
            shopManager.getShopLocation().ifPresent(location -> {
                player.teleport(location);
                player.sendMessage(TextFormat.GREEN + "Teleported to the shop.");
            });
        });
        return true;
    }
}
