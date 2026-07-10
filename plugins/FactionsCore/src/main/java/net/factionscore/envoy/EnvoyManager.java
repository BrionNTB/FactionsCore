package net.factionscore.envoy;

import net.factionscore.storage.Database;
import org.powernukkitx.Server;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.blockentity.BlockEntityChest;
import org.powernukkitx.item.Item;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.Position;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.plugin.Plugin;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Envoys: loot chests scatter across the warzone with their coordinates broadcast, pulling
 * everyone into the same area to fight over them -- the signature SaicoPvP-style event. Chests
 * are real chest blocks filled from a config loot table and swept back to air after a timeout.
 */
public final class EnvoyManager {

    private final Database database;
    private final Config config;
    private final Plugin plugin;
    private final List<Vector3> activeChests = new CopyOnWriteArrayList<>();
    private volatile Level activeLevel;

    public EnvoyManager(Database database, Config config, Plugin plugin) {
        this.database = database;
        this.config = config;
        this.plugin = plugin;
    }

    public void setCenter(Level level, double x, double y, double z, double radius) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO named_locations (name, level_name, x, y, z, radius) VALUES ('envoy', ?, ?, ?, ?, ?)
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

    private record Center(Level level, double x, double z, double radius) {
    }

    private Center loadCenter() {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT level_name, x, z, radius FROM named_locations WHERE name = 'envoy'")) {
            ResultSet rs = statement.executeQuery();
            if (!rs.next()) return null;
            Level level = Server.getInstance().getLevelByName(rs.getString("level_name"));
            if (level == null) return null;
            return new Center(level, rs.getDouble("x"), rs.getDouble("z"), rs.getDouble("radius"));
        } catch (SQLException e) {
            return null;
        }
    }

    public boolean hasCenter() {
        return loadCenter() != null;
    }

    /** @return number of chests dropped, or -1 if no center configured / an envoy is still active. */
    public int start() {
        if (!activeChests.isEmpty()) {
            return -1;
        }
        Center center = loadCenter();
        if (center == null) {
            return -1;
        }
        Level level = center.level();
        int count = config.getInt("envoy.count", 6);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> announced = new ArrayList<>();

        for (int i = 0; i < count * 4 && activeChests.size() < count; i++) {
            int x = (int) (center.x() + (random.nextDouble() * 2 - 1) * center.radius());
            int z = (int) (center.z() + (random.nextDouble() * 2 - 1) * center.radius());
            if (!level.isChunkGenerated(x >> 4, z >> 4)) continue;
            int y = level.getHighestBlockAt(x, z) + 1;
            Vector3 pos = new Vector3(x, y, z);
            if (!level.getBlock(pos).isAir()) continue;

            level.setBlock(pos, Block.get(BlockID.CHEST));
            BlockEntity blockEntity = BlockEntity.createBlockEntity(BlockEntity.CHEST,
                    Position.fromObject(pos, level),
                    BlockEntity.getDefaultCompound(pos, BlockEntity.CHEST));
            if (blockEntity instanceof BlockEntityChest chest) {
                fillLoot(chest);
            }
            activeChests.add(pos);
            announced.add(x + ", " + y + ", " + z);
        }

        if (activeChests.isEmpty()) {
            return 0;
        }
        activeLevel = level;

        Server.getInstance().broadcastMessage(TextFormat.GOLD + "[Envoy] " + TextFormat.YELLOW
                + activeChests.size() + " supply drops just landed! Coordinates:");
        for (String coordinate : announced) {
            Server.getInstance().broadcastMessage(TextFormat.GOLD + "[Envoy] " + TextFormat.AQUA + coordinate);
        }

        int despawnTicks = config.getInt("envoy.despawn-minutes", 10) * 60 * 20;
        Server.getInstance().getScheduler().scheduleDelayedTask(plugin, this::cleanup, despawnTicks);
        return activeChests.size();
    }

    private void fillLoot(BlockEntityChest chest) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int slot = 0;
        for (Map<?, ?> entry : config.getMapList("envoy.loot")) {
            Object id = entry.get("id");
            if (id == null) continue;
            double chance = entry.get("chance") instanceof Number n ? n.doubleValue() : 1.0;
            if (random.nextDouble() >= chance) continue;
            int count = entry.get("count") instanceof Number n ? n.intValue() : 1;
            Item item = Item.get(id.toString());
            item.setCount(count);
            if (!item.isNull() && slot < chest.getRealInventory().getSize()) {
                // Spread across random slots so chests don't all look identical.
                chest.getRealInventory().setItem(random.nextInt(chest.getRealInventory().getSize()), item);
                slot++;
            }
        }
    }

    public void cleanup() {
        Level level = activeLevel;
        if (level != null) {
            for (Vector3 pos : activeChests) {
                if (BlockID.CHEST.equals(level.getBlock(pos).getId())) {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        blockEntity.close();
                    }
                    level.setBlock(pos, Block.get(BlockID.AIR));
                }
            }
        }
        boolean any = !activeChests.isEmpty();
        activeChests.clear();
        activeLevel = null;
        if (any) {
            Server.getInstance().broadcastMessage(TextFormat.GOLD + "[Envoy] " + TextFormat.GRAY + "Remaining supply drops despawned.");
        }
    }
}
