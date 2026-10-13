package io.github.ningmeng123.mite.worldgen;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Convert only existing boundary blocks, preserving the modern Nether terrain and biomes. @author NingMeng123 */
public final class NetherBoundaryFeature extends Feature<NoneFeatureConfiguration> {
    public NetherBoundaryFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        if (!level.getLevel().dimension().equals(Level.NETHER)) return false;
        int x = context.origin().getX() & ~15, z = context.origin().getZ() & ~15;
        var pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            pos.set(x + dx, 0, z + dz);
            if (level.getBlockState(pos).is(Blocks.BEDROCK)) level.setBlock(pos, MiteWorldBlocks.CORE.get().defaultBlockState(), 2);
            for (int y = 123; y <= 127; y++) {
                pos.setY(y);
                if (level.getBlockState(pos).is(Blocks.BEDROCK)) level.setBlock(pos, MiteWorldBlocks.MANTLE.get().defaultBlockState(), 2);
            }
        }
        return true;
    }
}
