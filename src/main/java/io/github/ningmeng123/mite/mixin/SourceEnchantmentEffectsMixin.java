package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rebuild free-action resistance immediately when the native potion attributes change. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class SourceEnchantmentEffectsMixin {
    @Inject(method = "onEffectAdded", at = @At("TAIL"))
    private void mite$addedEffect(MobEffectInstance effect, Entity cause, CallbackInfo ci) {
        SourceEnchantmentCombat.refreshMovement((LivingEntity)(Object)this);
    }
    @Inject(method = "onEffectUpdated", at = @At("TAIL"))
    private void mite$updatedEffect(MobEffectInstance effect, boolean changed, Entity cause, CallbackInfo ci) {
        SourceEnchantmentCombat.refreshMovement((LivingEntity)(Object)this);
    }
    @Inject(method = "onEffectRemoved", at = @At("TAIL"))
    private void mite$removedEffect(MobEffectInstance effect, CallbackInfo ci) {
        SourceEnchantmentCombat.afterEffectRemoved((LivingEntity)(Object)this, effect);
    }
}
