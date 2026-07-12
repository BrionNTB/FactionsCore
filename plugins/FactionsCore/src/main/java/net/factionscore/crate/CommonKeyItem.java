package net.factionscore.crate;

import org.powernukkitx.utils.TextFormat;

public final class CommonKeyItem extends KeyItem {
    public static final String ID = "factionscore:key_common";

    public CommonKeyItem() {
        super(ID, "common", TextFormat.YELLOW + "" + TextFormat.BOLD + "Common Crate Key");
    }
}
