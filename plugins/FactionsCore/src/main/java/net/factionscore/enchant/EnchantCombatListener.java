package net.factionscore.enchant;

import org.powernukkitx.Player;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.EntityLiving;
import org.powernukkitx.entity.effect.Effect;
import org.powernukkitx.entity.effect.EffectType;
import org.powernukkitx.entity.weather.EntityLightningBolt;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;
import org.powernukkitx.event.weather.LightningStrikeEvent;
import org.powernukkitx.item.Item;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.nbt.tag.DoubleTag;
import org.powernukkitx.nbt.tag.FloatTag;
import org.powernukkitx.nbt.tag.ListTag;
import org.powernukkitx.utils.Identifier;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies the on-hit effect of every CosmicPvP-style custom enchant. PowerNukkitX's own
 * enchantment framework only wires narrow hook points (armor thorns-style {@code doPostAttack},
 * weapon {@code getDamageBonus}) so procs that don't fit those shapes (stuns, launches, webs,
 * lightning...) are applied directly here from the same damage event the vanilla combat pipeline
 * already fires, right after knockback/critical tuning ({@link net.factionscore.faction.listener.CombatTuningListener}) runs.
 */
public final class EnchantCombatListener implements Listener {

    private final CustomEnchantRegistry registry;

    public EnchantCombatListener(CustomEnchantRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof EntityLiving victim)) return;

        Item weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.isNull()) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();

        applyIfPresent(weapon, "stun", level -> {
            if (roll(random, level)) {
                victim.addEffect(Effect.get(EffectType.SLOWNESS).setDuration(20 + level * 20).setAmplifier(2 + level));
                victim.addEffect(Effect.get(EffectType.MINING_FATIGUE).setDuration(20 + level * 20).setAmplifier(2));
            }
        });

        applyIfPresent(weapon, "tornado", level -> {
            if (roll(random, level)) {
                victim.setMotion(new Vector3(
                        (random.nextDouble() - 0.5) * 0.6 * level,
                        0.6 + 0.2 * level,
                        (random.nextDouble() - 0.5) * 0.6 * level));
            }
        });

        applyIfPresent(weapon, "leech", level -> {
            double healAmount = event.getFinalDamage() * (0.1 * level);
            attacker.heal((float) healAmount);
        });

        applyIfPresent(weapon, "blaze", level -> victim.setOnFire(2 * level));

        applyIfPresent(weapon, "spring", level -> {
            if (roll(random, level)) {
                victim.setMotion(new Vector3(0, 0.5 + 0.25 * level, 0));
            }
        });

        applyIfPresent(weapon, "lightning", level -> {
            if (roll(random, level / 2.0)) {
                strikeLightning(victim, level);
            }
        });

        applyIfPresent(weapon, "gravity", level -> {
            Vector3 towardAttacker = new Vector3(attacker.x - victim.x, 0, attacker.z - victim.z);
            double len = towardAttacker.length();
            if (len > 0.01) {
                double pull = 0.15 * level / len;
                victim.setMotion(new Vector3(towardAttacker.x * pull, 0.1, towardAttacker.z * pull));
            }
        });

        applyIfPresent(weapon, "webber", level -> trapInWeb(victim, level));

        if (victim instanceof Player victimPlayer) {
            checkEscape(attacker, victimPlayer);
        }
        checkHaste(attacker);
    }

    private void checkEscape(Player attacker, Player victim) {
        Item boots = victim.getInventory().getBoots();
        if (boots.isNull()) return;
        int level = boots.getCustomEnchantmentLevel(registry.identifierOf("escape"));
        if (level <= 0) return;
        if (!roll(ThreadLocalRandom.current(), level)) return;

        Vector3 away = new Vector3(victim.x - attacker.x, 0, victim.z - attacker.z);
        double len = away.length();
        if (len < 0.01) return;
        double distance = 3 + level;
        double nx = victim.x + (away.x / len) * distance;
        double nz = victim.z + (away.z / len) * distance;
        victim.teleport(new org.powernukkitx.level.Position(nx, victim.y, nz, victim.getLevel()));
    }

    private void checkHaste(Player player) {
        Item item = player.getInventory().getItemInMainHand();
        if (item.isNull()) return;
        int level = item.getCustomEnchantmentLevel(registry.identifierOf("haste"));
        if (level <= 0) return;
        Effect current = player.getEffect(EffectType.HASTE);
        if (current == null || current.getDuration() < 60) {
            player.addEffect(Effect.get(EffectType.HASTE).setDuration(200).setAmplifier(level - 1));
        }
    }

    private void applyIfPresent(Item weapon, String enchantId, java.util.function.IntConsumer effect) {
        Identifier identifier = registry.identifierOf(enchantId);
        int level = weapon.getCustomEnchantmentLevel(identifier);
        if (level > 0) {
            effect.accept(level);
        }
    }

    private boolean roll(ThreadLocalRandom random, double level) {
        // level 1 => 20% chance, scaling up; kept modest since these stack with normal PvP.
        double chance = Math.min(0.75, 0.2 * level);
        return random.nextDouble() < chance;
    }

    private void strikeLightning(Entity target, int level) {
        Level level0 = target.getLevel();
        if (level0 == null) return;
        IChunk chunk = level0.getChunk(target.getChunkX(), target.getChunkZ(), true);
        CompoundTag nbt = new CompoundTag()
                .putList("Pos", new ListTag<DoubleTag>().add(new DoubleTag(target.x))
                        .add(new DoubleTag(target.y)).add(new DoubleTag(target.z)))
                .putList("Motion", new ListTag<DoubleTag>().add(new DoubleTag(0))
                        .add(new DoubleTag(0)).add(new DoubleTag(0)))
                .putList("Rotation", new ListTag<FloatTag>().add(new FloatTag(0)).add(new FloatTag(0)));
        EntityLightningBolt bolt = new EntityLightningBolt(chunk, nbt);
        LightningStrikeEvent event = new LightningStrikeEvent(level0, bolt);
        level0.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            bolt.setEffect(false);
            return;
        }
        bolt.spawnToAll();
        if (target instanceof EntityLiving living) {
            living.setHealthCurrent(Math.max(0, living.getHealthCurrent() - level));
        }
    }

    private void trapInWeb(EntityLiving victim, int level) {
        Level lvl = victim.getLevel();
        if (lvl == null) return;
        int radius = Math.min(level, 3);
        java.util.List<Vector3> placed = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                Vector3 pos = new Vector3(victim.getFloorX() + dx, victim.getFloorY(), victim.getFloorZ() + dz);
                var existing = lvl.getBlock(pos);
                if (existing.isAir()) {
                    lvl.setBlock(pos, org.powernukkitx.block.Block.get(org.powernukkitx.block.BlockID.WEB));
                    placed.add(pos);
                }
            }
        }
        if (!placed.isEmpty()) {
            lvl.getServer().getScheduler().scheduleDelayedTask(() -> {
                for (Vector3 pos : placed) {
                    if (org.powernukkitx.block.BlockID.WEB.equals(lvl.getBlock(pos).getId())) {
                        lvl.setBlock(pos, org.powernukkitx.block.Block.get(org.powernukkitx.block.BlockID.AIR));
                    }
                }
            }, 100);
        }
    }
}
