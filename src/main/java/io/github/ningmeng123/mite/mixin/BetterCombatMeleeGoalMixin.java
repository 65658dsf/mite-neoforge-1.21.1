package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Weapon speed replaces the fixed vanilla twenty-tick goal interval. @author NingMeng123 */
@Mixin(MeleeAttackGoal.class)
public abstract class BetterCombatMeleeGoalMixin {
    @Shadow @Final protected PathfinderMob mob;
    @Inject(method = "checkAndPerformAttack", at = @At("HEAD"), cancellable = true)
    private void mite$weaponCadence(LivingEntity target, CallbackInfo ci) {
        if (BetterMobCombat.supported(mob)) {
            BetterMobCombat.request(mob, target);
            ci.cancel();
        }
    }
}
