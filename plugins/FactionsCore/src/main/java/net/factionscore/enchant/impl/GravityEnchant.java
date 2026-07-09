package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.item.enchantment.EnchantmentType;

public final class GravityEnchant extends CustomEnchant {
    public GravityEnchant() {
        super("gravity", "Gravity", Rarity.RARE, EnchantmentType.SWORD, 3);
    }

    @Override
    protected String getDisplayName() {
        return "Gravity";
    }
}
