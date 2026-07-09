package net.factionscore.enchant;

import org.powernukkitx.item.enchantment.Enchantment;
import org.powernukkitx.item.enchantment.EnchantmentType;
import org.powernukkitx.utils.Identifier;
import org.powernukkitx.utils.TextFormat;

/**
 * Base for the CosmicPvP-style custom enchants. These plug into PowerNukkitX's own custom
 * enchantment framework ({@link Enchantment#register}), so leveled enchanted books, anvil
 * combining, lore rendering and NBT storage are all handled by the engine; only the "what happens
 * when this procs" logic is FactionsCore's to provide, wired up in {@link EnchantCombatListener}.
 */
public abstract class CustomEnchant extends Enchantment {

    public static final String NAMESPACE = "factionscore";

    private final int maxLevel;

    protected CustomEnchant(String path, String displayName, Rarity rarity, EnchantmentType type, int maxLevel) {
        super(new Identifier(NAMESPACE, path), displayName, rarity, type);
        this.maxLevel = maxLevel;
        setObtainableFromEnchantingTable(false);
        setFishable(false);
    }

    @Override
    public int getMaxLevel() {
        return maxLevel;
    }

    @Override
    public String getName() {
        return getDisplayName();
    }

    protected abstract String getDisplayName();

    @Override
    public String getLore() {
        return TextFormat.LIGHT_PURPLE + getDisplayName() + " " + toRomanNumeral(getLevel());
    }

    private static String toRomanNumeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
