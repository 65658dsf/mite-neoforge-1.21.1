package io.github.ningmeng123.mite.entity;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.item.ItemStack;

/** Real walking routes to reachable attack positions, including routes that first lead away. @author NingMeng123 */
public final class MeleePursuitPaths {
    private static final int RANGE = 48;
    private static final int MAX_NODES = 2048;
    private static final int DETOUR_LENGTH = 96;
    private static final int DETOUR_NODES = 8192;
    private static final int COMPLETE_REUSE_TICKS = 10;
    public static final int MAX_FAILURE_RETRY_TICKS = 80;
    public static final int RETRY_STAGGER_TICKS = 10;
    private static final Map<PathfinderMob, SearchCache> SEARCHES = new WeakHashMap<>();

    /** Values never retain their weak entity key or its level. Server thread only. */
    private static final class SearchCache {
        long searches, cacheHits, searchedAt = Long.MIN_VALUE, retryAt = Long.MIN_VALUE, completeUntil;
        int targetId, failures;
        ResourceKey<Level> dimension;
        Vec3 origin, destination;
        float width, height, step;
        boolean floats;
        ItemStack held = ItemStack.EMPTY;
        Path partial, complete;

        boolean matches(PathfinderMob mob, LivingEntity target, long now) {
            return origin != null && now >= searchedAt && dimension == mob.level().dimension()
                    && targetId == target.getId() && origin.distanceToSqr(mob.position()) < 1D
                    && destination.distanceToSqr(target.position()) < 1D
                    && width == mob.getBbWidth() && height == mob.getBbHeight() && step == mob.maxUpStep()
                    && floats == mob.getNavigation().getNodeEvaluator().canFloat()
                    && ItemStack.isSameItemSameComponents(held, mob.getMainHandItem());
        }

        void clear() {
            failures = 0; partial = null; complete = null; held = ItemStack.EMPTY;
            origin = null; destination = null; retryAt = Long.MIN_VALUE;
        }

        void remember(PathfinderMob mob, LivingEntity target, long now) {
            searchedAt = now; targetId = target.getId(); dimension = mob.level().dimension();
            origin = mob.position(); destination = target.position();
            width = mob.getBbWidth(); height = mob.getBbHeight(); step = mob.maxUpStep();
            floats = mob.getNavigation().getNodeEvaluator().canFloat();
            held = mob.getMainHandItem().copy();
        }

        Path rememberComplete(PathfinderMob mob, LivingEntity target, long now, Path path) {
            clear(); remember(mob, target, now);
            complete = path; completeUntil = now + COMPLETE_REUSE_TICKS;
            return path;
        }
    }

    /** Read-only counters for comparing actual native queries with reused failures. */
    public record SearchStats(long searches, long cacheHits, int failures, long retryAt) {}

    public static SearchStats searchStats(PathfinderMob mob) {
        SearchCache cache = SEARCHES.get(mob);
        return cache == null ? new SearchStats(0, 0, 0, Long.MIN_VALUE)
                : new SearchStats(cache.searches, cache.cacheHits, cache.failures, cache.retryAt);
    }

    public static void invalidate(PathfinderMob mob) {
        SearchCache cache = SEARCHES.get(mob);
        if (cache != null) cache.clear();
    }

    static long failedRetryAt(PathfinderMob mob) {
        SearchCache cache = SEARCHES.get(mob);
        return cache == null || cache.failures == 0 ? Long.MIN_VALUE : cache.retryAt;
    }

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
                && clearNextNodes(mob, active)) { invalidate(mob); return active; }
        SearchCache cache = SEARCHES.computeIfAbsent(mob, ignored -> new SearchCache());
        long now = mob.level().getGameTime();
        boolean matches = cache.matches(mob, target, now);
        // Discovery probes do not start navigation. Share their complete route with the
        // subsequent target/attack/digging goals instead of searching again on acquisition.
        if (matches && cache.complete != null && now < cache.completeUntil
                && reaches(mob, cache.complete, target) && clearNextNodes(mob, cache.complete)) {
            ++cache.cacheHits;
            return cache.complete;
        }
        boolean sameFailure = matches && cache.failures > 0;
        if (sameFailure && now < cache.retryAt) {
            ++cache.cacheHits;
            return cache.partial;
        }
        if (!sameFailure) cache.clear();
        Path direct = find(mob, Set.of(target.blockPosition()), 1, false);
        if (reaches(mob, direct, target)) return cache.rememberComplete(mob, target, now, direct);
        // Accuracy zero only helps when the looser goal was reached outside physical attack range.
        // A failed looser search need not explore the same region again before the larger detour.
        if (direct != null && direct.canReach()) {
            Path exact = find(mob, Set.of(target.blockPosition()), 0, false);
            if (reaches(mob, exact, target)) return cache.rememberComplete(mob, target, now, exact);
            if (distance(mob, exact, target) < distance(mob, direct, target)) direct = exact;
        }

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
            if (reaches(mob, detour, target)) return cache.rememberComplete(mob, target, now, detour);
        }
        cache.failures = Math.min(3, cache.failures + 1);
        cache.remember(mob, target, now);
        cache.retryAt = now + Math.min(MAX_FAILURE_RETRY_TICKS, 20 << (cache.failures - 1))
                + Math.floorMod(mob.getId(), RETRY_STAGGER_TICKS);
        cache.complete = null; cache.partial = direct;
        return direct;
    }

    public static boolean reaches(PathfinderMob mob, @Nullable Path path, LivingEntity target) {
        return path != null && path.canReach() && path.getEndNode() != null
                && SourceMeleeGoal.canStrikeFrom(mob, target, standingPoint(mob, path, path.getNodeCount() - 1));
    }

    /** Hidden first acquisition must verify the entire current route, including a cached far end. */
    public static boolean completeRouteClear(PathfinderMob mob, @Nullable Path path, LivingEntity target) {
        if (!reaches(mob, path, target)) return false;
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            Vec3 point = standingPoint(mob, path, i);
            if (!loadedBody(mob, point)) return false;
            AABB body = bodyAt(mob, point);
            if (!mob.level().noCollision(mob, body)) return false;
            // Recheck supporting terrain too: removing a bridge must not leave an imaginary
            // complete ground route. Native water nodes retain their existing swimming rules.
            if (mob.level().noCollision(mob, body.move(0D, -.0625D, 0D))
                    && path.getNode(i).type != PathType.WATER && path.getNode(i).type != PathType.WATER_BORDER) return false;
        }
        return true;
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
        ++SEARCHES.computeIfAbsent(mob, ignored -> new SearchCache()).searches;
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
