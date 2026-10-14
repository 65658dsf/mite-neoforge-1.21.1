package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.monster.Skeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ordinary source skeletons never transform into the modern stray in powder snow. @author NingMeng123 */
@Mixin(Skeleton.class)
public abstract class R196VanillaSkeletonMixin {
    @Inject(method = {"startFreezeConversion", "doFreezeConversion"}, at = @At("HEAD"), cancellable = true)
    private void mite$keepSourceSkeletonIdentity(CallbackInfo ci) { ci.cancel(); }
}
