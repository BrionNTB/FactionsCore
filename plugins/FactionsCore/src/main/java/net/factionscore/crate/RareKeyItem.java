package net.factionscore.crate;

import org.powernukkitx.utils.TextFormat;

public final class RareKeyItem extends KeyItem {
    public static final String ID = "factionscore:key_rare";

    public RareKeyItem() {
        super(ID, "rare", TextFormat.AQUA + "" + TextFormat.BOLD + "Rare Crate Key");
    }
}
