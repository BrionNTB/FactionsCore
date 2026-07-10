package net.factionscore.shop;

import net.factionscore.economy.EconomyManager;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.blockentity.BlockEntityChest;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerInteractEvent;
import org.powernukkitx.inventory.Inventory;
import org.powernukkitx.item.Item;
import org.powernukkitx.utils.TextFormat;

public final class SellWandListener implements Listener {

    private final SellManager sellManager;

    public SellWandListener(SellManager sellManager) {
        this.sellManager = sellManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!(player.getInventory().getItemInMainHand() instanceof SellWandItem wand)) return;
        Block block = event.getBlock();
        if (block == null || !BlockID.CHEST.equals(block.getId())) return;

        event.setCancelled(true);

        if (wand.getUses() <= 0) {
            player.sendMessage(TextFormat.RED + "This sell wand is used up.");
            return;
        }
        if (!(block.getLevel().getBlockEntity(block) instanceof BlockEntityChest chest)) {
            return;
        }

        Inventory inventory = chest.getInventory();
        double earned = 0;
        int soldCount = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            Item item = inventory.getItem(slot);
            if (item == null || item.isNull()) continue;
            double unitPrice = sellManager.priceOf(item.getId());
            if (unitPrice <= 0) continue;
            earned += unitPrice * item.getCount();
            soldCount += item.getCount();
            inventory.clear(slot, true);
        }

        if (soldCount == 0) {
            player.sendMessage(TextFormat.RED + "Nothing sellable in that chest.");
            return;
        }

        double balance = sellManager.addBalance(player.getUniqueId(), earned);
        wand.setUses(wand.getUses() - 1);
        player.getInventory().setItemInMainHand(wand);
        player.sendMessage(TextFormat.GREEN + "Sold " + soldCount + " items for " + EconomyManager.format(earned)
                + TextFormat.GRAY + " (balance: " + EconomyManager.format(balance) + ", wand uses left: " + wand.getUses() + ")");
    }
}
