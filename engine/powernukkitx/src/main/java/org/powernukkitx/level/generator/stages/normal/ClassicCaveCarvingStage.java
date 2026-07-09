package org.powernukkitx.level.generator.stages.normal;

import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.level.generator.ChunkGenerateContext;
import org.powernukkitx.level.generator.GenerateStage;
import org.powernukkitx.level.generator.feature.terrain.CaveExtraUndergroundFeature;
import org.powernukkitx.level.generator.feature.terrain.CaveGenerateFeature;

/**
 * Carves classic (pre-1.18, "worm tunnel" random-walk) caves into freshly generated overworld
 * terrain, on top of the modern density-function caves {@link NormalTerrainStage} already
 * produces. {@link CaveGenerateFeature} already faithfully implements that legacy algorithm
 * (branching tunnels + occasional rooms via chunk-seeded random walks) -- it just wasn't wired
 * into any generation pipeline; none of the shipped biome definitions list
 * {@code minecraft:overworld_cave} as an active feature. This stage makes it opt-in via
 * {@code gameplay-settings.classic-caves} instead of replacing the modern terrain shaping, which
 * would require unpicking the cave contribution from the density-function/aquifer system.
 */
public class ClassicCaveCarvingStage extends GenerateStage {

    public static final String NAME = "classic_cave_carving";

    private final CaveGenerateFeature caveFeature = new CaveGenerateFeature();
    private final CaveExtraUndergroundFeature extraFeature = new CaveExtraUndergroundFeature();

    @Override
    public void apply(ChunkGenerateContext context) {
        IChunk chunk = context.getChunk();
        if (!chunk.getLevel().getServer().getSettings().gameplaySettings().classicCaves()) {
            return;
        }
        caveFeature.apply(context);
        extraFeature.apply(context);
    }

    @Override
    public String name() {
        return NAME;
    }
}
