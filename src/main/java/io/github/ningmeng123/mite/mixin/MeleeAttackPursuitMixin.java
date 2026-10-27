package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.MeleePursuitPaths;
import io.github.ningmeng123.mite.entity.VanillaMeleePursuit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replace only native hostile melee navigation; attacks, speed and special goals stay native. @author NingMeng123 */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackPursuitMixin {
    @Shadow @Final protected PathfinderMob mob;
    @Shadow @Final private double speedModifier;
    @Shadow private Path path;
    @Unique private LivingEntity mite$pursuitTarget;
    @Unique private Path mite$pursuitPath;
    @Unique private PathNavigation mite$pursuitNavigation;
    @Unique private int mite$lastTargetId = Integer.MIN_VALUE;
    @Unique private PathNavigation mite$lastNavigation;
    @Unique private int mite$replanTicks;
    @Unique private Vec3 mite$progressPosition;
    @Unique private int mite$stalledTicks;

    @Unique private void mite$clearPursuit(boolean invalidate) {
        if (mite$pursuitNavigation != null && mite$pursuitNavigation.getPath() == mite$pursuitPath)
            mite$pursuitNavigation.stop();
        if (invalidate) {
            MeleePursuitPaths.invalidate(mob);
            mite$lastTargetId = Integer.MIN_VALUE; mite$lastNavigation = null;
        }
        VanillaMeleePursuit.clear(mob);
        mite$pursuitTarget = null; mite$pursuitPath = null; mite$pursuitNavigation = null;
        mite$progressPosition = null; mite$stalledTicks = 0; mite$replanTicks = 0;
    }

    @Unique private Path mite$findPursuit(LivingEntity target) {
        if (mite$pursuitTarget != target || mite$pursuitNavigation != mob.getNavigation()) {
            mite$clearPursuit(mite$lastTargetId != Integer.MIN_VALUE
                    && (mite$lastTargetId != target.getId() || mite$lastNavigation != mob.getNavigation()));
            mite$pursuitTarget = target; mite$pursuitNavigation = mob.getNavigation();
            mite$lastTargetId = target.getId(); mite$lastNavigation = mob.getNavigation();
        }
        mite$pursuitPath = MeleePursuitPaths.findVanilla(mob, target, mite$pursuitPath);
        return mite$pursuitPath;
    }

    @WrapOperation(method = "canUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;"))
    private Path mite$planNativeMelee(PathNavigation navigation, Entity target, int accuracy, Operation<Path> original) {
        if (target instanceof LivingEntity living && VanillaMeleePursuit.applies(mob, living))
            return mite$findPursuit(living);
        if (mite$pursuitTarget != null) mite$clearPursuit(true);
        return original.call(navigation, target, accuracy);
    }

    @Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
    private void mite$continueGroundDetour(CallbackInfoReturnable<Boolean> callback) {
        LivingEntity target = mob.getTarget();
        if (VanillaMeleePursuit.applies(mob, target) && mob.isWithinRestriction(target.blockPosition()))
            callback.setReturnValue(true);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mite$followNativeMeleeDetour(CallbackInfo callback) {
        LivingEntity target = mob.getTarget();
        if (!VanillaMeleePursuit.applies(mob, target)) {
            if (mite$pursuitTarget != null) mite$clearPursuit(true);
            return;
        }
        boolean changed = mite$pursuitTarget != target || mite$pursuitNavigation != mob.getNavigation();
        if (changed) mite$replanTicks = 0;
        if (mite$progressPosition == null || mite$progressPosition.distanceToSqr(mob.position()) >= .25D) {
            mite$progressPosition = mob.position(); mite$stalledTicks = 0;
        } else if (++mite$stalledTicks == 60) {
            mob.getNavigation().stop(); mite$replanTicks = 0;
        }
        if (--mite$replanTicks <= 0) {
            path = mite$findPursuit(target);
            mite$replanTicks = 10;
            if (path != mob.getNavigation().getPath() || mob.getNavigation().isDone())
                mob.getNavigation().moveTo(path, speedModifier);
            // Native moveTo keeps its existing object when both routes have the same nodes.
            // Progress and target memory must follow the object actually installed in navigation.
            path = mob.getNavigation().getPath();
            mite$pursuitPath = path;
        }
        VanillaMeleePursuit.recordProgress(mob, target, mite$pursuitPath);
        if (mob.getNavigation().isDone() && MeleePursuitPaths.reachesVanilla(mob, mite$pursuitPath, target)
                && !VanillaMeleePursuit.canStrikeFrom(mob, target, mob.position())) {
            Vec3 end = MeleePursuitPaths.standingPoint(mob, mite$pursuitPath, mite$pursuitPath.getNodeCount() - 1);
            if (mob.level().noCollision(mob, mob.getBoundingBox().move(end.subtract(mob.position())).deflate(.001D)))
                mob.getMoveControl().setWantedPosition(end.x, end.y, end.z, speedModifier);
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;moveTo(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean mite$keepVerifiedRoute(PathNavigation navigation, Entity target, double speed, Operation<Boolean> original) {
        if (target instanceof LivingEntity living && VanillaMeleePursuit.applies(mob, living))
            return navigation.getPath() != null && !navigation.isDone();
        return original.call(navigation, target, speed);
    }

    @Inject(method = "stop", at = @At("TAIL"))
    private void mite$stopNativePursuit(CallbackInfo callback) {
        // Preserve failure backoff across native goal stop/start cycles for the same target.
        mite$clearPursuit(false);
    }
}
