package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Both source creature overrides and native creatures ultimately invoke Mob's real melee hurt. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class SourceEnchantmentMobMeleeMixin {
    @WrapOperation(method = "doHurtTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mite$sourceMelee(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        return SourceEnchantmentCombat.melee((Mob)(Object)this, target, source, amount,
                () -> original.call(target, source, amount));
    }
}
