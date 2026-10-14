package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import io.github.ningmeng123.mite.rules.DecorativeRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve the native fall event and immunity gates, replacing only source bush landing damage. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class WitherwoodFallCushionMixin {
    @WrapOperation(method = "causeFallDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mite$sourceBushCushion(LivingEntity entity, DamageSource source, float damage, Operation<Boolean> original,
            float distance, float multiplier, DamageSource originalSource) {
        var level = entity.level(); var landed = entity.getOnPos();
        var bush = MiteDecorativeContent.WITHERWOOD_BUSH.get();
        if (!level.getBlockState(landed).is(bush) && !level.getBlockState(landed.above()).is(bush))
            return original.call(entity, source, damage);
        var jump = entity.getEffect(MobEffects.JUMP);
        float cushioned = DecorativeRules.witherwoodFallDamage(distance, jump == null ? -1 : jump.getAmplifier(),
                (float) (multiplier * entity.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)));
        return cushioned > 0 && original.call(entity, source, cushioned);
    }
}
