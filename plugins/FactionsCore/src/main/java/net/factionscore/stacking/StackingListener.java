package net.factionscore.stacking;

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

    public StackingListener(MobStackManager mobStacks, SpawnerStackManager spawnerStacks) {
        this.mobStacks = mobStacks;
        this.spawnerStacks = spawnerStacks;
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

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!spawnerStacks.isEnabled()) return;
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (event.getBlock() == null) return;

        BlockEntity blockEntity = player.getLevel().getBlockEntity(event.getBlock());
        if (!(blockEntity instanceof BlockEntityMobSpawner spawner)) return;
        if (!player.hasPermission("factionscore.command.stack")) return;

        int newLevel = spawnerStacks.increment(spawner);
        if (newLevel < 0) {
            player.sendMessage(TextFormat.RED + "This spawner is already at the maximum stack size.");
        } else {
            player.sendMessage(TextFormat.GREEN + "Spawner stacked to x" + newLevel + ".");
        }
    }
}
