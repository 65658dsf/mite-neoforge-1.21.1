package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removed or replaced blocks lose their provenance before any later placement. @author NingMeng123 */
@Mixin({LevelChunk.class, ProtoChunk.class})
public abstract class TrialBlockOriginMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void mite$clearReplacedOrigin(BlockPos pos, BlockState requested, boolean moving,
            CallbackInfoReturnable<BlockState> cir) {
        BlockState previous = cir.getReturnValue();
        if (previous != null) {
            ChunkAccess chunk = (ChunkAccess)(Object)this;
            NaturalTrialBlocks.onBlockChanged(chunk, pos, previous, chunk.getBlockState(pos));
        }
    }
}
