package net.factionscore.genbucket;

import org.powernukkitx.block.BlockID;
import org.powernukkitx.utils.TextFormat;

public final class ObsidianGenBucketItem extends GenBucketItem {
    public static final String ID = "factionscore:genbucket_obsidian";

    public ObsidianGenBucketItem() {
        super(ID, BlockID.OBSIDIAN, TextFormat.DARK_PURPLE + "Obsidian Gen Bucket");
    }
}
