package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.item.enchantment.EnchantmentType;

/** Passive: grants the Haste effect while held, applied by a repeating task rather than on hit. */
public final class HasteEnchant extends CustomEnchant {
    public HasteEnchant() {
        super("haste", "Haste", Rarity.COMMON, EnchantmentType.SWORD, 2);
    }

    @Override
    protected String getDisplayName() {
        return "Haste";
    }
}
