package net.factionscore.anticheat;

import org.powernukkitx.Player;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.block.BlockBreakEvent;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;
import org.powernukkitx.event.player.PlayerAnimationEvent;
import org.powernukkitx.event.player.PlayerMoveEvent;
import org.powernukkitx.event.player.PlayerQuitEvent;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;

public final class AntiCheatListener implements Listener {

    private static final long COMBAT_GRACE_MS = 4000;

    private final AntiCheatManager manager;

    public AntiCheatListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == AnimatePacket.Action.SWING) {
            manager.onSwing(event.getPlayer());
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        manager.onBlockBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            manager.markInCombat(attacker);
            if (!event.isCancelled()) {
                manager.onAttack(attacker, event.getEntity());
            }
        }
        if (event.getEntity() instanceof Player victim) {
            manager.markInCombat(victim);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInCombat(player, COMBAT_GRACE_MS)) {
            return;
        }
        double yawDelta = normalizeDegrees(event.getTo().yaw - event.getFrom().yaw);
        double pitchDelta = event.getTo().pitch - event.getFrom().pitch;
        manager.onAimSample(player, yawDelta, pitchDelta);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.forget(event.getPlayer());
    }

    private double normalizeDegrees(double degrees) {
        double d = degrees % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }
}
