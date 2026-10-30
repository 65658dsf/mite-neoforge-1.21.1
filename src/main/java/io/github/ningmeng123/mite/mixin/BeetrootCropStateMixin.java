package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Beetroot's own state builder and tick both enter the shared crop lifecycle. @author NingMeng123 */
@Mixin(BeetrootBlock.class)
public abstract class BeetrootCropStateMixin {
    @Inject(method="randomTick",at=@At("HEAD"),cancellable=true)
    private void mite$sharedCropTiming(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        // Beetroot's extra native 1/3 gate would otherwise multiply the calibrated time by three.
        if(CropLifecycle.tick(state,level,pos,random))ci.cancel();
    }
    @Inject(method="createBlockStateDefinition",at=@At("TAIL"))
    private void mite$diseaseState(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci) {
        builder.add(CubeCropBlight.BLIGHTED,CubeCropBlight.DEAD);
    }
}
