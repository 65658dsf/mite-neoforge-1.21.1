package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Source uf:1101 disarms before hurt; proc effects follow the real primary hit, not modern sweep copies. @author NingMeng123 */
@Mixin(Player.class)
public abstract class SourceEnchantmentPlayerMeleeMixin {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mite$sourceMelee(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        return SourceEnchantmentCombat.melee((Player)(Object)this, target, source, amount,
                () -> original.call(target, source, amount));
    }
}
