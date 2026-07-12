package net.factionscore.hopper;

import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.item.Item;
import org.powernukkitx.item.customitem.CustomItem;
import org.powernukkitx.item.customitem.CustomItemDefinition;
import org.powernukkitx.item.customitem.data.CreativeCategory;
import org.powernukkitx.utils.TextFormat;

/**
 * Places a real vanilla hopper block (so all the existing item-transfer/redstone-lock logic
 * applies unchanged) tagged as tier 2; {@link HopperTierListener} stamps the tier onto the
 * resulting block entity right after placement. The block_placer component is required so the
 * Bedrock client predicts the placement -- without it the server ignores the use attempt and the
 * hopper simply "won't place".
 */
public final class ItemHopperTier2 extends Item implements CustomItem {

    public static final String ID = "factionscore:hopper_tier2";

    public ItemHopperTier2() {
        super(ID);
        this.block = Block.get(BlockID.HOPPER);
        this.name = "Hopper II";
        setLore(
                TextFormat.YELLOW + "" + TextFormat.BOLD + "1.5x" + TextFormat.RESET + TextFormat.GRAY + " loot from everything it collects.",
                TextFormat.DARK_GRAY + "" + TextFormat.ITALIC + "Place above a chest or hopper line.");
    }

    @Override
    public CustomItemDefinition getDefinition() {
        return CustomItemDefinition.customBuilder(this)
                .name(TextFormat.BOLD + "" + TextFormat.YELLOW + "Hopper II")
                .texture("hopper")
                .allowOffHand(false)
                .creativeCategory(CreativeCategory.ITEMS)
                .creativeGroup("itemGroup.name.hopper")
                .blockPlacer(BlockID.HOPPER)
                .glint(true)
                .build();
    }
}
