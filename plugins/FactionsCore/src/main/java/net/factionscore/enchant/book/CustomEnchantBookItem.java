package net.factionscore.enchant.book;

import org.powernukkitx.item.Item;
import org.powernukkitx.item.customitem.CustomItem;
import org.powernukkitx.item.customitem.CustomItemDefinition;
import org.powernukkitx.item.customitem.data.CreativeCategory;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.utils.TextFormat;

/**
 * A book for one custom enchant. Unlike vanilla enchanted books (which are effectively unique per
 * enchant+level via the engine's own auto-generated-book mechanism, see
 * {@link net.factionscore.enchant.CustomEnchantRegistry}), these ship as one item id per enchant
 * with the level stored in NBT -- simpler to spawn/administer, and the level travels correctly
 * through drag-to-apply (see net.factionscore.enchant.EnchantApplyListener) regardless of stack
 * operations.
 */
public abstract class CustomEnchantBookItem extends Item implements CustomItem {

    private static final String LEVEL_TAG = "FCBookLevel";

    private final String enchantKey;
    private final String displayName;
    private final String description;

    protected CustomEnchantBookItem(String itemId, String enchantKey, String displayName, String description) {
        super(itemId);
        this.enchantKey = enchantKey;
        this.displayName = displayName;
        this.description = description;
        this.name = displayName + " Book";
        refreshLore();
    }

    public String getEnchantKey() {
        return enchantKey;
    }

    public int getLevel() {
        CompoundTag nbt = getNbt();
        return nbt != null && nbt.contains(LEVEL_TAG) ? nbt.getInt(LEVEL_TAG) : 1;
    }

    public void setLevel(int level) {
        CompoundTag nbt = getOrCreateNbt();
        nbt.putInt(LEVEL_TAG, level);
        setNbt(nbt);
        refreshLore();
    }

    private void refreshLore() {
        setLore(
                TextFormat.LIGHT_PURPLE + "" + TextFormat.BOLD + displayName + " " + roman(getLevel()),
                TextFormat.GRAY + "" + TextFormat.ITALIC + description,
                "",
                TextFormat.YELLOW + "▶ " + TextFormat.GRAY + "Drag onto an item to apply. " + TextFormat.RED + "No anvil!",
                TextFormat.YELLOW + "▶ " + TextFormat.GRAY + "Can " + TextFormat.RED + "" + TextFormat.BOLD + "FAIL" + TextFormat.RESET
                        + TextFormat.GRAY + " and backfire on you...",
                TextFormat.YELLOW + "▶ " + TextFormat.GRAY + "Drag a stack of " + TextFormat.GREEN + "2" + TextFormat.GRAY + " for better odds!");
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> String.valueOf(level);
        };
    }

    @Override
    public int getMaxStackSize() {
        // Needs to be stackable so a player can drag a *stack* of 2+ onto a target item for the
        // higher "combined" success chance (see EnchantApplyListener).
        return 64;
    }

    @Override
    public CustomItemDefinition getDefinition() {
        return CustomItemDefinition.customBuilder(this)
                .name(TextFormat.BOLD + "" + TextFormat.LIGHT_PURPLE + displayName + " Book")
                .texture("book_enchanted")
                .allowOffHand(false)
                .creativeCategory(CreativeCategory.ITEMS)
                .creativeGroup("itemGroup.name.enchantedBook")
                .glint(true)
                .maxStackSize(64)
                .build();
    }
}
