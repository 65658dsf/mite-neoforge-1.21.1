package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.combat.ShieldGuard;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Resolve forced guard breaks after the threshold hit has paid native shield wear. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class ShieldGuardLivingMixin {
    @WrapMethod(method = "hurt")
    private boolean mite$finishGuardAfterNativeHit(DamageSource source, float damage, Operation<Boolean> original) {
        return ShieldGuard.hurt((LivingEntity)(Object)this, () -> original.call(source, damage));
    }

    @Inject(method = "startUsingItem", at = @At("HEAD"), cancellable = true)
    private void mite$blockShieldChangesDuringCooldown(InteractionHand hand, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (ShieldGuard.disabled(self) && ShieldGuard.shield(self.getItemInHand(hand))) ci.cancel();
    }

    @Inject(method = "blockUsingShield", at = @At("TAIL"))
    private void mite$clearGuardAfterNativeMobAxeBlock(LivingEntity attacker, CallbackInfo ci) {
        ShieldGuard.axeBlocked((LivingEntity)(Object)this, attacker);
    }
}
