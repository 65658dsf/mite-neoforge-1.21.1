package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.rules.SourceStatusEffectRules;
import io.github.ningmeng123.mite.survival.SourceStatusEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Entity-aware poison pulses retain the native duration/hidden-effect lifecycle. @author NingMeng123 */
@Mixin(MobEffectInstance.class)
public abstract class SourceStatusEffectInstanceMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;shouldApplyEffectTickThisTick(II)Z"))
    private boolean mite$sourcePoisonInterval(MobEffect effect, int duration, int amplifier, Operation<Boolean> original,
            @Local(argsOnly = true) LivingEntity target) {
        return SourceStatusEffects.sourcePoison(target, effect) ? SourceStatusEffectRules.poisonPulse(duration, amplifier)
                : original.call(effect, duration, amplifier);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z"))
    private boolean mite$sourcePoisonCanKill(MobEffect effect, LivingEntity target, int amplifier, Operation<Boolean> original) {
        return SourceStatusEffects.sourcePoison(target, effect) ? SourceStatusEffects.poisonTick(target)
                : original.call(effect, target, amplifier);
    }
}
