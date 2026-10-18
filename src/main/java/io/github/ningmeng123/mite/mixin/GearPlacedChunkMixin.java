package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.progression.PlayerPlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removed blocks release their old marker before another block occupies the coordinate. @author NingMeng123 */
@Mixin({LevelChunk.class, ProtoChunk.class})
public abstract class GearPlacedChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void mite$clearRemovedPlayerBlock(BlockPos pos, BlockState requested, boolean moving,
            CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null) {
            ChunkAccess chunk = (ChunkAccess)(Object)this;
            PlayerPlacedBlocks.changed(chunk, pos, chunk.getBlockState(pos));
        }
    }
}
