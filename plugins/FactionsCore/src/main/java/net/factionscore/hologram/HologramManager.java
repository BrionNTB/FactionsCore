package net.factionscore.hologram;

import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.blockentity.BlockEntityHopper;
import org.powernukkitx.blockentity.BlockEntityMobSpawner;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.Location;
import org.powernukkitx.level.particle.FloatingTextParticle;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.registry.Registries;
import org.powernukkitx.utils.TextFormat;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Floating text over special blocks so players can read them at a glance: tiered hoppers show
 * their tier, spawners show their mob type and stack count. Bedrock has no block-hover tooltip,
 * so this is done with nametag-only invisible armor stands (FloatingTextParticle).
 * <p>
 * A periodic reconcile scans loaded block entities, which both (re)creates holograms after chunk
 * loads/restarts (the tier/type data lives in the block entity, so nothing extra is persisted)
 * and removes ones whose block is gone. Joining players get every current hologram resent.
 */
public final class HologramManager {

    private final Map<String, FloatingTextParticle> holograms = new ConcurrentHashMap<>();

    private String key(Level level, Vector3 pos) {
        return level.getName() + ":" + pos.getFloorX() + ":" + pos.getFloorY() + ":" + pos.getFloorZ();
    }

    public void set(Level level, Vector3 pos, String title) {
        String key = key(level, pos);
        FloatingTextParticle existing = holograms.get(key);
        if (existing != null) {
            existing.setTitle(title);
            return;
        }
        Vector3 center = new Vector3(pos.getFloorX() + 0.5, pos.getFloorY() + 1.15, pos.getFloorZ() + 0.5);
        FloatingTextParticle particle = new FloatingTextParticle(Location.fromObject(center, level), title);
        holograms.put(key, particle);
        level.addParticle(particle);
    }

    public void remove(Level level, Vector3 pos) {
        FloatingTextParticle particle = holograms.remove(key(level, pos));
        if (particle != null) {
            particle.setInvisible(true);
        }
    }

    /** Resend all holograms to one player (used on join, since particles are fire-and-forget per client). */
    public void sendAll(Player player) {
        for (FloatingTextParticle particle : holograms.values()) {
            player.getLevel().addParticle(particle, player);
        }
    }

    /** Runs on the main thread on a timer: creates missing holograms, drops stale ones. */
    public void reconcile() {
        for (Level level : Server.getInstance().getLevels().values()) {
            for (BlockEntity blockEntity : new ArrayList<>(level.getBlockEntities().values())) {
                if (blockEntity == null || blockEntity.closed) continue;
                if (blockEntity instanceof BlockEntityHopper hopper && hopper.getTier() > 1) {
                    set(level, hopper, hopperTitle(hopper.getTier()));
                } else if (blockEntity instanceof BlockEntityMobSpawner spawner && spawner.getSpawnEntityType() > 0) {
                    set(level, spawner, spawnerTitle(spawner));
                }
            }
        }
        // Drop holograms whose backing block entity no longer exists (broken block, etc.).
        holograms.entrySet().removeIf(entry -> {
            String[] parts = entry.getKey().split(":");
            Level level = Server.getInstance().getLevelByName(parts[0]);
            if (level == null) return false;
            Vector3 pos = new Vector3(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            if (!level.isChunkLoaded(pos.getFloorX() >> 4, pos.getFloorZ() >> 4)) {
                return false; // unloaded chunk: keep, we can't tell
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            boolean stillValid = blockEntity instanceof BlockEntityHopper hopper && hopper.getTier() > 1
                    || blockEntity instanceof BlockEntityMobSpawner spawner && spawner.getSpawnEntityType() > 0;
            if (!stillValid) {
                entry.getValue().setInvisible(true);
            }
            return !stillValid;
        });
    }

    public static String hopperTitle(int tier) {
        return tier >= 3
                ? TextFormat.GOLD + "" + TextFormat.BOLD + "Hopper III" + TextFormat.RESET + TextFormat.GRAY + " (3x loot)"
                : TextFormat.YELLOW + "" + TextFormat.BOLD + "Hopper II" + TextFormat.RESET + TextFormat.GRAY + " (1.5x loot)";
    }

    public static String spawnerTitle(BlockEntityMobSpawner spawner) {
        String identifier = Registries.ENTITY.getEntityIdentifier(spawner.getSpawnEntityType());
        String mobName = identifier == null ? "Mob" : pretty(identifier);
        int stack = Math.max(1, spawner.getMinSpawnCount());
        return TextFormat.AQUA + "" + TextFormat.BOLD + mobName + " Spawner"
                + TextFormat.RESET + TextFormat.YELLOW + " x" + stack;
    }

    private static String pretty(String identifier) {
        String path = identifier.substring(identifier.indexOf(':') + 1).replace('_', ' ');
        StringBuilder pretty = new StringBuilder();
        for (String word : path.split(" ")) {
            if (word.isEmpty()) continue;
            pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return pretty.toString().trim();
    }
}
