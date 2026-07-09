package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.item.enchantment.EnchantmentType;

public final class LeechEnchant extends CustomEnchant {
    public LeechEnchant() {
        super("leech", "Leech", Rarity.RARE, EnchantmentType.SWORD, 3);
    }

    @Override
    protected String getDisplayName() {
        return "Leech";
    }
}
