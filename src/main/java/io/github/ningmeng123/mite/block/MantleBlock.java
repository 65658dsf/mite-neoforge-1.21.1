package io.github.ningmeng123.mite.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import java.util.function.BiConsumer;

/** Immutable luminous boundary with R196's lava-like ignition walk. @author NingMeng123 */
public final class MantleBlock extends Block {
    public MantleBlock(Properties properties) { super(properties); }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // The same 0..2 upward walk, or three neighbouring ignition attempts. Modern fire hooks apply.
        Fluids.LAVA.defaultFluidState().randomTick(level, pos, random);
    }
    @Override protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> drops) {
        // R196 excludes mantle/core even before ordinary explosion damage and drops are considered.
    }
}
