package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.entity.VanillaMeleePursuit;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Existing targets can remain remembered while a verified native melee detour makes progress. @author NingMeng123 */
@Mixin(TargetGoal.class)
public abstract class NativeMeleeTargetMemoryMixin {
    @Shadow @Final protected Mob mob;

    @ModifyExpressionValue(method = "canContinueToUse", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/ai/goal/target/TargetGoal;unseenMemoryTicks:I"), require = 1, allow = 1)
    private int mite$rememberProgressingDetour(int original) {
        return mob instanceof PathfinderMob walker && VanillaMeleePursuit.followsReachableRoute(walker, mob.getTarget())
                ? Math.max(original, 600) : original;
    }
}
