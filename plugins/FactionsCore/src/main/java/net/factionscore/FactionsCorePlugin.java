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
import net.factionscore.enchant.EnchantApplyListener;
import net.factionscore.enchant.EnchantCombatListener;
import net.factionscore.enchant.EnchantCommand;
import net.factionscore.enchant.book.BlazeBookItem;
import net.factionscore.enchant.book.EscapeBookItem;
import net.factionscore.enchant.book.GravityBookItem;
import net.factionscore.enchant.book.HasteBookItem;
import net.factionscore.enchant.book.LeechBookItem;
import net.factionscore.enchant.book.LightningBookItem;
import net.factionscore.enchant.book.ProtectionBookItem;
import net.factionscore.enchant.book.SharpnessBookItem;
import net.factionscore.enchant.book.SpringBookItem;
import net.factionscore.enchant.book.StunBookItem;
import net.factionscore.enchant.book.TornadoBookItem;
import net.factionscore.enchant.book.WebberBookItem;
import net.factionscore.faction.FactionManager;
import net.factionscore.faction.FactionValueManager;
import net.factionscore.faction.FactionsAdminCommand;
import net.factionscore.faction.FactionsCommand;
import net.factionscore.faction.listener.ClaimProtectionListener;
import net.factionscore.faction.listener.CombatTuningListener;
import net.factionscore.faction.listener.SpawnerValueListener;
import net.factionscore.hopper.HopperTierListener;
import net.factionscore.hopper.ItemHopperTier2;
import net.factionscore.hopper.ItemHopperTier3;
import net.factionscore.shop.SellCommand;
import net.factionscore.shop.SellManager;
import net.factionscore.shop.SetShopCommand;
import net.factionscore.shop.ShopCommand;
import net.factionscore.shop.ShopManager;
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
        checkRedstoneSettings();

        try {
            database = new Database(getDataFolder(), getConfig().getString("storage.file", "factionscore.db"));
        } catch (SQLException e) {
            getLogger().critical("Failed to open FactionsCore database, disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        factionManager = new FactionManager(database, getConfig());
        FactionValueManager factionValueManager = new FactionValueManager(database, factionManager, getConfig());
        net.factionscore.economy.EconomyManager economyManager = new net.factionscore.economy.EconomyManager(database);

        net.factionscore.faction.listener.FactionChatListener factionChatListener =
                new net.factionscore.faction.listener.FactionChatListener(factionManager);
        getServer().getCommandMap().register("factionscore", new FactionsCommand(factionManager, factionValueManager, factionChatListener));
        getServer().getCommandMap().register("factionscore", new FactionsAdminCommand(factionManager));

        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(factionManager, getConfig()), this);
        getServer().getPluginManager().registerEvents(new CombatTuningListener(getConfig()), this);
        getServer().getPluginManager().registerEvents(new SpawnerValueListener(factionManager, factionValueManager), this);
        getServer().getPluginManager().registerEvents(factionChatListener, this);

        int regenIntervalTicks = 20 * 60 * 60; // once per in-game hour, matches power.regen-per-hour semantics
        getServer().getScheduler().scheduleRepeatingTask(this, () -> factionManager.applyPowerRegenTick(), regenIntervalTicks, true);

        enchantRegistry = new CustomEnchantRegistry();
        enchantRegistry.registerAll(getConfig().getStringList("enchants.enabled"));
        getServer().getCommandMap().register("factionscore", new EnchantCommand(enchantRegistry));
        getServer().getPluginManager().registerEvents(new EnchantCombatListener(enchantRegistry), this);
        getServer().getPluginManager().registerEvents(new EnchantApplyListener(enchantRegistry, getConfig()), this);
        try {
            org.powernukkitx.registry.Registries.ITEM.registerCustomItem(this,
                    StunBookItem.class, TornadoBookItem.class, LeechBookItem.class, BlazeBookItem.class,
                    SpringBookItem.class, HasteBookItem.class, EscapeBookItem.class, LightningBookItem.class,
                    GravityBookItem.class, WebberBookItem.class, ProtectionBookItem.class, SharpnessBookItem.class);
        } catch (org.powernukkitx.registry.RegisterException e) {
            getLogger().warning("Failed to register custom enchant book items: " + e.getMessage());
        }

        MobStackManager mobStackManager = new MobStackManager(getConfig());
        SpawnerStackManager spawnerStackManager = new SpawnerStackManager(getConfig());
        getServer().getPluginManager().registerEvents(new StackingListener(mobStackManager, spawnerStackManager, factionManager, factionValueManager), this);
        getServer().getCommandMap().register("factionscore", new StackCommand());

        try {
            org.powernukkitx.registry.Registries.ITEM.registerCustomItem(this, ItemHopperTier2.class, ItemHopperTier3.class);
        } catch (org.powernukkitx.registry.RegisterException e) {
            getLogger().warning("Failed to register custom hopper items: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(new HopperTierListener(), this);

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

        ShopManager shopManager = new ShopManager(database);
        getServer().getCommandMap().register("factionscore", new ShopCommand(shopManager));
        getServer().getCommandMap().register("factionscore", new SetShopCommand(shopManager));

        SellManager sellManager = new SellManager(database, getConfig(), economyManager);
        getServer().getCommandMap().register("factionscore", new SellCommand(sellManager));
        int fluctuateTicks = getConfig().getInt("sell.demand.fluctuate-hours", 4) * 60 * 60 * 20;
        getServer().getScheduler().scheduleRepeatingTask(this, sellManager::fluctuateDemand, fluctuateTicks, true);

        getServer().getCommandMap().register("factionscore", new net.factionscore.economy.BalanceCommand(economyManager));
        getServer().getCommandMap().register("factionscore", new net.factionscore.economy.PayCommand(economyManager));
        getServer().getCommandMap().register("factionscore", new net.factionscore.economy.BalTopCommand(economyManager));
        getServer().getCommandMap().register("factionscore", new net.factionscore.misc.WildCommand(factionManager, worldBorderManager, getConfig()));

        net.factionscore.kit.KitManager kitManager = new net.factionscore.kit.KitManager(database, getConfig());
        getServer().getCommandMap().register("factionscore", new net.factionscore.kit.KitCommand(kitManager));

        net.factionscore.bounty.BountyManager bountyManager = new net.factionscore.bounty.BountyManager(database, economyManager);
        getServer().getCommandMap().register("factionscore", new net.factionscore.bounty.BountyCommand(bountyManager));
        getServer().getPluginManager().registerEvents(new net.factionscore.bounty.BountyListener(bountyManager), this);

        net.factionscore.koth.KothManager kothManager = new net.factionscore.koth.KothManager(database, factionManager, economyManager, getConfig());
        getServer().getCommandMap().register("factionscore", new net.factionscore.koth.KothCommand(kothManager));
        getServer().getScheduler().scheduleRepeatingTask(this, kothManager::tick, 20, false);

        net.factionscore.envoy.EnvoyManager envoyManager = new net.factionscore.envoy.EnvoyManager(database, getConfig(), this);
        getServer().getCommandMap().register("factionscore", new net.factionscore.envoy.EnvoyCommand(envoyManager));
        int envoyIntervalHours = getConfig().getInt("envoy.auto-interval-hours", 0);
        if (envoyIntervalHours > 0) {
            getServer().getScheduler().scheduleRepeatingTask(this, envoyManager::start, envoyIntervalHours * 60 * 60 * 20, false);
        }

        net.factionscore.crate.CrateManager crateManager = new net.factionscore.crate.CrateManager(database, getConfig());
        getServer().getCommandMap().register("factionscore", new net.factionscore.crate.CrateCommand(crateManager));
        getServer().getPluginManager().registerEvents(new net.factionscore.crate.CrateListener(crateManager), this);

        getServer().getPluginManager().registerEvents(new net.factionscore.genbucket.GenBucketListener(factionManager, getConfig(), this), this);
        getServer().getPluginManager().registerEvents(new net.factionscore.shop.SellWandListener(sellManager), this);
        getServer().getCommandMap().register("factionscore", new net.factionscore.shop.SellWandCommand());
        getServer().getPluginManager().registerEvents(new net.factionscore.raid.ObsidianBreakListener(getConfig()), this);

        try {
            org.powernukkitx.registry.Registries.ITEM.registerCustomItem(this,
                    net.factionscore.crate.CommonKeyItem.class,
                    net.factionscore.crate.RareKeyItem.class,
                    net.factionscore.crate.LegendaryKeyItem.class,
                    net.factionscore.genbucket.CobbleGenBucketItem.class,
                    net.factionscore.genbucket.ObsidianGenBucketItem.class,
                    net.factionscore.genbucket.SandGenBucketItem.class,
                    net.factionscore.shop.SellWandItem.class);
        } catch (org.powernukkitx.registry.RegisterException e) {
            getLogger().warning("Failed to register crate/genbucket/sellwand items: " + e.getMessage());
        }

        if (getConfig().getBoolean("buycraft.enabled", false)) {
            BuycraftIntegration buycraft = new BuycraftIntegration(getConfig(), getLogger());
            int pollTicks = getConfig().getInt("buycraft.poll-interval-seconds", 60) * 20;
            getServer().getScheduler().scheduleRepeatingTask(this, buycraft::pollAndExecute, pollTicks, true);
        }

        getLogger().info("FactionsCore enabled.");
    }

    /**
     * TNT cannons, traps, farms -- redstone is core to a factions server, and "redstone doesn't
     * work" reports are far more often a disabled gameplay setting than an engine bug (verified
     * by reviewing the wire/torch/repeater/comparator/observer/piston implementations, which are
     * faithful vanilla ports). Surface any misconfiguration loudly at startup instead of letting
     * it silently look like broken redstone.
     */
    private void checkRedstoneSettings() {
        var settings = getServer().getSettings().gameplaySettings();
        if (!settings.enableRedstone()) {
            getLogger().warning(org.powernukkitx.utils.TextFormat.RED
                    + "gameplay-settings.enable-redstone is FALSE -- every redstone component (wire, "
                    + "repeaters, pistons, TNT triggers, ...) is inert. This is almost certainly not what "
                    + "you want on a factions server; enable it in the server's settings file.");
        }
        if (!settings.tickRedstone()) {
            getLogger().warning(org.powernukkitx.utils.TextFormat.RED
                    + "gameplay-settings.tick-redstone is FALSE -- redstone components are frozen and will "
                    + "not respond to signal changes.");
        }
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
