package net.factionscore.stacking;

import org.powernukkitx.blockentity.BlockEntityMobSpawner;
import org.powernukkitx.utils.Config;

/**
 * "Stacking" a spawner scales how many mobs it produces per cycle and how many of its mobs are
 * allowed to be alive nearby at once. The engine's spawn-count fields had no public setter before
 * this project patched one in (see engine/FORK_NOTES.md), since PowerNukkitX only ever needed to
 * read them from NBT.
 */
public final class SpawnerStackManager {

    private final Config config;

    public SpawnerStackManager(Config config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return config.getBoolean("stacking.spawners.enabled", true);
    }

    public int maxStackSize() {
        return config.getInt("stacking.spawners.max-stack-size", 15);
    }

    public int stackLevelOf(BlockEntityMobSpawner spawner) {
        return spawner.getMinSpawnCount();
    }

    /** @return the new stack level, or -1 if already at the configured max. */
    public int increment(BlockEntityMobSpawner spawner) {
        int max = maxStackSize();
        int current = spawner.getMinSpawnCount();
        if (current >= max) {
            return -1;
        }
        int next = current + 1;
        boolean scalePerSpawn = config.getBoolean("stacking.spawners.mobs-per-spawn-scaling", true);
        spawner.setSpawnCount(next, scalePerSpawn ? next : spawner.getMaxSpawnCount());
        spawner.setMaxNearbyEntities(6 * next);
        return next;
    }
}
