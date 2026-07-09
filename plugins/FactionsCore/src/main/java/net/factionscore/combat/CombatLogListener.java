package net.factionscore.combat;

import net.factionscore.faction.FactionManager;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;
import org.powernukkitx.event.player.PlayerQuitEvent;
import org.powernukkitx.event.player.PlayerTeleportEvent;
import org.powernukkitx.item.Item;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

public final class CombatLogListener implements Listener {

    private final CombatTagManager tagManager;
    private final FactionManager factionManager;
    private final Config config;

    public CombatLogListener(CombatTagManager tagManager, FactionManager factionManager, Config config) {
        this.tagManager = tagManager;
        this.factionManager = factionManager;
        this.config = config;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config.getBoolean("combatlog.enabled", true)) return;
        if (event.isCancelled()) return;
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            tagManager.tag(attacker);
            tagManager.tag(victim);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!config.getBoolean("combatlog.enabled", true)) return;
        if (!tagManager.isTagged(player)) return;
        if (player.hasPermission("factionscore.bypass.combatlog")) return;

        killForCombatLog(player);
        tagManager.clear(player);
    }

    /**
     * The player's session is already tearing down by the time PlayerQuitEvent fires (chunks
     * unloaded, about to be removed from the online list), so this deliberately doesn't route
     * through the normal damage/attack pipeline -- that sends packets that assume a connected
     * client. Dropping items and applying the faction penalty directly is what still reliably
     * runs during a disconnect.
     */
    private void killForCombatLog(Player player) {
        for (Item item : player.getInventory().getContents().values()) {
            if (item != null && !item.isNull()) {
                player.getLevel().dropItem(player, item);
            }
        }
        for (Item item : player.getInventory().getArmorInventory().getContents().values()) {
            if (item != null && !item.isNull()) {
                player.getLevel().dropItem(player, item);
            }
        }
        player.getInventory().clearAll();
        player.getInventory().getArmorInventory().clearAll();
        player.setHealthCurrent(0);

        factionManager.getByMember(player.getUniqueId()).ifPresent(factionManager::onMemberDeath);

        Server.getInstance().broadcastMessage(TextFormat.RED + player.getName() + " combat logged and died.");
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (!config.getBoolean("combatlog.block-teleport", true)) return;
        Player player = event.getPlayer();
        if (!tagManager.isTagged(player)) return;
        if (player.hasPermission("factionscore.bypass.combatlog")) return;

        event.setCancelled(true);
        player.sendMessage(TextFormat.RED + "You can't teleport for " + tagManager.remainingSeconds(player) + "s, you're in combat!");
    }
}
