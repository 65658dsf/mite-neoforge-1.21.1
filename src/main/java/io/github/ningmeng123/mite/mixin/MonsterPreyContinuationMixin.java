package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.MonsterHunting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops a nearest-prey goal from restoring its fallback target after the blood night has ended. @author NingMeng123 */
@Mixin(TargetGoal.class)
public abstract class MonsterPreyContinuationMixin {
    @Shadow @Final protected Mob mob;
    @Shadow protected LivingEntity targetMob;

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void mite$endBloodHunt(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity target = mob.getTarget() == null ? targetMob : mob.getTarget();
        if ((Object)this instanceof NearestAttackableTargetGoal<?> && target != null
                && MonsterHunting.monster(mob) && !MonsterHunting.mayAttackPrey(mob, target)) cir.setReturnValue(false);
    }
}
