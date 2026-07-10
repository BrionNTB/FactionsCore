package net.factionscore.genbucket;

import org.powernukkitx.block.BlockID;
import org.powernukkitx.utils.TextFormat;

public final class CobbleGenBucketItem extends GenBucketItem {
    public static final String ID = "factionscore:genbucket_cobblestone";

    public CobbleGenBucketItem() {
        super(ID, BlockID.COBBLESTONE, TextFormat.GRAY + "Cobblestone Gen Bucket");
    }
}
