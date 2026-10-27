package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Existing target selectors follow the actual vindicator range after join or saved-attribute restoration. @author NingMeng123 */
@Mixin(NearestAttackableTargetGoal.class)
public abstract class NativeVindicatorTargetRangeMixin extends TargetGoal {
    @Shadow protected TargetingConditions targetConditions;

    protected NativeVindicatorTargetRangeMixin(Mob mob, boolean mustSee) { super(mob, mustSee); }

    @Inject(method = "findTarget", at = @At("HEAD"))
    private void mite$refreshNativeVindicatorRange(CallbackInfo callback) {
        // Only change range on the existing condition: its predicate, sight and visibility checks remain native.
        if (mob.getType() == EntityType.VINDICATOR) targetConditions.range(getFollowDistance());
    }
}
