package net.factionscore.koth;

import net.factionscore.economy.EconomyManager;
import net.factionscore.faction.FactionManager;
import net.factionscore.storage.Database;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.level.Level;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * King of the Hill: admins define a capture zone, an event is started, and the first player to
 * hold the zone uncontested for the configured capture time wins the pot. Contested (players from
 * different factions inside at once) pauses progress; leaving the zone resets it -- the classic
 * ruleset that forces a fight over the point rather than a race to tag it.
 */
public final class KothManager {

    private record Zone(Level level, double x, double y, double z, double radius) {
    }

    private final Database database;
    private final FactionManager factions;
    private final EconomyManager economy;
    private final Config config;

    private volatile boolean active;
    private UUID controller;
    private int progressSeconds;

    public KothManager(Database database, FactionManager factions, EconomyManager economy, Config config) {
        this.database = database;
        this.factions = factions;
        this.economy = economy;
        this.config = config;
    }

    public boolean isActive() {
        return active;
    }

    public void setZone(Level level, double x, double y, double z, double radius) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO named_locations (name, level_name, x, y, z, radius) VALUES ('koth', ?, ?, ?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET level_name = excluded.level_name, x = excluded.x,
                    y = excluded.y, z = excluded.z, radius = excluded.radius
                """)) {
            statement.setString(1, level.getName());
            statement.setDouble(2, x);
            statement.setDouble(3, y);
            statement.setDouble(4, z);
            statement.setDouble(5, radius);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private Zone loadZone() {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT level_name, x, y, z, radius FROM named_locations WHERE name = 'koth'")) {
            ResultSet rs = statement.executeQuery();
            if (!rs.next()) return null;
            Level level = Server.getInstance().getLevelByName(rs.getString("level_name"));
            if (level == null) return null;
            return new Zone(level, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getDouble("radius"));
        } catch (SQLException e) {
            return null;
        }
    }

    public boolean hasZone() {
        return loadZone() != null;
    }

    public boolean start() {
        if (active || !hasZone()) {
            return false;
        }
        active = true;
        controller = null;
        progressSeconds = 0;
        int captureSeconds = config.getInt("koth.capture-seconds", 300);
        Server.getInstance().broadcastMessage(TextFormat.GOLD + "[KOTH] " + TextFormat.YELLOW
                + "The hill is live! Hold it uncontested for " + (captureSeconds / 60) + "m " + (captureSeconds % 60)
                + "s to win " + TextFormat.GREEN + EconomyManager.format(config.getDouble("koth.reward-money", 10000)) + TextFormat.YELLOW + "!");
        return true;
    }

    public void stop(boolean announce) {
        if (!active) return;
        active = false;
        controller = null;
        progressSeconds = 0;
        if (announce) {
            Server.getInstance().broadcastMessage(TextFormat.GOLD + "[KOTH] " + TextFormat.YELLOW + "The event was cancelled.");
        }
    }

    public String status() {
        if (!active) return "No KOTH event is running.";
        if (controller == null) return "The hill is unclaimed!";
        Player player = Server.getInstance().getOnlinePlayers().get(controller);
        String name = player != null ? player.getName() : "someone";
        int captureSeconds = config.getInt("koth.capture-seconds", 300);
        return name + " is capturing: " + progressSeconds + "/" + captureSeconds + "s";
    }

    /** Called once per second by the plugin's scheduler while the server runs; no-op when inactive. */
    public void tick() {
        if (!active) return;
        Zone zone = loadZone();
        if (zone == null) {
            stop(false);
            return;
        }

        List<Player> inside = new ArrayList<>();
        double radiusSq = zone.radius() * zone.radius();
        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            if (!player.getLevel().getName().equals(zone.level().getName())) continue;
            double dx = player.x - zone.x();
            double dz = player.z - zone.z();
            if (dx * dx + dz * dz <= radiusSq && Math.abs(player.y - zone.y()) <= zone.radius() + 5) {
                inside.add(player);
            }
        }

        if (inside.isEmpty()) {
            if (controller != null) {
                controller = null;
                progressSeconds = 0;
            }
            return;
        }

        boolean contested = isContested(inside);
        if (contested) {
            return; // progress freezes while fought over
        }

        Player holder = inside.get(0);
        if (controller == null || !controller.equals(holder.getUniqueId())) {
            controller = holder.getUniqueId();
            progressSeconds = 0;
            Server.getInstance().broadcastMessage(TextFormat.GOLD + "[KOTH] " + TextFormat.YELLOW + holder.getName() + " is capturing the hill!");
        }

        progressSeconds++;
        int captureSeconds = config.getInt("koth.capture-seconds", 300);
        if (progressSeconds % 60 == 0 && progressSeconds < captureSeconds) {
            holder.sendPopup(TextFormat.GOLD + "KOTH: " + progressSeconds + "/" + captureSeconds + "s");
        }
        if (progressSeconds >= captureSeconds) {
            win(holder);
        }
    }

    private boolean isContested(List<Player> inside) {
        if (inside.size() <= 1) return false;
        String firstFaction = factions.getByMember(inside.get(0).getUniqueId()).map(f -> f.getId()).orElse(null);
        for (int i = 1; i < inside.size(); i++) {
            String otherFaction = factions.getByMember(inside.get(i).getUniqueId()).map(f -> f.getId()).orElse(null);
            if (firstFaction == null || !firstFaction.equals(otherFaction)) {
                return true;
            }
        }
        return false;
    }

    private void win(Player winner) {
        active = false;
        controller = null;
        progressSeconds = 0;

        double money = config.getDouble("koth.reward-money", 10000);
        economy.deposit(winner.getUniqueId(), money);
        Server.getInstance().broadcastMessage(TextFormat.GOLD + "[KOTH] " + TextFormat.GREEN + winner.getName()
                + " captured the hill and won " + EconomyManager.format(money) + "!");

        for (String command : config.getStringList("koth.reward-commands")) {
            String resolved = command.replace("{player}", winner.getName());
            try {
                Server.getInstance().executeCommand(Server.getInstance().getConsoleSender(), resolved);
            } catch (Exception ignored) {
            }
        }
    }
}
