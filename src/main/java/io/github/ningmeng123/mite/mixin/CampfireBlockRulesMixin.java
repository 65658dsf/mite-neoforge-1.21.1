package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A placed campfire starts cold and requires a paid firestarter or torch to ignite. @author NingMeng123 */
@Mixin(CampfireBlock.class)
public abstract class CampfireBlockRulesMixin {
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void mite$placeCold(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null) cir.setReturnValue(cir.getReturnValue().setValue(CampfireBlock.LIT, false));
    }

    @Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
    private void mite$noProjectileIgnition(CallbackInfo ci) {
        ci.cancel();
    }
}
