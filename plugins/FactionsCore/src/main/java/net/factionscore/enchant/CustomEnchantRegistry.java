package net.factionscore.enchant;

import net.factionscore.enchant.impl.BlazeEnchant;
import net.factionscore.enchant.impl.EscapeEnchant;
import net.factionscore.enchant.impl.GravityEnchant;
import net.factionscore.enchant.impl.HasteEnchant;
import net.factionscore.enchant.impl.LeechEnchant;
import net.factionscore.enchant.impl.LightningEnchant;
import net.factionscore.enchant.impl.ProtectionEnchant;
import net.factionscore.enchant.impl.SharpnessEnchant;
import net.factionscore.enchant.impl.SpringEnchant;
import net.factionscore.enchant.impl.StunEnchant;
import net.factionscore.enchant.impl.TornadoEnchant;
import net.factionscore.enchant.impl.WebberEnchant;
import org.powernukkitx.item.enchantment.Enchantment;
import org.powernukkitx.utils.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CustomEnchantRegistry {

    private final Map<String, CustomEnchant> byShortName = new LinkedHashMap<>();

    public void registerAll(java.util.List<String> enabled) {
        Map<String, CustomEnchant> all = new LinkedHashMap<>();
        all.put("stun", new StunEnchant());
        all.put("tornado", new TornadoEnchant());
        all.put("leech", new LeechEnchant());
        all.put("blaze", new BlazeEnchant());
        all.put("spring", new SpringEnchant());
        all.put("haste", new HasteEnchant());
        all.put("escape", new EscapeEnchant());
        all.put("lightning", new LightningEnchant());
        all.put("gravity", new GravityEnchant());
        all.put("webber", new WebberEnchant());
        all.put("protection", new ProtectionEnchant());
        all.put("sharpness", new SharpnessEnchant());

        for (var entry : all.entrySet()) {
            if (!enabled.isEmpty() && !enabled.contains(entry.getKey())) {
                continue;
            }
            // registerItem=false: FactionsCore ships its own book items (enchant/book/) with a
            // reliable level stored in NBT instead of the engine's auto-generated per-level books,
            // whose id-encodes-level scheme (e.g. "factionscore:stun3") doesn't round-trip back
            // through Enchantment.getEnchantment(String) since that looks up the bare identifier.
            var result = Enchantment.register(entry.getValue(), false);
            if (result.ok()) {
                byShortName.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public Optional<CustomEnchant> get(String shortName) {
        return Optional.ofNullable(byShortName.get(shortName.toLowerCase()));
    }

    public Map<String, CustomEnchant> all() {
        return byShortName;
    }

    public Identifier identifierOf(String shortName) {
        return new Identifier(CustomEnchant.NAMESPACE, shortName.toLowerCase());
    }
}
