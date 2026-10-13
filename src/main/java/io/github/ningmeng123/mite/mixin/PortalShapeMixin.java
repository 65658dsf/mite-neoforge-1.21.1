package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.portal.MitePortalFrame;
import io.github.ningmeng123.mite.portal.MitePortals;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 supports only the standard 2x3 interior and never activates in the End. @author NingMeng123 */
@Mixin(PortalShape.class)
public abstract class PortalShapeMixin {
    @Shadow @Final private LevelAccessor level;
    @Shadow private BlockPos bottomLeft;
    @Shadow @Final private int width;
    @Shadow private int height;

    @Inject(method = "isValid", at = @At("RETURN"), cancellable = true)
    private void mite$exactFrame(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && (width != 2 || height != 3
                || level instanceof Level world && world.dimension() == Level.END
                || MitePortalFrame.find(level, bottomLeft).isEmpty())) cir.setReturnValue(false);
    }

    @Inject(method = "createPortalBlocks", at = @At("TAIL"))
    private void mite$setActivationRoute(CallbackInfo ci) {
        if (level instanceof Level world) MitePortalFrame.find(level, bottomLeft)
                .ifPresent(frame -> MitePortals.initializeRoute(world, frame));
    }
}
