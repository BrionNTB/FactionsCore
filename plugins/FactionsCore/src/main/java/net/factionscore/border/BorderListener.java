package net.factionscore.border;

import net.factionscore.combat.CombatTagManager;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.block.BlockExplosionPrimeEvent;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;
import org.powernukkitx.event.entity.EntityExplosionPrimeEvent;
import org.powernukkitx.event.player.PlayerMoveEvent;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.Location;
import org.powernukkitx.level.Position;
import org.powernukkitx.level.particle.BlockForceFieldParticle;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BorderListener implements Listener {

    private final WorldBorderManager border;
    private final CombatTagManager combatTags;
    private final Config config;
    private final Map<UUID, Long> lastWarnedAt = new ConcurrentHashMap<>();

    public BorderListener(WorldBorderManager border, CombatTagManager combatTags, Config config) {
        this.border = border;
        this.combatTags = combatTags;
        this.config = config;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!border.isEnabled()) return;
        Player player = event.getPlayer();
        if (player.hasPermission("factionscore.bypass.border")) return;

        Level level = player.getLevel();
        double x = event.getTo().x;
        double z = event.getTo().z;

        if (border.isBeyondBorder(level, x, z)) {
            event.setCancelled(true);
            Position clamped = new Position(border.clampedX(level, x), event.getTo().y, border.clampedZ(level, z), level);
            // Passing a null cause skips PlayerTeleportEvent entirely -- this is a server-side
            // position correction, not a gameplay teleport, so it must not be blockable by the
            // combat-log "no teleporting while tagged" rule (CombatLogListener).
            player.teleport(Location.fromObject(clamped, level, player.yaw, player.pitch), null);
            // Render before warn(): both share lastWarnedAt as a throttle timestamp, and warn()
            // stamps "now" -- calling it first would make renderWall's own throttle check think a
            // wall was just drawn a moment ago and skip the one we actually want to show.
            renderWall(player, level, clamped.x, clamped.z);
            warn(player, "You've hit the world border!");
            return;
        }

        double warningDistance = config.getDouble("border.warning-distance", 50);
        if (border.distancePastBorder(level, x, z) > -warningDistance) {
            renderWall(player, level, x, z);
        }
    }

    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = lastWarnedAt.get(player.getUniqueId());
        if (last != null && now - last < 1500) return;
        lastWarnedAt.put(player.getUniqueId(), now);
        player.sendPopup(TextFormat.RED + message);
    }

    private void renderWall(Player player, Level level, double x, double z) {
        if (!config.getBoolean("border.wall.enabled", true)) return;
        long now = System.currentTimeMillis();
        Long last = lastWarnedAt.get(player.getUniqueId());
        // Reuse the warn-throttle window so the particle patch redraws at the same modest cadence
        // instead of every single move tick.
        if (last != null && now - last < 1000) return;

        double radius = border.radiusFor(level);
        boolean nearX = Math.abs(Math.abs(x) - radius) < config.getDouble("border.warning-distance", 50);
        boolean nearZ = Math.abs(Math.abs(z) - radius) < config.getDouble("border.warning-distance", 50);
        int patchRadius = config.getInt("border.wall.patch-radius", 8);
        int spacing = Math.max(1, config.getInt("border.wall.spacing", 2));

        if (nearX) {
            double wallX = Math.copySign(radius, x);
            for (int dz = -patchRadius; dz <= patchRadius; dz += spacing) {
                for (int dy = -2; dy <= 8; dy += spacing) {
                    level.addParticle(new BlockForceFieldParticle(new Vector3(wallX, player.y + dy, z + dz)), player);
                }
            }
        }
        if (nearZ) {
            double wallZ = Math.copySign(radius, z);
            for (int dx = -patchRadius; dx <= patchRadius; dx += spacing) {
                for (int dy = -2; dy <= 8; dy += spacing) {
                    level.addParticle(new BlockForceFieldParticle(new Vector3(x + dx, player.y + dy, wallZ)), player);
                }
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplosionPrimeEvent event) {
        if (!border.isEnabled()) return;
        Entity entity = event.getEntity();
        Level level = entity.getLevel();
        if (level != null && border.isBeyondBorder(level, entity.x, entity.z)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplosionPrimeEvent event) {
        if (!border.isEnabled()) return;
        Level level = event.getBlock().getLevel();
        if (level != null && border.isBeyondBorder(level, event.getBlock().x, event.getBlock().z)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!border.isEnabled()) return;
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player victim)) return;
        Level level = victim.getLevel();
        if (level != null && border.isBeyondBorder(level, victim.x, victim.z)) {
            event.setCancelled(true);
        }
    }

    /** Catches players who ended up beyond the border by means other than walking (teleports, vehicles, plugins). */
    public void sweepAll() {
        if (!border.isEnabled()) return;
        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            if (player.hasPermission("factionscore.bypass.border")) continue;
            Level level = player.getLevel();
            if (level == null || !border.isBeyondBorder(level, player.x, player.z)) continue;
            if (combatTags.isTagged(player)) {
                // Combat-tagged players can't be teleported (see CombatLogListener); the move-event
                // clamp already stops them advancing further, so just leave them at the wall.
                continue;
            }
            player.teleport(level.getSpawnLocation());
            player.sendMessage(TextFormat.RED + "You were teleported back to spawn for being past the world border.");
        }
    }
}
