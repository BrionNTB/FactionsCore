package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.item.enchantment.EnchantmentType;

public final class WebberEnchant extends CustomEnchant {
    public WebberEnchant() {
        super("webber", "Webber", Rarity.UNCOMMON, EnchantmentType.SWORD, 3);
    }

    @Override
    protected String getDisplayName() {
        return "Webber";
    }
}
