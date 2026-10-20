package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.item.SourceArmorDefense;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Every living entity retains native armor, toughness, Breach and magic reduction. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class SourceArmorDefenseMixin {
    @WrapOperation(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"))
    private float mite$sourcePiercingBeforeModernArmor(LivingEntity target, float incoming, DamageSource source,
            float armor, float toughness, Operation<Float> original) {
        armor = io.github.ningmeng123.mite.progression.GearModifierEffects.generalProtectionArmor(target, armor);
        armor += io.github.ningmeng123.mite.progression.GearModifierEffects.conditionalArmor(target, source);
        return original.call(target, incoming, source, SourceArmorDefense.armorAfterPiercing(target, source, armor), toughness);
    }
}
