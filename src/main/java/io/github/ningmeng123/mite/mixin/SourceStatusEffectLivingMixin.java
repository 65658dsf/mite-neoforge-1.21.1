package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.survival.SourceStatusEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source potion eligibility and immediate migration of saved native attribute modifiers. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class SourceStatusEffectLivingMixin {
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void mite$sourceBiology(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (SourceStatusEffects.rejects((LivingEntity)(Object)this, effect)) cir.setReturnValue(false);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mite$restoreSourceEffectAttributes(CompoundTag tag, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;
        SourceStatusEffects.migrateLoadedEffects(entity);
        SourceEnchantmentCombat.refreshMovement(entity);
    }
}
