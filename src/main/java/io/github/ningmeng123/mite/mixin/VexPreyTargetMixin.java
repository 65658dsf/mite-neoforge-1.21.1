package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.MonsterHunting;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Vex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Summoned vexes copy proactive prey only during blood nights and still assist their attacked owner. @author NingMeng123 */
@Mixin(targets = "net.minecraft.world.entity.monster.Vex$VexCopyOwnerTargetGoal")
public abstract class VexPreyTargetMixin extends TargetGoal {
    protected VexPreyTargetMixin(Mob mob, boolean sight) { super(mob, sight); }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void mite$bloodNightOwnerPrey(CallbackInfoReturnable<Boolean> cir) {
        Vex vex = (Vex)mob;
        Mob owner = vex.getOwner();
        if (owner != null && owner.getTarget() != null && !MonsterHunting.mayAttackPrey(vex, owner.getTarget()))
            cir.setReturnValue(false);
    }

    @Override public boolean canContinueToUse() {
        var target = mob.getTarget();
        return target != null && MonsterHunting.mayAttackPrey(mob, target) && super.canContinueToUse();
    }
}
