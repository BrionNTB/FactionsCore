package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.item.enchantment.EnchantmentType;

public final class BlazeEnchant extends CustomEnchant {
    public BlazeEnchant() {
        super("blaze", "Blaze", Rarity.UNCOMMON, EnchantmentType.SWORD, 3);
    }

    @Override
    protected String getDisplayName() {
        return "Blaze";
    }
}
