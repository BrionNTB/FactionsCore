package net.factionscore.faction.listener;

import net.factionscore.faction.FactionManager;
import net.factionscore.faction.FactionValueManager;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.block.BlockBreakEvent;
import org.powernukkitx.event.block.BlockPlaceEvent;

/** Keeps a faction's tracked spawner value in sync as spawners are placed/broken in their claims. */
public final class SpawnerValueListener implements Listener {

    private final FactionManager factions;
    private final FactionValueManager values;

    public SpawnerValueListener(FactionManager factions, FactionValueManager values) {
        this.factions = factions;
        this.values = values;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        var block = event.getBlockReplace();
        if (!BlockID.MOB_SPAWNER.equals(block.getId())) return;
        factions.getClaimOwner(block.getLocation()).ifPresent(owner ->
                values.onSpawnerPlaced(block.getLevel(), block, owner));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        if (!BlockID.MOB_SPAWNER.equals(block.getId())) return;
        values.onSpawnerBroken(block.getLevel(), block);
    }
}
