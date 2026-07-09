package net.factionscore.enchant.impl;

import net.factionscore.enchant.CustomEnchant;
import org.powernukkitx.item.enchantment.EnchantmentType;

/** Worn on armor: chance to short-blink away from the attacker when hit. */
public final class EscapeEnchant extends CustomEnchant {
    public EscapeEnchant() {
        super("escape", "Escape", Rarity.RARE, EnchantmentType.ARMOR_FEET, 3);
    }

    @Override
    protected String getDisplayName() {
        return "Escape";
    }
}
