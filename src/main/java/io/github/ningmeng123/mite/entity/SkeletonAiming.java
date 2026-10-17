package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.SkeletonAimRules;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/** Only the arrow's initial release direction is assisted. @author NingMeng123 */
public final class SkeletonAiming {
    private SkeletonAiming() {}

    /** Use the same exposed body point for both permission to fire and the release direction. */
    @Nullable
    public static Vec3 aimPoint(Mob shooter, LivingEntity target) {
        Vec3 middle = target.position().add(0D, target.getBbHeight() * .5D, 0D);
        if (SourceRangedAttackGoal.clearShot(shooter, middle)) return middle;
        Vec3 eyes = target.getEyePosition();
        if (SourceRangedAttackGoal.clearShot(shooter, eyes)) return eyes;
        Vec3 lowerBody = target.position().add(0D, target.getBbHeight() * .25D, 0D);
        return SourceRangedAttackGoal.clearShot(shooter, lowerBody) ? lowerBody : null;
    }

    public static void shoot(AbstractSkeleton skeleton, AbstractArrow arrow, LivingEntity target, float speed) {
        Vec3 point = aimPoint(skeleton, target);
        if (point == null) return;
        Vec3 velocity = target.getKnownMovement();
        double dx = point.x - arrow.getX(), dy = point.y - arrow.getY(), dz = point.z - arrow.getZ();
        var aim = SkeletonAimRules.aim(dx, dy, dz, velocity.x, velocity.z, speed);
        // A lead point behind a wall is not privileged information: fall back to the visible body.
        double travel = (1D - Math.pow(SkeletonAimRules.DRAG, aim.flightTicks())) / (1D - SkeletonAimRules.DRAG);
        Vec3 predicted = new Vec3(arrow.getX() + aim.x() * travel, point.y, arrow.getZ() + aim.z() * travel);
        if (!SourceRangedAttackGoal.clearShot(skeleton, predicted)) aim = SkeletonAimRules.aim(dx, dy, dz, 0D, 0D, speed);
        arrow.shoot(aim.x(), aim.y(), aim.z(), speed, SkeletonAimRules.inaccuracy(skeleton.level().getDifficulty().getId()));
    }
}
