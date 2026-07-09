package net.factionscore.anticheat;

import net.factionscore.anticheat.check.AimCheck;
import net.factionscore.anticheat.check.AutoClickerCheck;
import net.factionscore.anticheat.check.XrayCheck;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.utils.Config;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiCheatManager {

    private final Map<UUID, PlayerCheckData> data = new ConcurrentHashMap<>();
    private final AutoClickerCheck autoClickerCheck;
    private final XrayCheck xrayCheck;
    private final AimCheck aimCheck;
    private final PunishmentManager punishmentManager;

    public AntiCheatManager(Config config, PunishmentManager punishmentManager) {
        this.autoClickerCheck = new AutoClickerCheck(config);
        this.xrayCheck = new XrayCheck(config);
        this.aimCheck = new AimCheck(config);
        this.punishmentManager = punishmentManager;
    }

    public PlayerCheckData dataFor(Player player) {
        return data.computeIfAbsent(player.getUniqueId(), id -> new PlayerCheckData());
    }

    public void forget(Player player) {
        data.remove(player.getUniqueId());
    }

    public void onSwing(Player player) {
        if (player.hasPermission("factionscore.bypass.anticheat")) return;
        PlayerCheckData playerData = dataFor(player);
        playerData.recordClick(System.currentTimeMillis());
        var result = autoClickerCheck.evaluate(playerData);
        if (result.flagged()) {
            punishmentManager.recordFlag(player, "autoclicker", result.detail());
            playerData.clickTimestamps.clear();
        }
    }

    public void onBlockBreak(Player player, Block block) {
        if (player.hasPermission("factionscore.bypass.anticheat")) return;
        PlayerCheckData playerData = dataFor(player);
        var result = xrayCheck.evaluate(playerData, block, System.currentTimeMillis());
        if (result.flagged()) {
            punishmentManager.recordFlag(player, "xray", result.detail());
        }
    }

    public void onAimSample(Player player, double yawDelta, double pitchDelta) {
        if (player.hasPermission("factionscore.bypass.anticheat")) return;
        PlayerCheckData playerData = dataFor(player);
        playerData.recordAimDelta(yawDelta, pitchDelta);
        var result = aimCheck.evaluate(playerData);
        if (result.flagged()) {
            punishmentManager.recordFlag(player, "aim", result.detail());
            playerData.aimDeltas.clear();
        }
    }

    public void markInCombat(Player player) {
        dataFor(player).lastCombatAt = System.currentTimeMillis();
    }

    public boolean isInCombat(Player player, long graceMs) {
        PlayerCheckData playerData = data.get(player.getUniqueId());
        return playerData != null && (System.currentTimeMillis() - playerData.lastCombatAt) <= graceMs;
    }
}
