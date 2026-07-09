package net.factionscore.enchant;

import net.factionscore.enchant.impl.BlazeEnchant;
import net.factionscore.enchant.impl.EscapeEnchant;
import net.factionscore.enchant.impl.GravityEnchant;
import net.factionscore.enchant.impl.HasteEnchant;
import net.factionscore.enchant.impl.LeechEnchant;
import net.factionscore.enchant.impl.LightningEnchant;
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

        for (var entry : all.entrySet()) {
            if (!enabled.isEmpty() && !enabled.contains(entry.getKey())) {
                continue;
            }
            var result = Enchantment.register(entry.getValue(), true);
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
