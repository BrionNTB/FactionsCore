package net.factionscore.anticheat.check;

import net.factionscore.anticheat.PlayerCheckData;
import org.powernukkitx.utils.Config;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Autoclickers/click macros land in one of two buckets that legitimate humans rarely produce
 * over a long sample: implausibly high sustained CPS, or CPS that's high AND suspiciously
 * *regular* (near-zero variance in the gap between clicks -- a human's clicking rhythm always has
 * jitter, a macro's often doesn't).
 */
public final class AutoClickerCheck {

    private final Config config;

    public AutoClickerCheck(Config config) {
        this.config = config;
    }

    public Result evaluate(PlayerCheckData data) {
        if (!config.getBoolean("anticheat.autoclicker.enabled", true)) {
            return Result.CLEAN;
        }
        int sampleSize = config.getInt("anticheat.autoclicker.sample-size", 20);
        List<Long> clicks = new ArrayList<>(data.clickTimestamps);
        if (clicks.size() < sampleSize) {
            return Result.CLEAN;
        }

        long windowMs = clicks.get(clicks.size() - 1) - clicks.get(0);
        if (windowMs <= 0) {
            return Result.CLEAN;
        }
        double cps = (clicks.size() - 1) * 1000.0 / windowMs;

        double maxCps = config.getDouble("anticheat.autoclicker.max-cps", 20);
        double flagCps = config.getDouble("anticheat.autoclicker.flag-cps", 14);
        double maxVarianceBelow = config.getDouble("anticheat.autoclicker.max-variance-below", 4.5);

        if (cps < flagCps) {
            return Result.CLEAN;
        }

        double meanGap = windowMs / (double) (clicks.size() - 1);
        double variance = 0;
        Iterator<Long> it = clicks.iterator();
        long prev = it.next();
        int count = 0;
        while (it.hasNext()) {
            long next = it.next();
            double gap = next - prev;
            variance += Math.pow(gap - meanGap, 2);
            prev = next;
            count++;
        }
        double stddev = Math.sqrt(variance / count);

        if (cps >= maxCps) {
            return new Result(true, "cps=%.1f (hard cap %.1f)".formatted(cps, maxCps));
        }
        if (stddev <= maxVarianceBelow) {
            return new Result(true, "cps=%.1f stddev=%.2fms (too regular for a human)".formatted(cps, stddev));
        }
        return Result.CLEAN;
    }

    public record Result(boolean flagged, String detail) {
        static final Result CLEAN = new Result(false, null);
    }
}
