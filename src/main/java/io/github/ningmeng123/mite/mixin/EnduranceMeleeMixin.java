package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.enchantment.EnduranceActions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Source attacks cost effort on an allowed attempt, including an invulnerable victim. @author NingMeng123 */
@Mixin(Player.class)
public abstract class EnduranceMeleeMixin {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onPlayerAttackTarget(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean mite$sourceAttackAttempt(Player player, Entity target, Operation<Boolean> original) {
        boolean allowed = original.call(player, target);
        if (allowed) EnduranceActions.melee(player);
        return allowed;
    }

    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void mite$noSecondSuccessfulHitFee(Player player, float amount) { }
}
