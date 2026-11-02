package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.TimedCropGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Track actual chunk replacements rather than only player planting and breaking. @author NingMeng123 */
@Mixin(LevelChunk.class)
public abstract class TimedCropChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void mite$trackTimedCrop(BlockPos pos, BlockState requested, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        BlockState previous = cir.getReturnValue();
        if (previous != null) {
            LevelChunk chunk = (LevelChunk)(Object)this;
            TimedCropGrowth.changed(chunk, pos, previous, chunk.getBlockState(pos));
        }
    }
}
