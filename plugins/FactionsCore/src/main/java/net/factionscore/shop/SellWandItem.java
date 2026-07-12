package net.factionscore.shop;

import org.powernukkitx.item.Item;
import org.powernukkitx.item.customitem.CustomItem;
import org.powernukkitx.item.customitem.CustomItemDefinition;
import org.powernukkitx.item.customitem.data.CreativeCategory;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.utils.TextFormat;

/**
 * Sell wand: right-click a chest to sell its entire sellable contents at current /sell prices.
 * Carries a limited number of uses in NBT -- a classic paid perk item on factions servers.
 */
public final class SellWandItem extends Item implements CustomItem {

    public static final String ID = "factionscore:sell_wand";
    private static final String USES_TAG = "FCWandUses";

    public SellWandItem() {
        super(ID);
        this.name = "Sell Wand";
        setLore(
                TextFormat.GRAY + "Right-click a " + TextFormat.GOLD + "chest" + TextFormat.GRAY + " to sell everything inside",
                TextFormat.GRAY + "at current " + TextFormat.GREEN + "/sell" + TextFormat.GRAY + " prices.",
                TextFormat.DARK_GRAY + "" + TextFormat.ITALIC + "Get a charged one with /sellwand.");
    }

    public int getUses() {
        CompoundTag nbt = getNbt();
        return nbt != null && nbt.contains(USES_TAG) ? nbt.getInt(USES_TAG) : 0;
    }

    public void setUses(int uses) {
        CompoundTag nbt = getOrCreateNbt();
        nbt.putInt(USES_TAG, uses);
        setNbt(nbt);
        setLore(
                TextFormat.GREEN + "" + TextFormat.BOLD + uses + TextFormat.RESET + TextFormat.GRAY + " uses left",
                TextFormat.GRAY + "Right-click a " + TextFormat.GOLD + "chest" + TextFormat.GRAY + " to sell everything inside",
                TextFormat.GRAY + "at current " + TextFormat.GREEN + "/sell" + TextFormat.GRAY + " prices.");
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public CustomItemDefinition getDefinition() {
        return CustomItemDefinition.customBuilder(this)
                .name(TextFormat.BOLD + "" + TextFormat.GOLD + "Sell Wand")
                .texture("blaze_rod")
                .allowOffHand(false)
                .creativeCategory(CreativeCategory.ITEMS)
                .glint(true)
                .maxStackSize(1)
                .build();
    }
}
