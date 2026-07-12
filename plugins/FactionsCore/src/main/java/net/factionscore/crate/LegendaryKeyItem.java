package net.factionscore.crate;

import org.powernukkitx.utils.TextFormat;

public final class LegendaryKeyItem extends KeyItem {
    public static final String ID = "factionscore:key_legendary";

    public LegendaryKeyItem() {
        super(ID, "legendary", TextFormat.LIGHT_PURPLE + "" + TextFormat.BOLD + "Legendary Crate Key");
    }
}
