package net.factionscore.genbucket;

import org.powernukkitx.item.Item;
import org.powernukkitx.item.customitem.CustomItem;
import org.powernukkitx.item.customitem.CustomItemDefinition;
import org.powernukkitx.item.customitem.data.CreativeCategory;
import org.powernukkitx.utils.TextFormat;

/**
 * Gen buckets: one-use items that grow a line of blocks from where you click -- the standard
 * factions tool for raising base walls without placing thousands of blocks by hand. Clicking the
 * top/bottom of a block generates a vertical column; clicking a side generates a horizontal run
 * extending away from the clicked face.
 */
public abstract class GenBucketItem extends Item implements CustomItem {

    private final String generatesBlockId;
    private final String displayName;

    protected GenBucketItem(String itemId, String generatesBlockId, String displayName) {
        super(itemId);
        this.generatesBlockId = generatesBlockId;
        this.displayName = displayName;
        this.name = displayName;
        setLore(
                TextFormat.YELLOW + "▶ " + TextFormat.GRAY + "Click the " + TextFormat.GREEN + "TOP" + TextFormat.GRAY + " of a block: wall goes " + TextFormat.GREEN + "UP",
                TextFormat.YELLOW + "▶ " + TextFormat.GRAY + "Click a " + TextFormat.AQUA + "SIDE" + TextFormat.GRAY + ": line extends " + TextFormat.AQUA + "OUTWARD",
                TextFormat.YELLOW + "▶ " + TextFormat.GRAY + "Stops at blocks, claims, or max length.",
                TextFormat.DARK_GRAY + "" + TextFormat.ITALIC + "One use per bucket.");
    }

    public String getGeneratesBlockId() {
        return generatesBlockId;
    }

    @Override
    public CustomItemDefinition getDefinition() {
        return CustomItemDefinition.customBuilder(this)
                .name(displayName)
                .texture("bucket_empty")
                .allowOffHand(false)
                .creativeCategory(CreativeCategory.ITEMS)
                .glint(true)
                .maxStackSize(16)
                .build();
    }
}
