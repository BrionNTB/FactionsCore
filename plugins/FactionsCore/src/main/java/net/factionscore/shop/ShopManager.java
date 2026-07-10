package net.factionscore.shop;

import net.factionscore.storage.Database;
import org.powernukkitx.Server;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.Location;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class ShopManager {

    private final Database database;

    public ShopManager(Database database) {
        this.database = database;
    }

    public Optional<Location> getShopLocation() {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT level_name, x, y, z, yaw, pitch FROM shop_location WHERE id = 1")) {
            ResultSet rs = statement.executeQuery();
            if (!rs.next()) {
                return Optional.empty();
            }
            Level level = Server.getInstance().getLevelByName(rs.getString("level_name"));
            if (level == null) {
                return Optional.empty();
            }
            return Optional.of(new Location(rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                    rs.getDouble("yaw"), rs.getDouble("pitch"), level));
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public void setShopLocation(Location location) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO shop_location (id, level_name, x, y, z, yaw, pitch) VALUES (1, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET level_name = excluded.level_name, x = excluded.x, y = excluded.y,
                    z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch
                """)) {
            statement.setString(1, location.getLevel().getName());
            statement.setDouble(2, location.x);
            statement.setDouble(3, location.y);
            statement.setDouble(4, location.z);
            statement.setDouble(5, location.yaw);
            statement.setDouble(6, location.pitch);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }
}
