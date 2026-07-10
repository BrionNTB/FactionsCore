package net.factionscore.crate;

import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.block.BlockBreakEvent;
import org.powernukkitx.event.player.PlayerInteractEvent;
import org.powernukkitx.item.Item;
import org.powernukkitx.utils.TextFormat;

public final class CrateListener implements Listener {

    private final CrateManager crates;

    public CrateListener(CrateManager crates) {
        this.crates = crates;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getBlock();
        if (block == null || !BlockID.CHEST.equals(block.getId())) return;
        Player player = event.getPlayer();

        // Admin binding flow: /crate set <type> then click the chest.
        if (crates.hasPendingSelection(player.getUniqueId())) {
            String action = crates.consumeSelection(player.getUniqueId());
            event.setCancelled(true);
            if (action.equals("remove")) {
                crates.unregister(block.getLevel(), block);
                player.sendMessage(TextFormat.GREEN + "Crate unbound from this chest.");
            } else {
                crates.register(block.getLevel(), block, action);
                player.sendMessage(TextFormat.GREEN + "This chest is now a " + action + " crate.");
            }
            return;
        }

        var crateType = crates.crateTypeAt(block.getLevel(), block);
        if (crateType.isEmpty()) return;

        // Registered crates never open as normal chests.
        event.setCancelled(true);

        Item held = player.getInventory().getItemInMainHand();
        if (!(held instanceof KeyItem key) || !key.getCrateType().equals(crateType.get())) {
            player.sendMessage(TextFormat.RED + "You need a " + crateType.get() + " key to open this crate.");
            return;
        }

        Item remaining = held.clone();
        remaining.setCount(remaining.getCount() - 1);
        player.getInventory().setItemInMainHand(remaining.getCount() <= 0 ? Item.get(BlockID.AIR) : remaining);
        crates.openReward(player, crateType.get());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!BlockID.CHEST.equals(block.getId())) return;
        if (crates.crateTypeAt(block.getLevel(), block).isEmpty()) return;
        if (!event.getPlayer().hasPermission("factionscore.command.crate")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(TextFormat.RED + "You can't break a crate.");
        } else {
            crates.unregister(block.getLevel(), block);
            event.getPlayer().sendMessage(TextFormat.YELLOW + "Crate chest broken; binding removed.");
        }
    }
}
