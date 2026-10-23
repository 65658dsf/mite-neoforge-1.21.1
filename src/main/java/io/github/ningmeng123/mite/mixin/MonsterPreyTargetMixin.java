package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.MonsterHunting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared vanilla monster acquisition keeps player and HurtByTargetGoal retaliation rules. @author NingMeng123 */
@Mixin(NearestAttackableTargetGoal.class)
public abstract class MonsterPreyTargetMixin extends TargetGoal {
    @Shadow protected LivingEntity target;
    protected MonsterPreyTargetMixin(Mob mob, boolean sight) { super(mob, sight); }

    @Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
    private void mite$bloodNightPrey(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && target != null && MonsterHunting.monster(mob)
                && !MonsterHunting.mayAttackPrey(mob, target)) cir.setReturnValue(false);
    }

    @Inject(method = "start", at = @At("TAIL"))
    private void mite$rememberHunt(CallbackInfo ci) {
        if (MonsterHunting.monster(mob)) MonsterHunting.remember(mob, mob.getTarget());
    }
}
