package net.factionscore.stacking;

import net.factionscore.faction.FactionManager;
import net.factionscore.faction.FactionValueManager;
import org.powernukkitx.Player;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.blockentity.BlockEntityMobSpawner;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.EntityCreature;
import org.powernukkitx.entity.EntityLiving;
import org.powernukkitx.entity.item.EntityXpOrb;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityDeathEvent;
import org.powernukkitx.event.entity.EntitySpawnEvent;
import org.powernukkitx.event.player.PlayerInteractEvent;
import org.powernukkitx.item.Item;
import org.powernukkitx.utils.TextFormat;

public final class StackingListener implements Listener {

    private final MobStackManager mobStacks;
    private final SpawnerStackManager spawnerStacks;
    private final FactionManager factions;
    private final FactionValueManager values;
    private final net.factionscore.hologram.HologramManager holograms;

    public StackingListener(MobStackManager mobStacks, SpawnerStackManager spawnerStacks, FactionManager factions,
                            FactionValueManager values, net.factionscore.hologram.HologramManager holograms) {
        this.mobStacks = mobStacks;
        this.spawnerStacks = spawnerStacks;
        this.factions = factions;
        this.values = values;
        this.holograms = holograms;
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityXpOrb orb) {
            long tick = orb.getLevel() != null ? orb.getLevel().getCurrentTick() : 0;
            if (mobStacks.isRecentStackDeath(tick)) {
                orb.setExp(orb.getExp() * mobStacks.consumeXpMultiplier());
            }
            return;
        }
        if (!mobStacks.isEnabled() || !event.isCreature()) return;
        if (!(entity instanceof EntityCreature creature)) return;
        if (mobStacks.isExcluded(creature.getIdentifier())) return;
        // The engine stamps spawner-produced mobs with this NBT flag at creation (fork patch in
        // BlockEntityMobSpawner) -- naturally spawned mobs never carry it.
        if (mobStacks.isSpawnerOnly() && !creature.getNbt().getBoolean("spawner")) return;

        Entity mergeTarget = mobStacks.findMergeTarget(creature);
        if (mergeTarget != null) {
            mobStacks.merge(mergeTarget);
            event.setCancelled(true);
            creature.close();
        } else {
            mobStacks.registerNewHead(creature);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!mobStacks.isEnabled()) return;
        if (!(event.getEntity() instanceof EntityLiving living)) return;
        long tick = living.getLevel() != null ? living.getLevel().getCurrentTick() : 0;
        int multiplier = mobStacks.consumeOnDeath(living, tick);
        if (multiplier <= 1) return;

        Item[] drops = event.getDrops();
        Item[] scaled = new Item[drops.length];
        for (int i = 0; i < drops.length; i++) {
            scaled[i] = drops[i].clone();
            scaled[i].setCount(drops[i].getCount() * multiplier);
        }
        event.setDrops(scaled);
    }

    /**
     * Spawner stacking, the standard factions way: right-click an existing spawner while HOLDING
     * spawner items of the same mob type -- one item is consumed and the block's stack goes up
     * (instead of placing a second block next to it). Empty-hand sneak-click shows info.
     */
    @EventHandler(priority = org.powernukkitx.event.EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!spawnerStacks.isEnabled()) return;
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        var block = event.getBlock();
        if (block == null || !org.powernukkitx.block.BlockID.MOB_SPAWNER.equals(block.getId())) return;

        BlockEntity blockEntity = player.getLevel().getBlockEntity(block);
        if (!(blockEntity instanceof BlockEntityMobSpawner spawner)) return;

        Item held = player.getInventory().getItemInMainHand();

        if (held != null && !held.isNull() && org.powernukkitx.block.BlockID.MOB_SPAWNER.equals(held.getId())) {
            // Never place a second spawner block when clicking an existing one.
            event.setCancelled(true);

            String heldMob = held.hasNbt() && held.getNbt().contains(SpawnerCommand.MOB_TAG)
                    ? held.getNbt().getString(SpawnerCommand.MOB_TAG) : null;
            int heldNetworkId = heldMob == null ? 0 : org.powernukkitx.registry.Registries.ENTITY.getEntityNetworkId(heldMob);
            if (heldNetworkId <= 0 || spawner.getSpawnEntityType() <= 0 || heldNetworkId != spawner.getSpawnEntityType()) {
                player.sendMessage(TextFormat.RED + "You can only stack a spawner of the " + TextFormat.BOLD + "same mob type" + TextFormat.RESET + TextFormat.RED + " onto this one.");
                return;
            }

            int newLevel = spawnerStacks.increment(spawner);
            if (newLevel < 0) {
                player.sendMessage(TextFormat.RED + "This spawner is maxed out at " + TextFormat.BOLD + "x" + spawnerStacks.maxStackSize() + TextFormat.RESET + TextFormat.RED + ".");
                return;
            }

            Item remaining = held.clone();
            remaining.setCount(remaining.getCount() - 1);
            player.getInventory().setItemInMainHand(remaining.getCount() <= 0 ? Item.get(org.powernukkitx.block.BlockID.AIR) : remaining);

            player.sendMessage(TextFormat.GREEN + "Spawner stacked to " + TextFormat.BOLD + "x" + newLevel + TextFormat.RESET + TextFormat.GREEN + "!");
            holograms.set(player.getLevel(), block, net.factionscore.hologram.HologramManager.spawnerTitle(spawner));
            factions.getClaimOwner(block.getLocation())
                    .ifPresent(owner -> values.onSpawnerStackIncreased(player.getLevel(), block, newLevel));
            return;
        }

        if (player.isSneaking()) {
            event.setCancelled(true);
            player.sendMessage(TextFormat.GOLD + "▶ " + net.factionscore.hologram.HologramManager.spawnerTitle(spawner));
        }
    }
}
