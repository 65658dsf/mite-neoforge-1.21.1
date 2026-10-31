package io.github.ningmeng123.mite.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Physical step-height support for native path points. @author NingMeng123 */
final class PursuitGroundGeometry {
    private static final double EPSILON = .001D;

    private PursuitGroundGeometry() {}

    record Standing(Vec3 position, boolean footObstacle) {}

    /** The caller supplies the native below-block floor and validates the resulting full body. */
    static Standing resolve(PathfinderMob mob, BlockGetter terrain, Vec3 base) {
        AABB footprint = mob.getBoundingBox().move(base.subtract(mob.position()));
        double insetX = Math.min(EPSILON, (footprint.maxX - footprint.minX) * .25D);
        double insetZ = Math.min(EPSILON, (footprint.maxZ - footprint.minZ) * .25D);
        double minX = footprint.minX + insetX, maxX = footprint.maxX - insetX;
        double minZ = footprint.minZ + insetZ, maxZ = footprint.maxZ - insetZ;
        double limit = base.y + Math.max(0D, mob.maxUpStep());
        int minimumY = Mth.floor(base.y + EPSILON), maximumY = Mth.floor(limit + EPSILON);
        double standingY = base.y;
        boolean footObstacle = false;
        CollisionContext context = CollisionContext.of(mob);

        for (BlockPos position : BlockPos.betweenClosed(Mth.floor(minX), minimumY, Mth.floor(minZ),
                Mth.floor(maxX), maximumY, Mth.floor(maxZ))) {
            // Never let support probing load terrain or partially apply a missing-area result.
            if (mob.level().isOutsideBuildHeight(position) || !mob.level().hasChunkAt(position))
                return new Standing(base, false);
            var shape = terrain.getBlockState(position).getCollisionShape(terrain, position, context);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                double left = position.getX() + box.minX, right = position.getX() + box.maxX;
                double front = position.getZ() + box.minZ, back = position.getZ() + box.maxZ;
                if (right <= minX || left >= maxX || back <= minZ || front >= maxZ) continue;
                double top = position.getY() + box.maxY;
                if (top <= base.y + EPSILON) continue;
                footObstacle = true;
                // Higher obstacles remain obstacles; native node/jump logic decides how to pass them.
                if (top <= limit + EPSILON) standingY = Math.max(standingY, top);
            }
        }

        return new Standing(standingY > base.y + EPSILON ? new Vec3(base.x, standingY, base.z) : base,
                footObstacle);
    }
}
