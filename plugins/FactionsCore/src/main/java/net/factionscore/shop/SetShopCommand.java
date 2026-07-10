package net.factionscore.shop;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.level.Location;
import org.powernukkitx.utils.TextFormat;

public final class SetShopCommand extends Command {

    private final ShopManager shopManager;

    public SetShopCommand(ShopManager shopManager) {
        super("setshop", "Set the shop location to your current position", "/setshop");
        this.setPermission("factionscore.command.setshop");
        this.shopManager = shopManager;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Only players can use /setshop.");
            return true;
        }
        shopManager.setShopLocation(Location.fromObject(player, player.getLevel(), player.yaw, player.pitch));
        player.sendMessage(TextFormat.GREEN + "Shop location set to your current position.");
        return true;
    }
}
