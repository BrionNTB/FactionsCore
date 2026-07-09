package net.factionscore.combat;

import org.powernukkitx.Player;
import org.powernukkitx.utils.Config;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks "combat tag" state: a player who has hit, or been hit by, another player recently is
 * tagged for {@code combatlog.tag-duration-seconds} (default 10s). Tagged players can't teleport
 * and are killed for real if they disconnect while tagged -- the standard "no combat logging"
 * rule factions servers rely on to stop players escaping a losing fight by pulling the plug.
 */
public final class CombatTagManager {

    private final Config config;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();

    public CombatTagManager(Config config) {
        this.config = config;
    }

    public void tag(Player player) {
        long durationMs = config.getInt("combatlog.tag-duration-seconds", 10) * 1000L;
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
    }

    public boolean isTagged(Player player) {
        Long until = taggedUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public long remainingSeconds(Player player) {
        Long until = taggedUntil.get(player.getUniqueId());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000);
    }

    public void clear(Player player) {
        taggedUntil.remove(player.getUniqueId());
    }
}
