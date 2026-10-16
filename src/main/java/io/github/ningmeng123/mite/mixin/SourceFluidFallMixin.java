package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.world.SourceFluidFall;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The source freefall condition runs only when the fluid's scheduled tick executes. @author NingMeng123 */
@Mixin(FlowingFluid.class)
public abstract class SourceFluidFallMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void mite$fallBeforeSpreading(Level level,BlockPos pos,FluidState state,CallbackInfo ci) {
        if (SourceFluidFall.tryFall(level,pos,state)) ci.cancel();
    }
}
