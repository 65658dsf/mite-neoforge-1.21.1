package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.MantleHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flowing water cannot enter a cell directly over mantle/core. @author NingMeng123 */
@Mixin(FlowingFluid.class)
public abstract class MantleWaterFlowMixin {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void mite$evaporate(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluid, CallbackInfo ci) {
        if (fluid.is(FluidTags.WATER) && MantleHeat.evaporatesWater(level, pos)) ci.cancel();
    }
}
