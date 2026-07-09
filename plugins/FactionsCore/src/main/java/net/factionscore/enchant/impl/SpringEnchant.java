package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.item.enchantment.EnchantmentType;

public final class SpringEnchant extends CustomEnchant {
    public SpringEnchant() {
        super("spring", "Spring", Rarity.UNCOMMON, EnchantmentType.SWORD, 3);
    }

    @Override
    protected String getDisplayName() {
        return "Spring";
    }
}
