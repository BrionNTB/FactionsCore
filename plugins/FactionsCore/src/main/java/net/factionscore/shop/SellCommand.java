package net.factionscore.shop;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.inventory.fake.FakeInventory;
import org.powernukkitx.inventory.fake.FakeInventoryType;
import org.powernukkitx.item.Item;
import org.powernukkitx.utils.TextFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SellCommand extends Command {

    private final SellManager sellManager;

    public SellCommand(SellManager sellManager) {
        super("sell", "Sell mob/crop loot from your inventory", "/sell");
        this.setPermission("factionscore.command.sell");
        this.sellManager = sellManager;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "Only players can use /sell.");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        List<String> itemIds = new ArrayList<>(sellManager.basePrices().keySet());
        int size = Math.max(9, ((itemIds.size() + 8) / 9) * 9);
        FakeInventory inventory = new FakeInventory(FakeInventoryType.CHEST, TextFormat.DARK_GREEN + "Sell Shop", size);

        for (int i = 0; i < itemIds.size(); i++) {
            String itemId = itemIds.get(i);
            inventory.setItem(i, icon(itemId));
            inventory.setItemHandler(i, (fakeInventory, slot, oldItem, newItem, event) -> {
                event.setCancelled(true);
                sellAll(player, itemId);
                fakeInventory.setItem(slot, icon(itemId));
            });
        }

        player.addWindow(inventory);
        player.sendMessage(TextFormat.YELLOW + "Click an item to sell every one of that item you're carrying.");
    }

    private Item icon(String itemId) {
        Item item = Item.get(itemId);
        item.setCount(1);
        double price = sellManager.priceOf(itemId);
        double multiplier = sellManager.multiplierOf(itemId);
        String trend = multiplier >= 1.05 ? TextFormat.GREEN + "up" : multiplier <= 0.95 ? TextFormat.RED + "down" : TextFormat.GRAY + "steady";
        item.setLore(
                TextFormat.GRAY + "$" + String.format("%.2f", price) + " each",
                TextFormat.GRAY + "Demand: " + trend
        );
        return item;
    }

    private void sellAll(Player player, String itemId) {
        double unitPrice = sellManager.priceOf(itemId);
        int total = 0;
        var contents = player.getInventory().getContents();
        for (var entry : contents.entrySet()) {
            Item stack = entry.getValue();
            if (stack != null && !stack.isNull() && stack.getId().equals(itemId)) {
                total += stack.getCount();
                player.getInventory().clear(entry.getKey(), true);
            }
        }

        if (total == 0) {
            player.sendMessage(TextFormat.RED + "You don't have any of that to sell.");
            return;
        }

        double earned = total * unitPrice;
        double newBalance = sellManager.addBalance(player.getUniqueId(), earned);
        player.sendMessage(TextFormat.GREEN + "Sold " + total + "x for $" + String.format("%.2f", earned)
                + TextFormat.GRAY + " (balance: $" + String.format("%.2f", newBalance) + ")");
    }
}
