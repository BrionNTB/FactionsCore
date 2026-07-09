package net.factionscore.anticheat.check;

import net.factionscore.anticheat.PlayerCheckData;
import org.powernukkitx.utils.Config;

import java.util.List;

/**
 * Aimbots/killaura tend to fail one of two ways a real player's aim doesn't: they occasionally
 * snap the camera by an implausible amount in a single tick to (re)acquire a target, or their
 * tracking is *too* smooth -- near-zero variance in the yaw/pitch deltas while actively fighting,
 * where a human's hand always has some tremor/overcorrection.
 */
public final class AimCheck {

    private final Config config;

    public AimCheck(Config config) {
        this.config = config;
    }

    public Result evaluate(PlayerCheckData data) {
        if (!config.getBoolean("anticheat.aim.enabled", true)) {
            return Result.CLEAN;
        }
        int sampleSize = config.getInt("anticheat.aim.sample-size", 40);
        List<double[]> deltas = List.copyOf(data.aimDeltas);
        if (deltas.size() < sampleSize) {
            return Result.CLEAN;
        }

        double maxSnap = config.getDouble("anticheat.aim.max-snap-angle-degrees", 60);
        double minJitter = config.getDouble("anticheat.aim.min-humanlike-jitter", 0.02);

        double sumYaw = 0;
        int snaps = 0;
        for (double[] delta : deltas) {
            double magnitude = Math.hypot(delta[0], delta[1]);
            if (magnitude > maxSnap) {
                snaps++;
            }
            sumYaw += delta[0];
        }
        if (snaps > 0) {
            return new Result(true, "%d implausible snap-angle ticks in the last %d samples".formatted(snaps, deltas.size()));
        }

        double mean = sumYaw / deltas.size();
        double variance = 0;
        for (double[] delta : deltas) {
            variance += Math.pow(delta[0] - mean, 2);
        }
        variance /= deltas.size();

        if (variance < minJitter) {
            return new Result(true, "yaw variance=%.4f while tracking a target (too smooth)".formatted(variance));
        }
        return Result.CLEAN;
    }

    public record Result(boolean flagged, String detail) {
        static final Result CLEAN = new Result(false, null);
    }
}
