package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.event.entity.EntityDamageEvent;
import org.powernukkitx.item.enchantment.EnchantmentType;

/**
 * A custom armor line that goes past vanilla Protection's cap of IV, up to VI. Mirrors vanilla's
 * "1 EPF point per level per armor piece" shape so 4 pieces at max level lands in the same
 * ballpark as a fully vanilla-enchanted set, just with more headroom.
 */
public final class ProtectionEnchant extends CustomEnchant {
    public ProtectionEnchant() {
        super("protection", "Protection", Rarity.COMMON, EnchantmentType.ARMOR, 6);
    }

    @Override
    protected String getDisplayName() {
        return "Protection";
    }

    @Override
    public float getProtectionFactor(EntityDamageEvent event) {
        return getLevel() * 2f;
    }
}
