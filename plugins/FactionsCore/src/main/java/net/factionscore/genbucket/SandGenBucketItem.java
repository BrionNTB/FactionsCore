package net.factionscore.genbucket;

import org.powernukkitx.block.BlockID;
import org.powernukkitx.utils.TextFormat;

public final class SandGenBucketItem extends GenBucketItem {
    public static final String ID = "factionscore:genbucket_sand";

    public SandGenBucketItem() {
        super(ID, BlockID.SAND, TextFormat.YELLOW + "Sand Gen Bucket");
    }
}
