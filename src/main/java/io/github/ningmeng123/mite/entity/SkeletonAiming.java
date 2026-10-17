package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.SkeletonAimRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/** Only the arrow's initial release direction is assisted. @author NingMeng123 */
public final class SkeletonAiming {
    private SkeletonAiming() {}

    public static void shoot(AbstractSkeleton skeleton, AbstractArrow arrow, LivingEntity target, float speed) {
        Vec3 velocity = target.getKnownMovement();
        double dx = target.getX() - arrow.getX(), dy = target.getY(.5D) - arrow.getY(), dz = target.getZ() - arrow.getZ();
        var aim = SkeletonAimRules.aim(dx, dy, dz, velocity.x, velocity.z, speed);
        // A lead point behind a wall is not privileged information: fall back to the visible body.
        double travel = (1D - Math.pow(SkeletonAimRules.DRAG, aim.flightTicks())) / (1D - SkeletonAimRules.DRAG);
        Vec3 predicted = new Vec3(arrow.getX() + aim.x() * travel, target.getY(.5D), arrow.getZ() + aim.z() * travel);
        if (!SourceRangedAttackGoal.clearShot(skeleton, predicted)) aim = SkeletonAimRules.aim(dx, dy, dz, 0D, 0D, speed);
        arrow.shoot(aim.x(), aim.y(), aim.z(), speed, SkeletonAimRules.inaccuracy(skeleton.level().getDifficulty().getId()));
    }
}
