package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.item.enchantment.EnchantmentType;

public final class LightningEnchant extends CustomEnchant {
    public LightningEnchant() {
        super("lightning", "Lightning", Rarity.VERY_RARE, EnchantmentType.SWORD, 2);
    }

    @Override
    protected String getDisplayName() {
        return "Lightning";
    }
}
