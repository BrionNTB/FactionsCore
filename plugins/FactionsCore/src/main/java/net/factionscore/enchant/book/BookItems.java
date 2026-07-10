package net.factionscore.enchant.book;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Maps an enchant's short name to a fresh instance of its book item, for admin `/enchant givebook`. */
public final class BookItems {

    private static final Map<String, Supplier<CustomEnchantBookItem>> FACTORIES = Map.ofEntries(
            Map.entry("stun", (Supplier<CustomEnchantBookItem>) StunBookItem::new),
            Map.entry("tornado", TornadoBookItem::new),
            Map.entry("leech", LeechBookItem::new),
            Map.entry("blaze", BlazeBookItem::new),
            Map.entry("spring", SpringBookItem::new),
            Map.entry("haste", HasteBookItem::new),
            Map.entry("escape", EscapeBookItem::new),
            Map.entry("lightning", LightningBookItem::new),
            Map.entry("gravity", GravityBookItem::new),
            Map.entry("webber", WebberBookItem::new),
            Map.entry("protection", ProtectionBookItem::new),
            Map.entry("sharpness", SharpnessBookItem::new)
    );

    private BookItems() {
    }

    public static Optional<CustomEnchantBookItem> create(String enchantKey, int level) {
        Supplier<CustomEnchantBookItem> factory = FACTORIES.get(enchantKey.toLowerCase());
        if (factory == null) {
            return Optional.empty();
        }
        CustomEnchantBookItem book = factory.get();
        book.setLevel(level);
        return Optional.of(book);
    }
}
