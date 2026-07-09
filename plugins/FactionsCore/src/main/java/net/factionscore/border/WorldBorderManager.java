package net.factionscore.border;

import org.powernukkitx.level.Level;
import org.powernukkitx.level.Position;
import org.powernukkitx.utils.Config;

public final class WorldBorderManager {

    private final Config config;

    public WorldBorderManager(Config config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return config.getBoolean("border.enabled", true);
    }

    public double radiusFor(Level level) {
        return switch (level.getDimension()) {
            case Level.DIMENSION_NETHER -> config.getDouble("border.radius.nether", 60000);
            case Level.DIMENSION_THE_END -> config.getDouble("border.radius.end", 60000);
            default -> config.getDouble("border.radius.overworld", 60000);
        };
    }

    public boolean isBeyondBorder(Level level, double x, double z) {
        double radius = radiusFor(level);
        return Math.abs(x) > radius || Math.abs(z) > radius;
    }

    /** Positive means past the border by that many blocks (on whichever axis is furthest out); non-positive means inside. */
    public double distancePastBorder(Level level, double x, double z) {
        double radius = radiusFor(level);
        return Math.max(Math.abs(x) - radius, Math.abs(z) - radius);
    }

    public double clampedX(Level level, double x) {
        double radius = radiusFor(level);
        return Math.max(-radius + 1, Math.min(radius - 1, x));
    }

    public double clampedZ(Level level, double z) {
        double radius = radiusFor(level);
        return Math.max(-radius + 1, Math.min(radius - 1, z));
    }

    public Position clamp(Position pos) {
        Level level = pos.getLevel();
        return new Position(clampedX(level, pos.x), pos.y, clampedZ(level, pos.z), level);
    }
}
