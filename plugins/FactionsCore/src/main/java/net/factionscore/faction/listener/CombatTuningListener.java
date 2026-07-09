package net.factionscore.faction.listener;

import org.powernukkitx.Player;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;
import org.powernukkitx.utils.Config;

/**
 * Bedrock never adopted the Java 1.9+ attack-cooldown/sweep-attack system, so combat here is
 * already structurally closer to Java 1.8.8 than modern Java combat is. This listener exposes the
 * remaining tunables (knockback strength, critical multiplier, sprint-reset) so server owners can
 * dial it in to match 1.8.8 PvP feel exactly instead of PowerNukkitX's defaults.
 */
public final class CombatTuningListener implements Listener {

    private final Config config;

    public CombatTuningListener(Config config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config.getBoolean("combat.legacy-knockback", true)) {
            return;
        }
        float base = (float) config.getDouble("combat.base-horizontal-knockback", 0.4);
        event.setKnockBack(base);

        if (config.getBoolean("combat.critical-hit.enabled", true)
                && event.getDamager() instanceof Player attacker
                && !attacker.onGround
                && attacker.motionY < 0
                && !attacker.isSprinting()) {
            double multiplier = config.getDouble("combat.critical-hit.multiplier", 1.5);
            event.setDamage((float) (event.getDamage() * multiplier));
        }

        if (config.getBoolean("combat.sprint-reset-on-hit", true)
                && event.getDamager() instanceof Player attacker
                && attacker.isSprinting()) {
            attacker.setSprinting(false);
        }
    }
}
