package net.factionscore.hopper;

import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.item.Item;
import org.powernukkitx.item.customitem.CustomItem;
import org.powernukkitx.item.customitem.CustomItemDefinition;
import org.powernukkitx.item.customitem.data.CreativeCategory;
import org.powernukkitx.utils.TextFormat;

public final class ItemHopperTier3 extends Item implements CustomItem {

    public static final String ID = "factionscore:hopper_tier3";

    public ItemHopperTier3() {
        super(ID);
        this.block = Block.get(BlockID.HOPPER);
        this.name = "Hopper III";
    }

    @Override
    public CustomItemDefinition getDefinition() {
        return CustomItemDefinition.customBuilder(this)
                .name(TextFormat.GOLD + "Hopper III")
                .texture("hopper")
                .allowOffHand(false)
                .creativeCategory(CreativeCategory.ITEMS)
                .creativeGroup("itemGroup.name.hopper")
                .glint(true)
                .build();
    }
}
