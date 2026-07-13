package net.factionscore.raid;

import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityExplodeEvent;
import org.powernukkitx.level.Level;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.utils.Config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TNT-damageable obsidian: vanilla obsidian is blast-proof, which makes obsidian-walled bases
 * literally unraidable and kills the raiding loop factions is built on. The classic fix (SaicoPvP
 * and every big factions server ran a variant) is that N TNT explosions near an obsidian block
 * break it. Explosions never include obsidian in their block list (it resists), so this scans a
 * small cube around each blast and tracks per-block hit counts in memory -- raids are active
 * sessions, so counters not surviving a restart is acceptable and avoids write-amplifying every
 * explosion into the database.
 */
public final class ObsidianBreakListener implements Listener {

    private final Config config;
    private final Map<String, Integer> hitCounts = new ConcurrentHashMap<>();

    public ObsidianBreakListener(Config config) {
        this.config = config;
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) return;
        if (!config.getBoolean("raid.obsidian-breaker.enabled", true)) return;

        int hitsToBreak = config.getInt("raid.obsidian-breaker.tnt-hits", 5);
        int radius = config.getInt("raid.obsidian-breaker.blast-radius", 4);
        Level level = event.getEntity().getLevel();
        if (level == null) return;
        Vector3 center = event.getPosition();

        // Water-muffled explosions don't wear obsidian: cannon charges detonate inside their
        // water chamber, and without this every shot chewed through the cannon's own walls
        // (5 hits = break -- "first shot fine, second shot the cannon eats itself"). Raiding is
        // unaffected: TNT fired at a base detonates dry.
        Vector3 blockPos = new Vector3(center.getFloorX(), center.getFloorY(), center.getFloorZ());
        Block at0 = level.getBlock(blockPos, 0);
        Block at1 = level.getBlock(blockPos, 1);
        if (at0.getId().contains("water") || at1.getId().contains("water")) {
            return;
        }

        int cx = center.getFloorX();
        int cy = center.getFloorY();
        int cz = center.getFloorZ();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (!level.isYInRange(y)) continue;
                    Block block = level.getBlock(x, y, z);
                    if (!BlockID.OBSIDIAN.equals(block.getId())) continue;

                    String key = level.getName() + ":" + x + ":" + y + ":" + z;
                    int hits = hitCounts.merge(key, 1, Integer::sum);
                    if (hits >= hitsToBreak) {
                        hitCounts.remove(key);
                        level.setBlock(new Vector3(x, y, z), Block.get(BlockID.AIR));
                    }
                }
            }
        }
    }
}
