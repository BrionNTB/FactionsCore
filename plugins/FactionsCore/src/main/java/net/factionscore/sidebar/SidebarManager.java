package net.factionscore.sidebar;

import net.factionscore.economy.EconomyManager;
import net.factionscore.faction.Faction;
import net.factionscore.faction.FactionManager;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.scoreboard.Scoreboard;
import org.powernukkitx.scoreboard.data.DisplaySlot;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player sidebar showing balance and faction status, refreshed on a timer. Each player gets
 * their own Scoreboard object with only themselves as viewer -- the engine's scoreboard lines are
 * global per board, so per-player values require per-player boards.
 */
public final class SidebarManager {

    private final FactionManager factions;
    private final EconomyManager economy;
    private final net.factionscore.misc.WarmupManager warmups;
    private final Config config;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public SidebarManager(FactionManager factions, EconomyManager economy,
                          net.factionscore.misc.WarmupManager warmups, Config config) {
        this.factions = factions;
        this.economy = economy;
        this.warmups = warmups;
        this.config = config;
    }

    public boolean isEnabled() {
        return config.getBoolean("sidebar.enabled", true);
    }

    public int updateSeconds() {
        // 1s so teleport countdowns tick down live in the sidebar.
        return Math.max(1, config.getInt("sidebar.update-seconds", 1));
    }

    /** Runs on the main thread on a repeating task. */
    public void refreshAll() {
        if (!isEnabled()) return;
        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            refresh(player);
        }
    }

    private void refresh(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard created = new Scoreboard("fc_" + id.toString().substring(0, 8),
                    TextFormat.BOLD + "" + TextFormat.DARK_RED + "Wicked" + TextFormat.GOLD + "Raids");
            created.addViewer(player, DisplaySlot.SIDEBAR);
            return created;
        });

        List<String> lines = new ArrayList<>();
        String countdown = warmups.countdownLine(player.getUniqueId());
        if (countdown != null) {
            lines.add(countdown);
        }
        lines.add(TextFormat.GRAY + "");
        lines.add(TextFormat.GOLD + "" + TextFormat.BOLD + "Balance");
        lines.add(TextFormat.GREEN + " " + EconomyManager.format(economy.getBalance(player.getUniqueId())));
        lines.add(TextFormat.YELLOW + "");
        Faction faction = factions.getByMember(player.getUniqueId()).orElse(null);
        lines.add(TextFormat.GOLD + "" + TextFormat.BOLD + "Faction");
        if (faction != null) {
            lines.add(TextFormat.AQUA + " " + faction.getName());
            lines.add(TextFormat.GRAY + " Power: " + TextFormat.RED + String.format("%.1f", faction.getPower()));
            lines.add(TextFormat.GRAY + " Members: " + TextFormat.WHITE + faction.members().size());
        } else {
            lines.add(TextFormat.GRAY + "" + TextFormat.ITALIC + " none -- /f create");
        }
        lines.add(TextFormat.DARK_GRAY + " ");
        board.setLines(lines);
    }

    public void forget(Player player) {
        Scoreboard board = boards.remove(player.getUniqueId());
        if (board != null) {
            board.removeViewer(player, DisplaySlot.SIDEBAR);
        }
    }
}
