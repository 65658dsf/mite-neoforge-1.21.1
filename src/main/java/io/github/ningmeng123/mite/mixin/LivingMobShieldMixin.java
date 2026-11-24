package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.api.MiteExtensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Give shield-bearing mobs the player's native axe-interruption rule. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class LivingMobShieldMixin {
    @Inject(method="isDamageSourceBlocked",at=@At("HEAD"),cancellable=true)
    private void mite$extensionMobDefense(DamageSource source,CallbackInfoReturnable<Boolean> cir) {
        if((Object)this instanceof Mob mob&&!MiteExtensions.policy().mobDefense(mob,true))cir.setReturnValue(false);
    }
    @ModifyExpressionValue(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/CommonHooks;onDamageBlock(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;Z)Lnet/neoforged/neoforge/event/entity/living/LivingShieldBlockEvent;"))
    private LivingShieldBlockEvent mite$respectCanceledShieldBlock(LivingShieldBlockEvent event) {
        // NeoForge 21.1 posts this cancellable event but does not copy cancellation to getBlocked().
        // Honor its documented contract before the native hurt path checks health and durability budgets.
        if (event.isCanceled()) event.setBlocked(false);
        return event;
    }

    @Inject(method = "hurtCurrentlyUsedShield", at = @At("HEAD"))
    private void mite$wearBlockingMobShield(float damage, CallbackInfo ci) {
        MobShields.hurtShield((LivingEntity)(Object)this, damage);
    }

    @Inject(method = "canDisableShield", at = @At("RETURN"), cancellable = true)
    private void mite$miteAxesDisableEveryShield(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && MobShields.axe(((LivingEntity)(Object)this).getMainHandItem())) cir.setReturnValue(true);
    }
}
