package net.factionscore.anticheat.check;

import net.factionscore.anticheat.PlayerCheckData;
import org.powernukkitx.block.Block;
import org.powernukkitx.math.BlockFace;
import org.powernukkitx.utils.Config;

import java.util.List;

/**
 * X-ray clients let a player see through stone to valuable ore, so they mine it "blind" --
 * straight to the ore block without ever having exposed a neighboring air face by tunneling
 * naturally. A single blind ore break happens sometimes by luck; a cluster of them in a short
 * window is the signature this check is built to catch.
 */
public final class XrayCheck {

    private final Config config;

    public XrayCheck(Config config) {
        this.config = config;
    }

    public boolean isTrackedOre(Block block) {
        List<String> tracked = config.getStringList("anticheat.xray.track-ores");
        return tracked.contains(shortId(block));
    }

    private String shortId(Block block) {
        String id = block.getId();
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** A break is "blind" if none of the 6 neighbors were already air before this block broke. */
    public boolean isBlindBreak(Block block) {
        for (BlockFace face : BlockFace.values()) {
            if (block.getSide(face).isAir()) {
                return false;
            }
        }
        return true;
    }

    public Result evaluate(PlayerCheckData data, Block block, long now) {
        if (!config.getBoolean("anticheat.xray.enabled", true)) {
            return Result.CLEAN;
        }
        if (!isTrackedOre(block)) {
            return Result.CLEAN;
        }
        if (!isBlindBreak(block)) {
            return Result.CLEAN;
        }
        data.recordSuspiciousOreBreak(now);

        long windowMs = config.getInt("anticheat.xray.suspicion-window-minutes", 30) * 60_000L;
        long cutoff = now - windowMs;
        long recent = data.suspiciousOreBreaks.stream().filter(t -> t >= cutoff).count();

        int threshold = config.getInt("anticheat.xray.flag-threshold", 5);
        if (recent >= threshold) {
            return new Result(true, "%d blind ore breaks in the last %d min".formatted(recent, windowMs / 60_000));
        }
        return Result.CLEAN;
    }

    public record Result(boolean flagged, String detail) {
        static final Result CLEAN = new Result(false, null);
    }
}
