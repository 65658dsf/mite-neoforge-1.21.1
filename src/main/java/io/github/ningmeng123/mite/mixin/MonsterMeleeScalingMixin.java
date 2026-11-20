package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.entity.MonsterMeleeScaling;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native melee reads the grown body attack before adding the held weapon. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class MonsterMeleeScalingMixin {
    @WrapMethod(method = "doHurtTarget")
    private boolean mite$scopeConfiguredMelee(Entity target, Operation<Boolean> original) {
        Mob self = (Mob)(Object)this;
        if (self.level().isClientSide || !MonsterDayScaling.handles(self)) return original.call(target);
        try (MonsterMeleeScaling.Scope ignored = MonsterMeleeScaling.enter(self)) {
            return original.call(target);
        }
    }

    @WrapOperation(method = "doHurtTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double mite$configureOnlyInnateAttack(Mob self, Holder<Attribute> attribute, Operation<Double> original) {
        double nativeValue = original.call(self, attribute);
        return attribute.equals(Attributes.ATTACK_DAMAGE)
                ? MonsterMeleeScaling.attackValue(self, nativeValue) : nativeValue;
    }
}
