package net.factionscore.stacking;

import org.powernukkitx.Server;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.blockentity.BlockEntityMobSpawner;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.block.BlockPlaceEvent;
import org.powernukkitx.item.Item;
import org.powernukkitx.level.Level;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.plugin.Plugin;
import org.powernukkitx.registry.Registries;

/** Types the spawner block entity when a pre-typed spawner item (see {@link SpawnerCommand}) is placed. */
public final class SpawnerItemListener implements Listener {

    private final Plugin plugin;

    public SpawnerItemListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        var block = event.getBlockReplace();
        if (!BlockID.MOB_SPAWNER.equals(block.getId())) return;
        Item item = event.getItem();
        if (item == null || !item.hasNbt() || !item.getNbt().contains(SpawnerCommand.MOB_TAG)) return;

        String mobId = item.getNbt().getString(SpawnerCommand.MOB_TAG);
        int networkId = Registries.ENTITY.getEntityNetworkId(mobId);
        if (networkId <= 0) return;

        Level level = block.getLevel();
        Vector3 pos = new Vector3(block.getFloorX(), block.getFloorY(), block.getFloorZ());
        // The block entity is created after this event resolves, so type it one tick later.
        Server.getInstance().getScheduler().scheduleDelayedTask(plugin, () -> {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BlockEntityMobSpawner spawner) {
                spawner.setSpawnEntityType(networkId);
            }
        }, 1);
    }
}
