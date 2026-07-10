package net.factionscore.enchant;

import net.factionscore.enchant.book.CustomEnchantBookItem;
import org.powernukkitx.Player;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.entity.effect.Effect;
import org.powernukkitx.entity.effect.EffectType;
import org.powernukkitx.entity.weather.EntityLightningBolt;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityDamageEvent;
import org.powernukkitx.event.player.PlayerTransferItemEvent;
import org.powernukkitx.event.weather.LightningStrikeEvent;
import org.powernukkitx.item.Item;
import org.powernukkitx.item.enchantment.Enchantment;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.nbt.tag.DoubleTag;
import org.powernukkitx.nbt.tag.FloatTag;
import org.powernukkitx.nbt.tag.ListTag;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies a custom enchant book directly to an item dragged onto it in the player's own
 * inventory -- no anvil. The interaction is a vanilla "swap two dissimilar items" gesture, which
 * the engine already surfaces as {@link PlayerTransferItemEvent} (type SWAP) with both items and
 * their inventories/slots resolved, so this only ever intervenes when the source is specifically
 * one of our book items and the destination is a valid target for that enchant -- any other
 * drag/swap in the game passes through completely untouched.
 */
public final class EnchantApplyListener implements Listener {

    private final CustomEnchantRegistry registry;
    private final Config config;

    public EnchantApplyListener(CustomEnchantRegistry registry, Config config) {
        this.registry = registry;
        this.config = config;
    }

    @EventHandler
    public void onTransfer(PlayerTransferItemEvent event) {
        if (event.getType() != PlayerTransferItemEvent.Type.SWAP) return;
        if (!(event.getSourceItem() instanceof CustomEnchantBookItem book)) return;

        Item target = event.getDestinationItem().orElse(null);
        if (target == null || target.isNull()) return;
        if (event.getDestinationInventory().isEmpty() || event.getDestinationSlot().isEmpty()) return;

        var enchantOpt = registry.get(book.getEnchantKey());
        if (enchantOpt.isEmpty()) return;
        CustomEnchant enchant = enchantOpt.get();
        if (!enchant.canEnchant(target)) return;

        int level = clamp(book.getLevel(), 1, enchant.getMaxLevel());
        int maxPerItem = config.getInt("enchants.max-custom-enchants-per-item", 3);
        if (countCustomEnchants(target) >= maxPerItem && target.getCustomEnchantmentLevel(registry.identifierOf(book.getEnchantKey())) == 0) {
            return; // let the swap happen normally; nothing we can add here
        }

        // From here on this is definitely our interaction: take it over completely.
        event.setCancelled(true);
        Player player = event.getPlayer();

        int requiredLevel = indexed(config.getIntegerList("enchants.apply.required-player-level"), level, 0);
        if (player.getExperienceLevel() < requiredLevel) {
            player.sendMessage(TextFormat.RED + "You need to be XP level " + requiredLevel + " to attempt a " + book.getEnchantKey() + " " + level + " book.");
            return;
        }

        boolean combined = event.getSourceItem().getCount() >= 2;
        int consumed = combined ? 2 : 1;
        double chance = combined
                ? indexed(config.getDoubleList("enchants.apply.combined-success-chance"), level, 0.5)
                : indexed(config.getDoubleList("enchants.apply.base-success-chance"), level, 0.5);

        consumeBook(event, consumed);

        boolean success = ThreadLocalRandom.current().nextDouble() < chance;
        if (success) {
            Item enchanted = target.clone();
            Enchantment instance = enchant;
            instance.setLevel(level, false);
            enchanted.addEnchantment(instance);
            event.getDestinationInventory().get().setItem(event.getDestinationSlot().get(), enchanted);
            player.sendMessage(TextFormat.GREEN + "Success! " + book.getEnchantKey() + " " + level + " applied.");
        } else {
            player.sendMessage(TextFormat.RED + "The enchant failed and backfired on you!");
            applyBackfire(player, book.getEnchantKey(), level);
        }
    }

    private void consumeBook(PlayerTransferItemEvent event, int consumed) {
        Item remaining = event.getSourceItem().clone();
        remaining.setCount(remaining.getCount() - consumed);
        event.getSourceInventory().setItem(event.getSourceSlot(), remaining.getCount() <= 0 ? Item.get(BlockID.AIR) : remaining);
    }

    private int countCustomEnchants(Item item) {
        int count = 0;
        for (Enchantment e : item.getEnchantments()) {
            if (e.getIdentifier() != null) count++;
        }
        return count;
    }

    private void applyBackfire(Player player, String enchantKey, int level) {
        double baseDamage = config.getDouble("enchants.apply.fail-damage", 4.0);
        player.attack(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.MAGIC, (float) baseDamage));

        switch (enchantKey) {
            case "lightning" -> strikeLightningOn(player);
            case "blaze" -> player.setOnFire(4);
            case "stun" -> {
                player.addEffect(Effect.get(EffectType.SLOWNESS).setDuration(60).setAmplifier(2));
                player.addEffect(Effect.get(EffectType.BLINDNESS).setDuration(60).setAmplifier(1));
            }
            default -> {
                // base damage above is the whole backfire for enchants without a signature effect
            }
        }
    }

    private void strikeLightningOn(Player player) {
        Level level = player.getLevel();
        if (level == null) return;
        IChunk chunk = level.getChunk(player.getChunkX(), player.getChunkZ(), true);
        CompoundTag nbt = new CompoundTag()
                .putList("Pos", new ListTag<DoubleTag>().add(new DoubleTag(player.x))
                        .add(new DoubleTag(player.y)).add(new DoubleTag(player.z)))
                .putList("Motion", new ListTag<DoubleTag>().add(new DoubleTag(0))
                        .add(new DoubleTag(0)).add(new DoubleTag(0)))
                .putList("Rotation", new ListTag<FloatTag>().add(new FloatTag(0)).add(new FloatTag(0)));
        EntityLightningBolt bolt = new EntityLightningBolt(chunk, nbt);
        LightningStrikeEvent event = new LightningStrikeEvent(level, bolt);
        level.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            bolt.setEffect(false);
            return;
        }
        bolt.spawnToAll();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int indexed(List<Integer> list, int level, int fallback) {
        if (list == null || list.isEmpty()) return fallback;
        return list.get(Math.min(level - 1, list.size() - 1));
    }

    private double indexed(List<Double> list, int level, double fallback) {
        if (list == null || list.isEmpty()) return fallback;
        return list.get(Math.min(level - 1, list.size() - 1));
    }
}
