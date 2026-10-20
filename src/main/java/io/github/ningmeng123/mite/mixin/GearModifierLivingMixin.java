package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.progression.GearModifierEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Copy harmful effect instances before immunity changes their application duration. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class GearModifierLivingMixin {
    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance mite$modifierImmunity(MobEffectInstance effect) {
        return GearModifierEffects.incomingEffect((LivingEntity)(Object)this, effect);
    }
}
