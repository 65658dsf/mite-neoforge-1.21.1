package io.github.ningmeng123.mite.entity;

import java.util.ArrayList;
import java.util.Comparator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Nearby reachable firing angles around real cover, without changing terrain. @author NingMeng123 */
public final class SkeletonFiringPositions {
    private static final int[] RADII = {2, 4, 6};
    private static final int[] HEIGHTS = {0, 1, -1, 2, -2};
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int MAX_PATH_ATTEMPTS = 8;

    private SkeletonFiringPositions() {}

    /** The attack goal limits this bounded search to once per second while the target is hidden. */
    @Nullable
    public static Path find(AbstractSkeleton skeleton, LivingEntity target, double rangeSquared) {
        if (!target.isAlive() || SourceRangedAttackGoal.clearShot(skeleton, target)) return null;
        BlockPos origin = skeleton.blockPosition();
        var candidates = new ArrayList<BlockPos>();
        for (int radius : RADII) for (int[] direction : DIRECTIONS) {
            for (int height : HEIGHTS) {
                BlockPos feet = origin.offset(direction[0] * radius, height, direction[1] * radius);
                Vec3 point = Vec3.atBottomCenterOf(feet);
                if (point.distanceToSqr(target.position()) > rangeSquared || !safeStandingPosition(skeleton, feet)) continue;
                if (!canShootFrom(skeleton, target, point)) continue;
                candidates.add(feet);
                break;
            }
        }
        // Nearby lateral steps win over lengthy detours; the target-distance term breaks equal-distance ties.
        candidates.sort(Comparator.comparingDouble(feet -> feet.distSqr(origin)
                + .05D * Vec3.atBottomCenterOf(feet).distanceToSqr(target.position())));
        int attempts = 0;
        for (BlockPos feet : candidates) {
            if (++attempts > MAX_PATH_ATTEMPTS) break;
            Path path = skeleton.getNavigation().createPath(feet, 0, 16);
            if (path == null || !path.canReach() || path.getEndNode() == null
                    || !path.getEndNode().asBlockPos().equals(feet) || !safePath(skeleton, path)) continue;
            // Check the actual navigation endpoint, not only the sampled block center.
            Vec3 end = path.getEntityPosAtNode(skeleton, path.getNodeCount() - 1);
            if (end.distanceToSqr(target.position()) <= rangeSquared && canShootFrom(skeleton, target, end)) return path;
        }
        return null;
    }

    private static boolean safeStandingPosition(AbstractSkeleton skeleton, BlockPos feet) {
        var level = skeleton.level();
        if (!level.hasChunkAt(feet) || level.isOutsideBuildHeight(feet)
                || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) return false;
        AABB body = skeleton.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(skeleton.position()));
        return level.noCollision(skeleton, body) && !level.containsAnyLiquid(body)
                && level.getFluidState(feet.below()).isEmpty();
    }

    private static boolean canShootFrom(AbstractSkeleton skeleton, LivingEntity target, Vec3 feet) {
        Vec3 eyes = feet.add(0D, skeleton.getEyeHeight(), 0D);
        return SourceRangedAttackGoal.clearShot(skeleton, eyes, target.position().add(0D, target.getBbHeight() * .5D, 0D))
                || SourceRangedAttackGoal.clearShot(skeleton, eyes, target.getEyePosition())
                || SourceRangedAttackGoal.clearShot(skeleton, eyes, target.position().add(0D, target.getBbHeight() * .25D, 0D));
    }

    private static boolean safePath(AbstractSkeleton skeleton, Path path) {
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (!skeleton.level().hasChunkAt(node.asBlockPos()) || node.costMalus < 0F
                    || node.type == PathType.LAVA || node.type == PathType.DAMAGE_FIRE
                    || node.type == PathType.DAMAGE_OTHER || node.type == PathType.DAMAGE_CAUTIOUS
                    || node.type == PathType.POWDER_SNOW || node.type == PathType.WATER
                    || node.type == PathType.WATER_BORDER) return false;
        }
        return true;
    }
}
