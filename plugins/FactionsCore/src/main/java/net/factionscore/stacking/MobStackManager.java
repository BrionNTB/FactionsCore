package net.factionscore.stacking;

import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.EntityCreature;
import org.powernukkitx.level.Level;
import org.powernukkitx.math.SimpleAxisAlignedBB;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factions servers grind mobs/mob farms constantly, and spawning one real entity per mob tanks
 * TPS fast. Stacking collapses same-type mobs within a small radius into a single entity with a
 * visible "xN" count; only that one entity actually ticks/pathfinds, and killing it drops loot
 * scaled by the stack size instead of requiring N individual kills.
 * <p>
 * Stack counts are kept in memory only (not persisted across restarts/chunk unload) -- acceptable
 * for a mob/spawner grinder since stacks re-form themselves within seconds of the next spawn wave.
 */
public final class MobStackManager {

    private final Config config;
    private final Map<UUID, Integer> stackCounts = new ConcurrentHashMap<>();

    // Best-effort XP scaling: EntityDeathEvent doesn't carry XP, so the XP orb spawn right after
    // a stack death is multiplied instead, keyed by a short-lived hint.
    private volatile long lastStackDeathTick = -1;
    private volatile int lastStackDeathMultiplier = 1;

    public MobStackManager(Config config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return config.getBoolean("stacking.mobs.enabled", true);
    }

    /** When true (default), only mobs produced by spawners stack; naturally spawned mobs stay vanilla. */
    public boolean isSpawnerOnly() {
        return config.getBoolean("stacking.mobs.spawner-only", true);
    }

    public boolean isExcluded(String identifier) {
        String shortId = identifier.contains(":") ? identifier.substring(identifier.indexOf(':') + 1) : identifier;
        return config.getStringList("stacking.mobs.excluded").contains(shortId);
    }

    /**
     * Looks for an existing stack head of the same type within the configured radius.
     *
     * @return the absorbing stack head, or {@code null} if this entity should become its own new stack head.
     */
    public Entity findMergeTarget(EntityCreature spawned) {
        Level level = spawned.getLevel();
        if (level == null) return null;
        double radius = config.getDouble("stacking.mobs.radius", 4.0);
        var box = new SimpleAxisAlignedBB(
                spawned.x - radius, spawned.y - radius, spawned.z - radius,
                spawned.x + radius, spawned.y + radius, spawned.z + radius);
        for (Entity candidate : level.getNearbyEntitiesSafe(box, spawned)) {
            if (candidate == spawned || candidate.closed) continue;
            if (!candidate.getIdentifier().equals(spawned.getIdentifier())) continue;
            // In spawner-only mode, never absorb into (and thus rename) a naturally spawned mob.
            if (isSpawnerOnly() && !candidate.getNbt().getBoolean("spawner")) continue;
            int current = stackCounts.getOrDefault(candidate.getUniqueId(), 1);
            int max = config.getInt("stacking.mobs.max-stack-size", 64);
            if (current < max) {
                return candidate;
            }
        }
        return null;
    }

    public void merge(Entity head) {
        int newCount = stackCounts.merge(head.getUniqueId(), 2, Integer::sum);
        applyNameTag(head, newCount);
    }

    public void registerNewHead(Entity head) {
        stackCounts.putIfAbsent(head.getUniqueId(), 1);
    }

    public int stackSizeOf(Entity entity) {
        return stackCounts.getOrDefault(entity.getUniqueId(), 1);
    }

    public void forget(Entity entity) {
        stackCounts.remove(entity.getUniqueId());
    }

    private void applyNameTag(Entity entity, int count) {
        if (count <= 1) {
            entity.setNameTagVisible(false);
            return;
        }
        String baseName = entity.getOriginalName() != null ? entity.getOriginalName() : entity.getIdentifier();
        entity.setNameTag(TextFormat.YELLOW + baseName + TextFormat.GRAY + " x" + count);
        entity.setNameTagVisible(true);
    }

    /** Called from the death handler; returns the loot multiplier to apply to drops. */
    public int consumeOnDeath(Entity entity, long currentTick) {
        Integer removed = stackCounts.remove(entity.getUniqueId());
        int count = removed == null ? 1 : removed;
        if (count > 1) {
            lastStackDeathTick = currentTick;
            lastStackDeathMultiplier = count;
        }
        return Math.max(1, count);
    }

    public boolean isRecentStackDeath(long currentTick) {
        return config.getBoolean("stacking.mobs.scale-xp", true) && (currentTick - lastStackDeathTick) <= 2;
    }

    public int consumeXpMultiplier() {
        int multiplier = lastStackDeathMultiplier;
        lastStackDeathTick = -1;
        return multiplier;
    }
}
