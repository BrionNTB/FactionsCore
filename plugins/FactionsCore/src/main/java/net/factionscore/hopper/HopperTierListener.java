package net.factionscore.hopper;

import org.powernukkitx.block.BlockID;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.blockentity.BlockEntityHopper;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.block.BlockBreakEvent;
import org.powernukkitx.event.block.BlockPlaceEvent;
import org.powernukkitx.item.Item;

public final class HopperTierListener implements Listener {

    private final net.factionscore.hologram.HologramManager holograms;

    public HopperTierListener(net.factionscore.hologram.HologramManager holograms) {
        this.holograms = holograms;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        var block = event.getBlock();
        if (!BlockID.HOPPER.equals(block.getId())) {
            return;
        }
        Item usedItem = event.getItem();
        int tier = tierOf(usedItem);
        if (tier <= 1) {
            return;
        }
        BlockEntity blockEntity = block.getLevel().getBlockEntity(block);
        if (blockEntity instanceof BlockEntityHopper hopper) {
            hopper.setTier(tier);
            holograms.set(block.getLevel(), block, net.factionscore.hologram.HologramManager.hopperTitle(tier));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        if (!BlockID.HOPPER.equals(block.getId())) {
            return;
        }
        BlockEntity blockEntity = block.getLevel().getBlockEntity(block);
        if (!(blockEntity instanceof BlockEntityHopper hopper) || hopper.getTier() <= 1) {
            return;
        }
        event.setDrops(new Item[]{itemForTier(hopper.getTier())});
        holograms.remove(block.getLevel(), block);
    }

    private int tierOf(Item item) {
        if (item == null) return 1;
        if (ItemHopperTier3.ID.equals(item.getId())) return 3;
        if (ItemHopperTier2.ID.equals(item.getId())) return 2;
        return 1;
    }

    private Item itemForTier(int tier) {
        return switch (tier) {
            case 3 -> new ItemHopperTier3();
            case 2 -> new ItemHopperTier2();
            default -> Item.get(BlockID.HOPPER);
        };
    }
}
