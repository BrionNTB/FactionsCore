package net.factionscore.faction.listener;

import net.factionscore.faction.Faction;
import net.factionscore.faction.FactionManager;
import net.factionscore.faction.RelationType;
import org.powernukkitx.Player;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.block.BlockBreakEvent;
import org.powernukkitx.event.block.BlockPlaceEvent;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;
import org.powernukkitx.event.entity.EntityDamageEvent;
import org.powernukkitx.level.Position;
import org.powernukkitx.utils.TextFormat;
import org.powernukkitx.utils.Config;

import java.util.Optional;
import java.util.UUID;

public final class ClaimProtectionListener implements Listener {

    private final FactionManager factions;
    private final Config config;

    public ClaimProtectionListener(FactionManager factions, Config config) {
        this.factions = factions;
        this.config = config;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!config.getBoolean("factions.claims.protect-build", true)) return;
        Player player = event.getPlayer();
        if (player.hasPermission("factionscore.bypass.claims")) return;
        if (!canBuild(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(TextFormat.RED + "You cannot build in claimed territory.");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!config.getBoolean("factions.claims.protect-build", true)) return;
        Player player = event.getPlayer();
        if (player.hasPermission("factionscore.bypass.claims")) return;
        if (!canBuild(player, event.getBlockReplace().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(TextFormat.RED + "You cannot build in claimed territory.");
        }
    }

    private boolean canBuild(Player player, Position position) {
        Optional<Faction> owner = factions.getClaimOwner(position);
        if (owner.isEmpty()) {
            return true;
        }
        Faction faction = owner.get();
        UUID uuid = player.getUniqueId();
        if (faction.isMember(uuid)) {
            return true;
        }
        // Allies can be granted build rights the same way vanilla factions plugins do it: via
        // relation, not a separate ACL system, to keep this simple.
        return factions.getByMember(uuid)
                .map(playerFaction -> playerFaction.relationWith(faction.getId()) == RelationType.ALLY)
                .orElse(false);
    }

    @EventHandler(priority = org.powernukkitx.event.EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (attacker.hasPermission("factionscore.bypass.claims")) return;

        Optional<Faction> locationClaim = factions.getClaimOwner(new Position(victim.x, victim.y, victim.z, victim.getLevel()));
        if (locationClaim.isPresent() && locationClaim.get().isSafezone()) {
            event.setCancelled(true);
            attacker.sendMessage(TextFormat.RED + "PvP is disabled in this safezone.");
            return;
        }

        Optional<Faction> attackerFaction = factions.getByMember(attacker.getUniqueId());
        Optional<Faction> victimFaction = factions.getByMember(victim.getUniqueId());
        if (attackerFaction.isEmpty() || victimFaction.isEmpty()) {
            return;
        }
        if (attackerFaction.get() == victimFaction.get()) {
            event.setCancelled(true);
            attacker.sendMessage(TextFormat.RED + "You cannot attack your own faction members.");
            return;
        }
        RelationType relation = attackerFaction.get().relationWith(victimFaction.get().getId());
        if (!relation.allowsFriendlyFire()) {
            event.setCancelled(true);
            attacker.sendMessage(TextFormat.RED + "You cannot attack " + relation.name().toLowerCase() + " members.");
        }
    }
}
