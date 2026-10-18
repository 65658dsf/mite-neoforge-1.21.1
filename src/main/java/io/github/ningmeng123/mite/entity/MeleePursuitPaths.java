package io.github.ningmeng123.mite.entity;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real walking routes to reachable attack positions, including routes that first lead away. @author NingMeng123 */
public final class MeleePursuitPaths {
    private static final int RANGE = 48;
    private static final int MAX_NODES = 2048;
    private static final int DETOUR_LENGTH = 96;
    private static final int DETOUR_NODES = 8192;

    private MeleePursuitPaths() {}

    public static boolean applies(PathfinderMob mob) {
        return R196MonsterAi.managed(mob) && mob.getNavigation() instanceof GroundPathNavigation
                && mob.getTarget() instanceof Player target && target.isAlive() && target.level() == mob.level();
    }

    public static boolean inRange(PathfinderMob mob, LivingEntity target) {
        return mob.blockPosition().distSqr(target.blockPosition()) <= RANGE * RANGE;
    }

    @Nullable public static Path find(PathfinderMob mob, LivingEntity target, @Nullable Path previous) {
        Path active = mob.getNavigation().getPath();
        // Other goals may supply a path that assumes doors can be opened. Only retain our verified route.
        if (active == previous && !mob.getNavigation().isDone() && !mob.getNavigation().isStuck() && reaches(mob, active, target)
                && clearNextNodes(mob, active)) return active;
        Path direct = find(mob, Set.of(target.blockPosition()), 1, false);
        if (reaches(mob, direct, target)) return direct;
        Path exact = find(mob, Set.of(target.blockPosition()), 0, false);
        if (reaches(mob, exact, target)) return exact;
        if (distance(mob, exact, target) < distance(mob, direct, target)) direct = exact;

        // The player's feet may occupy a fence, wall or other unreachable block. Search actual
        // attack positions together, so the closest failed endpoint cannot hide a side entrance.
        Set<BlockPos> positions = new LinkedHashSet<>();
        BlockPos center = target.blockPosition();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -2; y <= 1; y++) {
            BlockPos feet = center.offset(x, y, z);
            if (!mob.isWithinRestriction(feet) || !standingPosition(mob, feet)) continue;
            Vec3 point = entityPosition(mob, feet);
            if (SourceMeleeGoal.canStrikeFrom(mob, target, point)) positions.add(feet);
        }
        if (!positions.isEmpty()) {
            Path detour = find(mob, positions, 0, true);
            if (reaches(mob, detour, target)) return detour;
        }
        return direct;
    }

    public static boolean reaches(PathfinderMob mob, @Nullable Path path, LivingEntity target) {
        return path != null && path.canReach() && path.getEndNode() != null
                && SourceMeleeGoal.canStrikeFrom(mob, target, standingPoint(mob, path, path.getNodeCount() - 1));
    }

    /** Matches native navigation's floor-height adjustment for slabs, stairs and fence tops. */
    public static Vec3 standingPoint(PathfinderMob mob, Path path, int index) {
        return onFloor(mob, path.getEntityPosAtNode(mob, index));
    }

    private static Vec3 onFloor(PathfinderMob mob, Vec3 point) {
        BlockPos feet = BlockPos.containing(point);
        if (!mob.level().hasChunkAt(feet) || mob.level().isOutsideBuildHeight(feet.below())) return point;
        return mob.level().getBlockState(feet.below()).isAir() ? point
                : new Vec3(point.x, WalkNodeEvaluator.getFloorLevel(mob.level(), feet), point.z);
    }

    private static double distance(PathfinderMob mob, @Nullable Path path, LivingEntity target) {
        return path == null || path.getEndNode() == null ? Double.POSITIVE_INFINITY
                : standingPoint(mob, path, path.getNodeCount() - 1).distanceToSqr(target.position());
    }

    private static Vec3 entityPosition(PathfinderMob mob, BlockPos feet) {
        double offset = (int)(mob.getBbWidth() + 1F) * .5D;
        return onFloor(mob, new Vec3(feet.getX() + offset, feet.getY(), feet.getZ() + offset));
    }

    private static boolean clearNextNodes(PathfinderMob mob, Path path) {
        for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 6); i++) {
            Vec3 point = standingPoint(mob, path, i);
            if (!loadedBody(mob, point) || !mob.level().noCollision(mob, bodyAt(mob, point))) return false;
            for (BlockPos pos : new BlockPos[]{path.getNode(i).asBlockPos(), path.getNode(i).asBlockPos().above()}) {
                BlockState state = mob.level().getBlockState(pos);
                if ((state.getBlock() instanceof DoorBlock || SourceBreakPortalGoal.woodenPortal(state))
                        && state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN)) return false;
            }
        }
        return true;
    }

    private static AABB bodyAt(PathfinderMob mob, Vec3 point) {
        return mob.getBoundingBox().move(point.subtract(mob.position())).deflate(.001D);
    }

    private static boolean loadedBody(PathfinderMob mob, Vec3 point) {
        AABB body = bodyAt(mob, point);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY - 1D, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (mob.level().isOutsideBuildHeight(pos) || !mob.level().hasChunkAt(pos)) return false;
        }
        return true;
    }

    private static boolean standingPosition(PathfinderMob mob, BlockPos feet) {
        Vec3 point = entityPosition(mob, feet);
        if (!loadedBody(mob, point)) return false;
        AABB body = bodyAt(mob, point);
        return mob.level().noCollision(mob, body) && !mob.level().containsAnyLiquid(body)
                && !mob.level().noCollision(mob, body.move(0D, -.0625D, 0D));
    }

    /** Fresh native evaluator avoids navigation's cached same-target partial path. No chunk is loaded. */
    @Nullable static Path find(PathfinderMob mob, Set<BlockPos> targets, int accuracy, boolean detour) {
        BlockPos start = mob.blockPosition();
        Set<BlockPos> loaded = new LinkedHashSet<>();
        for (BlockPos target : targets) {
            if (start.distSqr(target) <= RANGE * RANGE && mob.level().hasChunkAt(target)) loaded.add(target);
        }
        if (loaded.isEmpty()) return null;
        PathNavigationRegion region = new PathNavigationRegion(mob.level(), start.offset(-RANGE - 8, -RANGE - 8, -RANGE - 8),
                start.offset(RANGE + 8, RANGE + 8, RANGE + 8)) {
            @Override public BlockState getBlockState(BlockPos pos) {
                return !mob.level().hasChunkAt(pos) || isOutsideBuildHeight(pos)
                        ? Blocks.BEDROCK.defaultBlockState() : super.getBlockState(pos);
            }
        };
        WalkNodeEvaluator nodes = new WalkNodeEvaluator() {
            @Override public int getNeighbors(Node[] neighbors, Node node) {
                int count = super.getNeighbors(neighbors, node), accepted = 0;
                for (int i = 0; i < count; i++) {
                    Node next = neighbors[i];
                    if (Math.abs(next.x - start.getX()) <= RANGE && Math.abs(next.y - start.getY()) <= RANGE
                            && Math.abs(next.z - start.getZ()) <= RANGE) neighbors[accepted++] = next;
                }
                return accepted;
            }
        };
        nodes.setCanOpenDoors(false);
        nodes.setCanPassDoors(true);
        nodes.setCanFloat(mob.getNavigation().getNodeEvaluator().canFloat());
        Path path = new PathFinder(nodes, detour ? DETOUR_NODES : MAX_NODES)
                .findPath(region, mob, loaded, detour ? DETOUR_LENGTH * 2 : RANGE, accuracy, 1F);
        if (path == null) return null;
        Vec3 previous = mob.position();
        double length = 0D;
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (Math.abs(node.x - start.getX()) > RANGE || Math.abs(node.y - start.getY()) > RANGE
                    || Math.abs(node.z - start.getZ()) > RANGE || !mob.level().hasChunkAt(node.asBlockPos())
                    || node.type == PathType.LAVA || node.type == PathType.DAMAGE_FIRE || node.type == PathType.DAMAGE_OTHER
                    || node.type == PathType.DAMAGE_CAUTIOUS || node.type == PathType.POWDER_SNOW) return null;
            Vec3 point = standingPoint(mob, path, i);
            if (!loadedBody(mob, point) || !mob.level().noCollision(mob, bodyAt(mob, point))) return null;
            length += previous.distanceTo(point);
            previous = point;
        }
        // Native exploration needs room to try alternatives; the accepted route itself stays bounded.
        if (detour && length > DETOUR_LENGTH) return null;
        return path;
    }
}
