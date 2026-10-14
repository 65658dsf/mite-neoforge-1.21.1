package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.SilverArmor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Server effect application uses source silver coverage before normal events and effect merging. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class SilverArmorPoisonMixin {
    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance mite$silverPoisonDuration(MobEffectInstance effect) {
        return SilverArmor.incomingPoison((LivingEntity)(Object)this, effect);
    }
}
