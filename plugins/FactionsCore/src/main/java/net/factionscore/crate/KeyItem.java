package net.factionscore.crate;

import org.powernukkitx.item.Item;
import org.powernukkitx.item.customitem.CustomItem;
import org.powernukkitx.item.customitem.CustomItemDefinition;
import org.powernukkitx.item.customitem.data.CreativeCategory;
import org.powernukkitx.utils.TextFormat;

/** A crate key: glinting custom item, one id per crate tier, redeemed by right-clicking the matching crate chest. */
public abstract class KeyItem extends Item implements CustomItem {

    private final String crateType;
    private final String displayName;

    protected KeyItem(String itemId, String crateType, String displayName) {
        super(itemId);
        this.crateType = crateType;
        this.displayName = displayName;
        this.name = displayName;
        setLore(
                TextFormat.GRAY + "Unlocks one roll of the " + TextFormat.BOLD + crateType + TextFormat.RESET + TextFormat.GRAY + " crate.",
                TextFormat.DARK_GRAY + "" + TextFormat.ITALIC + "Right-click the crate chest while holding this key.");
    }

    public String getCrateType() {
        return crateType;
    }

    @Override
    public CustomItemDefinition getDefinition() {
        return CustomItemDefinition.customBuilder(this)
                .name(displayName)
                .texture("tripwire_hook")
                .allowOffHand(false)
                .creativeCategory(CreativeCategory.ITEMS)
                .glint(true)
                .maxStackSize(64)
                .build();
    }
}
