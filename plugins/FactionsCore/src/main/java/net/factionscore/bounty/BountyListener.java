package net.factionscore.bounty;

import net.factionscore.economy.EconomyManager;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;
import org.powernukkitx.event.player.PlayerDeathEvent;
import org.powernukkitx.utils.TextFormat;

public final class BountyListener implements Listener {

    private final BountyManager bounties;

    public BountyListener(BountyManager bounties) {
        this.bounties = bounties;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent damage)) {
            return;
        }
        if (!(damage.getDamager() instanceof Player killer) || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        double paid = bounties.claim(victim.getUniqueId(), killer.getUniqueId());
        if (paid > 0) {
            Server.getInstance().broadcastMessage(TextFormat.GOLD + killer.getName() + " claimed the "
                    + TextFormat.RED + EconomyManager.format(paid) + TextFormat.GOLD + " bounty on " + victim.getName() + "!");
        }
    }
}
