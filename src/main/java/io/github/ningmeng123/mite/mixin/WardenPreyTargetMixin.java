package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.MonsterHunting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Warden roaring sets attack memory directly instead of firing the normal brain acquisition event. @author NingMeng123 */
@Mixin(Warden.class)
public abstract class WardenPreyTargetMixin {
    @Inject(method = "setAttackTarget", at = @At("HEAD"), cancellable = true)
    private void mite$bloodNightPrey(LivingEntity target, CallbackInfo ci) {
        if (!MonsterHunting.mayAttackPrey((Warden)(Object)this, target)) ci.cancel();
    }

    @Inject(method = "setAttackTarget", at = @At("TAIL"))
    private void mite$rememberBrainHunt(LivingEntity target, CallbackInfo ci) {
        MonsterHunting.rememberBrainHunt((Warden)(Object)this, target);
    }
}
