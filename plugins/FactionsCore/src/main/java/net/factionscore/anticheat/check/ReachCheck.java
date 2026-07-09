package net.factionscore.anticheat.check;

import org.powernukkitx.entity.Entity;
import org.powernukkitx.math.AxisAlignedBB;
import org.powernukkitx.utils.Config;

/**
 * Vanilla melee reach tops out a little above 3 blocks (3.0 in survival, ~3.0-3.25 accounting for
 * latency/lag-compensation slack); a killaura or reach client swings from further away than a
 * clenched fist ever could. Distance is measured attacker-eye to the nearest point on the
 * victim's hitbox rather than center-to-center, since center distance overcounts by the victim's
 * own hitbox radius and produces false positives on wide mobs/players at the edge of legit reach.
 */
public final class ReachCheck {

    private final Config config;

    public ReachCheck(Config config) {
        this.config = config;
    }

    public Result evaluate(Entity attacker, Entity victim) {
        if (!config.getBoolean("anticheat.reach.enabled", true)) {
            return Result.CLEAN;
        }
        double maxReach = config.getDouble("anticheat.reach.max-blocks", 3.25);

        double eyeX = attacker.x;
        double eyeY = attacker.y + attacker.getEyeHeight();
        double eyeZ = attacker.z;

        AxisAlignedBB box = victim.getBoundingBox();
        double closestX = clamp(eyeX, box.getMinX(), box.getMaxX());
        double closestY = clamp(eyeY, box.getMinY(), box.getMaxY());
        double closestZ = clamp(eyeZ, box.getMinZ(), box.getMaxZ());

        double dx = eyeX - closestX;
        double dy = eyeY - closestY;
        double dz = eyeZ - closestZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance > maxReach) {
            return new Result(true, "reach=%.2f blocks (max %.2f)".formatted(distance, maxReach));
        }
        return Result.CLEAN;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Result(boolean flagged, String detail) {
        static final Result CLEAN = new Result(false, null);
    }
}
