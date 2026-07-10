package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.item.enchantment.EnchantmentType;

/**
 * A custom weapon line that goes past vanilla Sharpness's cap of V, up to VI. Uses vanilla's own
 * damage-bonus formula (0.5 + level * 0.5) so levels I-V feel identical to vanilla Sharpness.
 */
public final class SharpnessEnchant extends CustomEnchant {
    public SharpnessEnchant() {
        super("sharpness", "Sharpness", Rarity.COMMON, EnchantmentType.SWORD, 6);
    }

    @Override
    protected String getDisplayName() {
        return "Sharpness";
    }

    @Override
    public double getDamageBonus(Entity target, Entity damager) {
        return 0.5 + getLevel() * 0.5;
    }
}
