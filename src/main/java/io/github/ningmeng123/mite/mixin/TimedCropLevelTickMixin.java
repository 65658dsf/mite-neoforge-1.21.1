package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.TimedCropGrowth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native chunk tick already enforces simulation range, loading and tick-freeze rules. @author NingMeng123 */
@Mixin(ServerLevel.class)
public abstract class TimedCropLevelTickMixin {
    @Inject(method = "tickChunk", at = @At("RETURN"))
    private void mite$tickTimedCrops(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        TimedCropGrowth.tickChunk((ServerLevel)(Object)this, chunk);
    }
}
