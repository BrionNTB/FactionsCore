package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.item.enchantment.EnchantmentType;

public final class TornadoEnchant extends CustomEnchant {
    public TornadoEnchant() {
        super("tornado", "Tornado", Rarity.RARE, EnchantmentType.SWORD, 3);
    }

    @Override
    protected String getDisplayName() {
        return "Tornado";
    }
}
