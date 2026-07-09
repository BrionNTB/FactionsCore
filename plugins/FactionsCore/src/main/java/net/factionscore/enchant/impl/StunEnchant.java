package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.item.enchantment.EnchantmentType;

public final class StunEnchant extends CustomEnchant {
    public StunEnchant() {
        super("stun", "Stun", Rarity.RARE, EnchantmentType.SWORD, 3);
    }

    @Override
    protected String getDisplayName() {
        return "Stun";
    }
}
