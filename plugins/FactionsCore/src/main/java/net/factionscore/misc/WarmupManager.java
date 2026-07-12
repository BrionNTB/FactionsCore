package net.factionscore.misc;

import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared teleport warmups: /shop, /wild and /spawn all wait out a countdown (shown bold purple in
 * the sidebar) before firing. Getting hit by another player cancels the countdown and the command
 * has to be re-run -- the anti-escape rule that keeps these from being combat exits.
 */
public final class WarmupManager {

    private static final class Warmup {
        final String label;
        final Runnable onComplete;
        int remainingSeconds;

        Warmup(String label, int remainingSeconds, Runnable onComplete) {
            this.label = label;
            this.remainingSeconds = remainingSeconds;
            this.onComplete = onComplete;
        }
    }

    private final Config config;
    private final Map<UUID, Warmup> pending = new ConcurrentHashMap<>();

    public WarmupManager(Config config) {
        this.config = config;
    }

    public int warmupSeconds() {
        return Math.max(0, config.getInt("teleport.warmup-seconds", 10));
    }

    /** @return false if the player already has a countdown running. */
    public boolean start(Player player, String label, Runnable onComplete) {
        int seconds = warmupSeconds();
        if (seconds == 0) {
            onComplete.run();
            return true;
        }
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(TextFormat.RED + "You already have a teleport counting down!");
            return false;
        }
        pending.put(player.getUniqueId(), new Warmup(label, seconds, onComplete));
        player.sendMessage(TextFormat.LIGHT_PURPLE + "" + TextFormat.BOLD + label + " in " + seconds + " secs..."
                + TextFormat.RESET + TextFormat.GRAY + " Getting hit cancels it!");
        return true;
    }

    /** Sidebar line for this player's active countdown, or null if none. */
    public String countdownLine(UUID player) {
        Warmup warmup = pending.get(player);
        if (warmup == null) return null;
        return TextFormat.DARK_PURPLE + "" + TextFormat.BOLD + "⌛ " + warmup.label + ": " + warmup.remainingSeconds + " secs";
    }

    /** @return true if a countdown was cancelled. */
    public boolean cancel(Player player, String reason) {
        Warmup warmup = pending.remove(player.getUniqueId());
        if (warmup == null) return false;
        player.sendMessage(TextFormat.RED + "" + TextFormat.BOLD + warmup.label + " cancelled!" + TextFormat.RESET + TextFormat.RED + " " + reason);
        return true;
    }

    /** Runs once per second on the main thread. */
    public void tick() {
        for (var entry : pending.entrySet()) {
            Player player = Server.getInstance().getOnlinePlayers().get(entry.getKey());
            if (player == null) {
                pending.remove(entry.getKey());
                continue;
            }
            Warmup warmup = entry.getValue();
            warmup.remainingSeconds--;
            if (warmup.remainingSeconds <= 0) {
                pending.remove(entry.getKey());
                try {
                    warmup.onComplete.run();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
