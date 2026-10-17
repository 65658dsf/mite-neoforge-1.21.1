package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Brain melee uses synced keyframes; digging and farmland work keep their native swings. @author NingMeng123 */
@Mixin(MeleeAttack.class)
public abstract class BetterCombatMobSwingMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private static void mite$weaponSwing(Mob mob, InteractionHand hand, Operation<Void> original) {
        if (!BetterMobCombat.supported(mob)) original.call(mob, hand);
    }
}
