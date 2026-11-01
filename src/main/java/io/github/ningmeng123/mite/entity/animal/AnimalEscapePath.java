package io.github.ningmeng123.mite.entity.animal;

import io.github.ningmeng123.mite.rules.AnimalEscapeRules;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/** Bounded terrain-aware escape planning, prioritizing immediate separation. @author NingMeng123 */
public final class AnimalEscapePath {
    private static final int SEARCH_RANGE = 48;
    private static final double EARLY_LENGTH = 6D;
    private static final int[] DISTANCES = {16, 24, 24, 24, 16, 16, 8, 8, 8, 4, 4, 4};
    private static final double[] ANGLES = {0, 0, 30, -30, 60, -60, 0, 90, -90, 0, 135, -135};

    private AnimalEscapePath() {}

    static int candidateCount() { return DISTANCES.length; }

    /** The same ordered destinations are also consumed by the paced flee planner. */
    @Nullable static BlockPos candidate(PathfinderMob mob, LivingEntity threat, Vec3 origin, Vec3 danger, int index) {
        double dx=origin.x-danger.x, dz=origin.z-danger.z, magnitude=Math.hypot(dx,dz);
        if(magnitude<.001D) {
            Vec3 facing=threat.getLookAngle();dx=-facing.x;dz=-facing.z;magnitude=Math.hypot(dx,dz);
            if(magnitude<.001D) {dx=1D;dz=0D;magnitude=1D;}
        }
        dx/=magnitude;dz/=magnitude;
        double angle=Math.toRadians(ANGLES[index]),cosine=Math.cos(angle),sine=Math.sin(angle);
        BlockPos position=BlockPos.containing(origin.x+(dx*cosine-dz*sine)*DISTANCES[index],origin.y,
                origin.z+(dx*sine+dz*cosine)*DISTANCES[index]);
        return mob.level().hasChunkAt(position)?SourceMovement032Goal.findFoot(mob,position):null;
    }

    /** Searches only loaded terrain and scores the route the animal will actually walk. */
    @Nullable public static Path find(PathfinderMob mob, LivingEntity threat) {
        if (!threat.isAlive() || mob.level() != threat.level()) return null;
        Vec3 origin = mob.position(), danger = threat.position();
        double dx = origin.x - danger.x, dz = origin.z - danger.z;
        double magnitude = Math.hypot(dx, dz);
        if (magnitude < .001D) {
            Vec3 facing = threat.getLookAngle();
            dx = -facing.x; dz = -facing.z; magnitude = Math.hypot(dx, dz);
            if (magnitude < .001D) { dx = 1D; dz = 0D; magnitude = 1D; }
        }
        dx /= magnitude; dz /= magnitude;
        Set<BlockPos> visited = new HashSet<>();
        Path selected = null;
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < DISTANCES.length; i++) {
            double angle = Math.toRadians(ANGLES[i]), cosine = Math.cos(angle), sine = Math.sin(angle);
            BlockPos candidate = BlockPos.containing(origin.x + (dx * cosine - dz * sine) * DISTANCES[i],
                    origin.y, origin.z + (dx * sine + dz * cosine) * DISTANCES[i]);
            if (!mob.level().hasChunkAt(candidate)) continue;
            BlockPos destination = SourceMovement032Goal.findFoot(mob, candidate);
            if (!visited.add(destination)) continue;
            Path path = SourceMovement032Goal.pathTo(mob, destination, SEARCH_RANGE);
            double score = score(mob, path, origin, danger);
            if (score > best) { best = score; selected = path; }
            // No valid route can exceed 10.4: both distance gains are bounded by distance walked.
            // Flat open ground normally needs just this first native path search.
            if (best >= AnimalEscapeRules.MAX_SCORE - .05D) break;
        }
        // createPath records its last requested target; preserve the selected target for native recomputation.
        if (selected != null && !selected.getTarget().equals(mob.getNavigation().getTargetPos())) {
            Path restored = SourceMovement032Goal.pathTo(mob, selected.getTarget(), SEARCH_RANGE);
            if (score(mob, restored, origin, danger) > Double.NEGATIVE_INFINITY) return restored;
        }
        return selected;
    }

    private static double distance(Vec3 point, Vec3 danger) {
        return point.distanceTo(danger);
    }

    private static double segmentDistance(Vec3 from, Vec3 to, Vec3 danger) {
        Vec3 movement = to.subtract(from);
        double lengthSqr = movement.lengthSqr();
        if (lengthSqr < .000001D) return distance(from, danger);
        double fraction = Math.max(0D, Math.min(1D, danger.subtract(from).dot(movement) / lengthSqr));
        return distance(from.lerp(to, fraction), danger);
    }

    private static boolean hazardous(PathfinderMob mob, Node node) {
        return node.costMalus < 0F || node.type == PathType.DAMAGE_OTHER || node.type == PathType.POWDER_SNOW
                || !mob.fireImmune() && (node.type == PathType.LAVA || node.type == PathType.DAMAGE_FIRE);
    }

    /** Native node malus remains authoritative; liquid and elevation also take longer to traverse. */
    private static double travelCost(PathfinderMob mob, Node node, double segment, double rise) {
        double malus = Math.max(0D, Math.max(node.costMalus, mob.getPathfindingMalus(node.type)));
        double fluid = node.type == PathType.WATER || node.type == PathType.WATER_BORDER ? .8D * segment : 0D;
        double cautious = node.type == PathType.DAMAGE_CAUTIOUS ? 4D : 0D;
        return segment + .5D * malus + fluid + cautious + Math.max(0D, rise) * .5D;
    }

    static double score(PathfinderMob mob, @Nullable Path path, Vec3 origin, Vec3 danger) {
        return score(mob,path,origin,danger,path==null?0:path.getNextNodeIndex());
    }

    /** Ignore only waypoints that native navigation has already reached, without changing the path cursor. */
    static double score(PathfinderMob mob, @Nullable Path path, Vec3 origin, Vec3 danger, int firstUnreachedNode) {
        if (path == null || path.isDone() || path.getEndNode() == null) return Double.NEGATIVE_INFINITY;
        double initial = distance(origin, danger), minimum = initial;
        double length = 0D, cost = 0D, earlyLength = 0D, earlyDistance = initial;
        Vec3 previous = origin;
        for (int i = Math.max(path.getNextNodeIndex(),firstUnreachedNode); i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (!mob.level().hasChunkAt(node.asBlockPos())) return Double.NEGATIVE_INFINITY;
            Vec3 point = path.getEntityPosAtNode(mob, i);
            double segment = previous.distanceTo(point);
            // The first node can be the animal's current hazardous block; it must still be able to leave it.
            if ((i > path.getNextNodeIndex() || segment > .75D) && hazardous(mob, node)
                    || previous.y - point.y > 3D) return Double.NEGATIVE_INFINITY;
            minimum = Math.min(minimum, segmentDistance(previous, point, danger));
            cost += travelCost(mob, node, segment, point.y - previous.y);
            if (length < EARLY_LENGTH && segment > 0D) {
                double fraction = Math.min(1D, (EARLY_LENGTH - length) / segment);
                earlyDistance = distance(previous.lerp(point, fraction), danger);
                earlyLength = Math.min(EARLY_LENGTH, length + segment);
            }
            length += segment;
            previous = point;
        }
        // Partial native paths are usable only when their real endpoint makes safe progress.
        return AnimalEscapeRules.score(length, cost, initial, distance(previous, danger),
                earlyLength, earlyDistance - initial, minimum);
    }
}
