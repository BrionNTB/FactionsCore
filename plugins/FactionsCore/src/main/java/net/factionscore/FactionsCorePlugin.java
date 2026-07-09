package net.factionscore;

import net.factionscore.anticheat.AntiCheatCommand;
import net.factionscore.anticheat.AntiCheatListener;
import net.factionscore.anticheat.AntiCheatManager;
import net.factionscore.anticheat.PunishmentManager;
import net.factionscore.border.BorderCommand;
import net.factionscore.border.BorderListener;
import net.factionscore.border.WorldBorderManager;
import net.factionscore.buycraft.BuycraftIntegration;
import net.factionscore.combat.CombatLogListener;
import net.factionscore.combat.CombatTagManager;
import net.factionscore.enchant.CustomEnchantRegistry;
import net.factionscore.enchant.EnchantCombatListener;
import net.factionscore.enchant.EnchantCommand;
import net.factionscore.faction.FactionManager;
import net.factionscore.faction.FactionValueManager;
import net.factionscore.faction.FactionsAdminCommand;
import net.factionscore.faction.FactionsCommand;
import net.factionscore.faction.listener.ClaimProtectionListener;
import net.factionscore.faction.listener.CombatTuningListener;
import net.factionscore.faction.listener.SpawnerValueListener;
import net.factionscore.stacking.MobStackManager;
import net.factionscore.stacking.SpawnerStackManager;
import net.factionscore.stacking.StackCommand;
import net.factionscore.stacking.StackingListener;
import net.factionscore.storage.Database;
import net.factionscore.updater.AutoUpdater;
import org.powernukkitx.plugin.PluginBase;

import java.sql.SQLException;

public final class FactionsCorePlugin extends PluginBase {

    private Database database;
    private FactionManager factionManager;
    private CustomEnchantRegistry enchantRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            database = new Database(getDataFolder(), getConfig().getString("storage.file", "factionscore.db"));
        } catch (SQLException e) {
            getLogger().critical("Failed to open FactionsCore database, disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        factionManager = new FactionManager(database, getConfig());
        FactionValueManager factionValueManager = new FactionValueManager(database, factionManager, getConfig());

        getServer().getCommandMap().register("factionscore", new FactionsCommand(factionManager, factionValueManager));
        getServer().getCommandMap().register("factionscore", new FactionsAdminCommand(factionManager));

        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(factionManager, getConfig()), this);
        getServer().getPluginManager().registerEvents(new CombatTuningListener(getConfig()), this);
        getServer().getPluginManager().registerEvents(new SpawnerValueListener(factionManager, factionValueManager), this);

        int regenIntervalTicks = 20 * 60 * 60; // once per in-game hour, matches power.regen-per-hour semantics
        getServer().getScheduler().scheduleRepeatingTask(this, () -> factionManager.applyPowerRegenTick(), regenIntervalTicks, true);

        enchantRegistry = new CustomEnchantRegistry();
        enchantRegistry.registerAll(getConfig().getStringList("enchants.enabled"));
        getServer().getCommandMap().register("factionscore", new EnchantCommand(enchantRegistry));
        getServer().getPluginManager().registerEvents(new EnchantCombatListener(enchantRegistry), this);

        MobStackManager mobStackManager = new MobStackManager(getConfig());
        SpawnerStackManager spawnerStackManager = new SpawnerStackManager(getConfig());
        getServer().getPluginManager().registerEvents(new StackingListener(mobStackManager, spawnerStackManager, factionManager, factionValueManager), this);
        getServer().getCommandMap().register("factionscore", new StackCommand());

        CombatTagManager combatTagManager = new CombatTagManager(getConfig());
        getServer().getPluginManager().registerEvents(new CombatLogListener(combatTagManager, factionManager, getConfig()), this);

        WorldBorderManager worldBorderManager = new WorldBorderManager(getConfig());
        BorderListener borderListener = new BorderListener(worldBorderManager, combatTagManager, getConfig());
        getServer().getPluginManager().registerEvents(borderListener, this);
        getServer().getCommandMap().register("factionscore", new BorderCommand(worldBorderManager));
        int borderSweepTicks = getConfig().getInt("border.check-interval-seconds", 5) * 20;
        // Touches live Player/Level state (teleport), so this must stay on the main thread.
        getServer().getScheduler().scheduleRepeatingTask(this, borderListener::sweepAll, borderSweepTicks, false);

        if (getConfig().getBoolean("anticheat.enabled", true)) {
            PunishmentManager punishmentManager = new PunishmentManager(database, getConfig());
            AntiCheatManager antiCheatManager = new AntiCheatManager(getConfig(), punishmentManager);
            getServer().getPluginManager().registerEvents(new AntiCheatListener(antiCheatManager), this);
            getServer().getCommandMap().register("factionscore", new AntiCheatCommand(database));
        }

        if (getConfig().getBoolean("updater.enabled", true)) {
            AutoUpdater updater = new AutoUpdater(getConfig(), getLogger(), getDataFolder());
            int intervalTicks = getConfig().getInt("updater.check-interval-minutes", 60) * 60 * 20;
            getServer().getScheduler().scheduleDelayedRepeatingTask(this, updater::checkAndMaybeApply, intervalTicks, intervalTicks, true);
        }

        if (getConfig().getBoolean("buycraft.enabled", false)) {
            BuycraftIntegration buycraft = new BuycraftIntegration(getConfig(), getLogger());
            int pollTicks = getConfig().getInt("buycraft.poll-interval-seconds", 60) * 20;
            getServer().getScheduler().scheduleRepeatingTask(this, buycraft::pollAndExecute, pollTicks, true);
        }

        getLogger().info("FactionsCore enabled.");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
    }

    public FactionManager getFactionManager() {
        return factionManager;
    }
}
