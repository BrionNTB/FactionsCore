package net.factionscore.schematic;

import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.blockentity.BlockEntityDispenser;
import org.powernukkitx.item.Item;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.Position;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.plugin.PluginBase;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads {@code .mcstructure} files from the plugin's {@code schematics/} folder so cannons (or any
 * build) can be pasted with a command instead of rebuilt by hand after every test shot. Bundled
 * schematics ship inside the plugin jar and are extracted on first start; server owners can drop
 * more exported .mcstructure files into the folder and {@code /schem reload} picks them up.
 */
public final class SchematicManager {

    private final PluginBase plugin;
    private final Map<String, McStructure> schematics = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public SchematicManager(PluginBase plugin) {
        this.plugin = plugin;
        for (String bundled : new String[]{"schematics/stacker20.mcstructure", "schematics/testcannon.mcstructure",
                "schematics/heavycannon.mcstructure",
                // Faction Schematic Pack cannons (converted from Java litematics):
                // ef = Efficient Fusion, ls = Left Shooter, rh = Reverse Hybrid; 80/160/255 stacker sizes
                "schematics/ef80.mcstructure", "schematics/ef160.mcstructure", "schematics/ef255.mcstructure",
                "schematics/ls80.mcstructure", "schematics/ls160.mcstructure", "schematics/ls255.mcstructure",
                "schematics/rh80.mcstructure", "schematics/rh160.mcstructure", "schematics/rh255.mcstructure"}) {
            plugin.saveResource(bundled);
        }
        reload();
    }

    public void reload() {
        schematics.clear();
        File dir = new File(plugin.getDataFolder(), "schematics");
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".mcstructure"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - ".mcstructure".length());
            try {
                McStructure structure = McStructure.load(file);
                schematics.put(name, structure);
                if (!structure.unresolvedBlocks().isEmpty()) {
                    plugin.getLogger().warning("Schematic " + name + " has unresolvable blocks (pasted as air): "
                            + structure.unresolvedBlocks());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not load schematic " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public Map<String, McStructure> all() {
        return schematics;
    }

    public McStructure get(String name) {
        return schematics.get(name);
    }

    /** Structural materials that {@code /schem paste <name> <material>} swaps for the requested block. */
    private static final java.util.Set<String> FRAME_MATERIALS = java.util.Set.of(
            BlockID.BEDROCK, BlockID.OBSIDIAN, BlockID.COBBLESTONE);

    /**
     * Pastes the structure with its minimum corner at {@code corner}, in the orientation it was
     * saved in (+X east, +Z south). Bottom-up so nothing pops off missing supports, no block
     * updates during the paste so the redstone lands in its saved resting state, and every
     * dispenser is topped up with TNT so a pasted cannon is ready to fire.
     *
     * @param frameMaterial optional block to substitute for the structure's frame blocks
     *                      (bedrock/obsidian/cobblestone), so a cannon can be tested in the
     *                      material players will actually build with; null = paste as saved
     * @return number of blocks placed
     */
    public int paste(McStructure structure, Level level, Vector3 corner, boolean fillDispensers, Block frameMaterial) {
        int placed = 0;
        int baseX = corner.getFloorX();
        int baseY = corner.getFloorY();
        int baseZ = corner.getFloorZ();
        for (int y = 0; y < structure.sizeY(); y++) {
            for (int x = 0; x < structure.sizeX(); x++) {
                for (int z = 0; z < structure.sizeZ(); z++) {
                    Block block = structure.blockAt(x, y, z);
                    if (block == null) {
                        continue;
                    }
                    if (frameMaterial != null && FRAME_MATERIALS.contains(block.getId())) {
                        block = frameMaterial;
                    }
                    Vector3 pos = new Vector3(baseX + x, baseY + y, baseZ + z);
                    level.setBlock(pos, block.clone(), false, false);
                    placed++;
                    if (fillDispensers && BlockID.DISPENSER.equals(block.getId())) {
                        fillDispenser(level, pos);
                    }
                }
            }
        }
        return placed;
    }

    private void fillDispenser(Level level, Vector3 pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            entity = BlockEntity.createBlockEntity(BlockEntity.DISPENSER, Position.fromObject(pos, level));
        }
        if (entity instanceof BlockEntityDispenser dispenser) {
            for (int slot = 0; slot < dispenser.getInventory().getSize(); slot++) {
                dispenser.getInventory().setItem(slot, Item.get(BlockID.TNT, 0, 64));
            }
        }
    }
}
