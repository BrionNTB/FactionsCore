package net.factionscore.schematic;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockState;
import org.powernukkitx.registry.Registries;
import org.powernukkitx.utils.HashUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * A parsed Bedrock {@code .mcstructure} file (the format structure blocks export). Uncompressed
 * little-endian NBT: {@code size} [x,y,z], {@code structure.block_indices} (two layers of palette
 * indices ordered x-major/z-fastest, -1 = leave world untouched) and
 * {@code structure.palette.default.block_palette} entries of name + states. Palette entries are
 * resolved to engine blocks through the same name+states FNV hash the chunk format uses, so any
 * vanilla block a structure block can save pastes back exactly -- repeater delays, piston facings
 * and all.
 */
public final class McStructure {

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int[] indices;
    private final Block[] palette;
    private final Set<String> unresolved = new LinkedHashSet<>();

    private McStructure(int sizeX, int sizeY, int sizeZ, int[] indices, Block[] palette) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.indices = indices;
        this.palette = palette;
    }

    public static McStructure load(File file) throws IOException {
        NbtMap root;
        try (var reader = NbtUtils.createReaderLE(new BufferedInputStream(new FileInputStream(file)))) {
            root = (NbtMap) reader.readTag();
        }
        List<Integer> size = root.getList("size", NbtType.INT);
        NbtMap structure = root.getCompound("structure");
        @SuppressWarnings("unchecked")
        List<List<Integer>> layers = (List<List<Integer>>) (List<?>) structure.getList("block_indices", NbtType.LIST);
        List<Integer> layer0 = layers.get(0);
        int[] indices = new int[layer0.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = layer0.get(i);
        }

        List<NbtMap> paletteNbt = structure.getCompound("palette").getCompound("default")
                .getList("block_palette", NbtType.COMPOUND);
        Block[] palette = new Block[paletteNbt.size()];
        McStructure result = new McStructure(size.get(0), size.get(1), size.get(2), indices, palette);
        for (int i = 0; i < paletteNbt.size(); i++) {
            NbtMap entry = paletteNbt.get(i);
            String name = entry.getString("name");
            // Same hashing scheme the chunk storage uses: name + alphabetically sorted states.
            NbtMap hashed = NbtMap.builder()
                    .putString("name", name)
                    .putCompound("states", NbtMap.fromMap(new TreeMap<>(entry.getCompound("states"))))
                    .build();
            BlockState state = Registries.BLOCKSTATE.get(HashUtils.fnv1a_32_nbt(hashed));
            if (state != null) {
                palette[i] = state.toBlock();
                continue;
            }
            // Hash miss: exporters aren't consistent about NBT value types (this file stores int
            // properties like redstone_signal as bytes), so fall back to matching properties by
            // name and coercing each value to the type the engine expects.
            Block resolved = resolveByProperties(name, entry.getCompound("states"));
            if (resolved != null) {
                palette[i] = resolved;
            } else {
                result.unresolved.add(name + entry.getCompound("states"));
            }
        }
        return result;
    }

    private static Block resolveByProperties(String name, NbtMap states) {
        Block block = Registries.BLOCK.get(name);
        if (block == null) {
            return null;
        }
        try {
            for (var stateEntry : states.entrySet()) {
                org.powernukkitx.block.property.type.BlockPropertyType<?> property = null;
                for (var type : block.getProperties().getPropertyTypeSet()) {
                    if (type.getName().equals(stateEntry.getKey())) {
                        property = type;
                        break;
                    }
                }
                if (property == null) {
                    return null;
                }
                Object value = stateEntry.getValue();
                value = switch (property.getType()) {
                    case INT -> ((Number) value).intValue();
                    case BOOLEAN -> value instanceof Boolean b ? b : ((Number) value).intValue() != 0;
                    case ENUM -> value.toString();
                };
                block.setPropertyValue(property.tryCreateValue(value));
            }
            return block;
        } catch (Exception e) {
            return null;
        }
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    /** Palette block at structure-local (x, y, z); null = untouched (-1) or unresolvable. Air is a real block. */
    public Block blockAt(int x, int y, int z) {
        int index = indices[(x * sizeY + y) * sizeZ + z];
        if (index < 0 || index >= palette.length) {
            return null;
        }
        return palette[index];
    }

    public List<String> unresolvedBlocks() {
        return new ArrayList<>(unresolved);
    }
}
