package net.factionscore.misc;

import org.powernukkitx.Player;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;

public final class WarmupListener implements Listener {

    private final WarmupManager warmups;

    public WarmupListener(WarmupManager warmups) {
        this.warmups = warmups;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player)) return;
        warmups.cancel(victim, "You were hit -- run the command again.");
    }
}
