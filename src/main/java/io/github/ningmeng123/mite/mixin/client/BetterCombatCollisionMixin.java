package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Parallel SAT axes can contain -0.0, which Vec3.equals(ZERO) does not accept. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.bettercombat.client.collision.OrientedBoundingBox", remap = false)
public abstract class BetterCombatCollisionMixin {
    @Inject(method = "Separated", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void mite$ignoreSignedZeroAxis(Vec3[] first, Vec3[] second, Vec3 axis,
            CallbackInfoReturnable<Boolean> cir) {
        if (axis.lengthSqr() == 0D) cir.setReturnValue(false);
    }
}
