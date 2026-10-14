package io.github.ningmeng123.mite.world;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import io.github.ningmeng123.mite.rules.DecorativeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.ArrayList;

/** Source afc four triangular samples, and aff material-specific patch sizes. @author NingMeng123 */
public final class R196DecorativeFeature extends Feature<BlockStateConfiguration> {
    public R196DecorativeFeature(Codec<BlockStateConfiguration> codec) { super(codec); }
    @Override public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        var level = context.level(); var random = context.random(); var state = context.config().state;
        BlockPos origin = context.origin(); boolean wither = state.is(MiteDecorativeContent.WITHERWOOD_BUSH.get());
        if (wither) {
            // aep performs 16 independently chosen seed positions; afc descends each seed to ground.
            boolean any = false;
            for (int patch = 0; patch < 16; patch++) {
                BlockPos seed = new BlockPos(origin.getX() + random.nextInt(16),
                        random.nextInt(128), origin.getZ() + random.nextInt(16));
                while (seed.getY() > level.getMinBuildHeight() && (level.isEmptyBlock(seed)
                        || level.getBlockState(seed).is(net.minecraft.tags.BlockTags.LEAVES))) seed = seed.below();
                for (int attempt = 0; attempt < 4; attempt++) {
                    BlockPos pos = seed.offset(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
                    if (level.isEmptyBlock(pos) && state.canSurvive(level, pos)) { level.setBlock(pos, state, 2); any = true; }
                }
            }
            return any;
        }
        // applyBiomeDecoration supplies the chunk's lowest section origin (overworld Y=-64).
        // Source flower choices belong to the surface biome, not a 3-D cave biome at that origin.
        BlockPos surface = new BlockPos(origin.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                origin.getX(), origin.getZ()), origin.getZ());
        var biome = level.getBiome(surface);
        int subtype = MiteDecorativeContent.flowerSubtype(state.getBlock());
        boolean swamp = biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP);
        boolean plains = biome.is(Biomes.PLAINS);
        boolean jungle = biome.is(BiomeTags.IS_JUNGLE);
        // acu defaults to A=2; adf Plains sets 4 and adc Jungle sets 6.
        int patches = plains ? 4 : jungle ? 6 : 2;
        var candidates = new ArrayList<Integer>();
        for (int type : new int[] {0, 1, 2, 5, 7, 8})
            if (DecorativeRules.flowerBiome(type, biome.value().getBaseTemperature(), swamp, jungle)) candidates.add(type);
        int attempts = DecorativeRules.flowerPatchAttempts(subtype, plains, jungle);
        boolean any = false;
        for (int patch = 0; patch < patches; patch++) {
            if (random.nextInt(swamp ? 3 : 2) != 0 || candidates.isEmpty()) continue;
            int chosen = plains && random.nextInt(2) == 0 ? 8 : candidates.get(random.nextInt(candidates.size()));
            if (chosen != subtype) continue;
            int x = origin.getX() + random.nextInt(16), z = origin.getZ() + random.nextInt(16);
            BlockPos seed = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
            for (int i = 0; i < attempts; i++) {
                BlockPos pos = seed.offset(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4), random.nextInt(8) - random.nextInt(8));
                if (level.isEmptyBlock(pos) && state.canSurvive(level, pos)) { level.setBlock(pos, state, 2); any = true; }
            }
        }
        return any;
    }
}
