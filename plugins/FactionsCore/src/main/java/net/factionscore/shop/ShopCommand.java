package net.factionscore.shop;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.utils.TextFormat;

public final class ShopCommand extends Command {

    private final ShopManager shopManager;

    public ShopCommand(ShopManager shopManager) {
        super("shop", "Teleport to the shop", "/shop");
        this.shopManager = shopManager;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Only players can use /shop.");
            return true;
        }
        var location = shopManager.getShopLocation();
        if (location.isEmpty()) {
            player.sendMessage(TextFormat.RED + "The shop location hasn't been set yet. Ask an admin to run /setshop.");
            return true;
        }
        player.teleport(location.get());
        player.sendMessage(TextFormat.GREEN + "Teleported to the shop.");
        return true;
    }
}
