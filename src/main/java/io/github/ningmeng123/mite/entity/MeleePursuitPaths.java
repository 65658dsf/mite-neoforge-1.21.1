package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.config.MiteConfig;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.item.ItemStack;

/** Real walking routes to reachable attack positions, including routes that first lead away. @author NingMeng123 */
public final class MeleePursuitPaths {
    private static final int RANGE = 48;
    private static final int MAX_NODES = 2048;
    private static final int DETOUR_LENGTH = 96;
    private static final int DETOUR_NODES = 8192;
    private static final int COMPLETE_REUSE_TICKS = 10;
    public static final int MIN_MOVING_RETRY_TICKS = 10;
    public static final int MAX_FAILURE_RETRY_TICKS = 80;
    public static final int RETRY_STAGGER_TICKS = 10;
    private static final Map<PathfinderMob, SearchCache> SEARCHES = new WeakHashMap<>();

    /** Values never retain their weak entity key or its level. Server thread only. */
    private static final class SearchCache {
        long searches, cacheHits, searchedAt = Long.MIN_VALUE, retryAt = Long.MIN_VALUE, completeUntil;
        long movingRetryAt = Long.MIN_VALUE;
        int targetId, failures;
        ResourceKey<Level> dimension;
        Vec3 origin, destination;
        float width, height, step;
        boolean floats;
        boolean vanillaEndpoint;
        boolean opensDoors, passesDoors;
        ItemStack held = ItemStack.EMPTY;
        Path partial, complete;

        boolean samePursuit(PathfinderMob mob, LivingEntity target, long now, boolean vanilla) {
            return origin != null && now >= searchedAt && dimension == mob.level().dimension()
                    && targetId == target.getId() && vanillaEndpoint == vanilla
                    && (!vanilla || opensDoors == mob.getNavigation().getNodeEvaluator().canOpenDoors()
                    && passesDoors == mob.getNavigation().getNodeEvaluator().canPassDoors())
                    && width == mob.getBbWidth() && height == mob.getBbHeight() && step == mob.maxUpStep()
                    && floats == mob.getNavigation().getNodeEvaluator().canFloat()
                    && ItemStack.isSameItemSameComponents(held, mob.getMainHandItem());
        }

        boolean matches(PathfinderMob mob, LivingEntity target, long now, boolean vanilla) {
            return samePursuit(mob, target, now, vanilla) && origin.distanceToSqr(mob.position()) < 1D
                    && destination.distanceToSqr(target.position()) < 1D;
        }

        void clear() {
            failures = 0; partial = null; complete = null; held = ItemStack.EMPTY;
            origin = null; destination = null; retryAt = Long.MIN_VALUE;
            movingRetryAt = Long.MIN_VALUE;
        }

        void remember(PathfinderMob mob, LivingEntity target, long now) {
            searchedAt = now; targetId = target.getId(); dimension = mob.level().dimension();
            origin = mob.position(); destination = target.position();
            width = mob.getBbWidth(); height = mob.getBbHeight(); step = mob.maxUpStep();
            floats = mob.getNavigation().getNodeEvaluator().canFloat();
            opensDoors = mob.getNavigation().getNodeEvaluator().canOpenDoors();
            passesDoors = mob.getNavigation().getNodeEvaluator().canPassDoors();
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
        AsyncMeleePursuit.cancel(mob);
        PathSearchBudget.cancel(mob);
        SearchCache cache = SEARCHES.get(mob);
        if (cache != null) cache.clear();
    }

    static long failedRetryAt(PathfinderMob mob) {
        SearchCache cache = SEARCHES.get(mob);
        return cache == null || cache.failures == 0 ? Long.MIN_VALUE : cache.retryAt;
    }

    static long movingRetryAt(PathfinderMob mob) {
        SearchCache cache = SEARCHES.get(mob);
        return cache == null ? Long.MIN_VALUE : cache.movingRetryAt;
    }

    private MeleePursuitPaths() {}

    private static int movingRetryTicks() {
        return MiteConfig.pursuitSearchBudgetEnabled() ? 20 : MIN_MOVING_RETRY_TICKS;
    }

    public static boolean applies(PathfinderMob mob) {
        return R196MonsterAi.managed(mob) && mob.getNavigation() instanceof GroundPathNavigation
                && mob.getTarget() instanceof Player target && target.isAlive() && target.level() == mob.level();
    }

    public static boolean inRange(PathfinderMob mob, LivingEntity target) {
        return mob.blockPosition().distSqr(target.blockPosition()) <= RANGE * RANGE;
    }

    @Nullable public static Path find(PathfinderMob mob, LivingEntity target, @Nullable Path previous) {
        return find(mob, target, previous, false);
    }

    /** Same loaded-terrain search, with the native goal's actual attack volume and weapon limits. */
    @Nullable public static Path findVanilla(PathfinderMob mob, LivingEntity target, @Nullable Path previous) {
        return find(mob, target, previous, true);
    }

    @Nullable private static Path find(PathfinderMob mob, LivingEntity target, @Nullable Path previous, boolean vanilla) {
        // Existing checkpoints must be invalidated even if a fast path or an unloaded target
        // means that this call never reaches another native query. New snapshots stay lazy.
        if (AsyncMeleePursuit.state(mob) != null) AsyncMeleePursuit.begin(mob, target, vanilla);
        try (PathSearchBudget.Decision ignored = PathSearchBudget.open(mob)) {
            Path result = findPursuit(mob, target, previous, vanilla, true);
            if (AsyncMeleePursuit.state(mob) != null && reaches(mob, result, target, vanilla)
                    && !completeRouteClear(mob, result, target, vanilla)) {
                // Terrain or support may have changed during a delayed query. Recheck natively
                // now instead of installing a stale route or classifying it as a failed pursuit.
                invalidate(mob);
                result = findPursuit(mob, target, null, vanilla, false);
                if (!completeRouteClear(mob, result, target, vanilla)) { invalidate(mob); return null; }
            }
            AsyncMeleePursuit.complete(mob, AsyncMeleePursuit.state(mob));
            PathSearchBudget.cancel(mob);
            return result;
        } catch (AsyncMeleePursuit.Pending pending) {
            return previous != null && completeRouteClear(mob, previous, target, vanilla) ? previous : null;
        } catch (RuntimeException | Error failure) {
            AsyncMeleePursuit.cancel(mob);
            PathSearchBudget.cancel(mob);
            throw failure;
        }
    }

    /** Waiting is distinct from a failed route, including after the worker has finished but before consumption. */
    public static boolean pending(PathfinderMob mob) { return AsyncMeleePursuit.pending(mob) || PathSearchBudget.pending(mob); }

    @Nullable private static Path findPursuit(PathfinderMob mob, LivingEntity target, @Nullable Path previous, boolean vanilla, boolean asynchronous) {
        SearchCache cache = SEARCHES.computeIfAbsent(mob, ignored -> new SearchCache());
        if (cache.targetId != target.getId() || cache.vanillaEndpoint != vanilla
                || vanilla && (cache.opensDoors != mob.getNavigation().getNodeEvaluator().canOpenDoors()
                || cache.passesDoors != mob.getNavigation().getNodeEvaluator().canPassDoors())) {
            cache.clear();
            cache.targetId = target.getId(); cache.vanillaEndpoint = vanilla;
            cache.opensDoors = mob.getNavigation().getNodeEvaluator().canOpenDoors();
            cache.passesDoors = mob.getNavigation().getNodeEvaluator().canPassDoors();
            previous = null;
        }
        Path active = mob.getNavigation().getPath();
        // Other goals may supply a path that assumes doors can be opened. Only retain our verified route.
        if (active == previous && !mob.getNavigation().isDone() && !mob.getNavigation().isStuck() && reaches(mob, active, target, vanilla)
                && clearNextNodes(mob, active, vanilla)) { invalidate(mob); return active; }
        long now = mob.level().getGameTime();
        boolean matches = cache.matches(mob, target, now, vanilla);
        // Discovery probes do not start navigation. Share their complete route with the
        // subsequent target/attack/digging goals instead of searching again on acquisition.
        if (matches && cache.complete != null && now < cache.completeUntil
                && reaches(mob, cache.complete, target, vanilla) && clearNextNodes(mob, cache.complete, vanilla)) {
            ++cache.cacheHits;
            return cache.complete;
        }
        boolean sameFailure = matches && cache.failures > 0;
        if (sameFailure && now < cache.retryAt) {
            ++cache.cacheHits;
            return cache.partial;
        }
        // Moving players used to reset failure backoff on every block crossed. Keep a
        // short real-query interval for a failed pursuit, even across geometry changes.
        // Never hand a moved monster an unverified stale path; other goals can retry soon.
        if (!matches && cache.failures > 0 && cache.samePursuit(mob, target, now, vanilla)
                && now - cache.searchedAt < movingRetryTicks()) {
            ++cache.cacheHits;
            cache.movingRetryAt = cache.searchedAt + movingRetryTicks();
            return null;
        }
        cache.movingRetryAt = Long.MIN_VALUE;
        if (!sameFailure) cache.clear();
        Path direct = find(mob, Set.of(target.blockPosition()), 1, false, vanilla, asynchronous ? target : null);
        if (reaches(mob, direct, target, vanilla)) return cache.rememberComplete(mob, target, now, direct);
        // Accuracy zero only helps when the looser goal was reached outside physical attack range.
        // A failed looser search need not explore the same region again before the larger detour.
        if (direct != null && direct.canReach()) {
            Path exact = find(mob, Set.of(target.blockPosition()), 0, false, vanilla, asynchronous ? target : null);
            if (reaches(mob, exact, target, vanilla)) return cache.rememberComplete(mob, target, now, exact);
            if (distance(mob, exact, target) < distance(mob, direct, target)) direct = exact;
        }

        // The player's feet may occupy a fence, wall or other unreachable block. Search actual
        // attack positions together, so the closest failed endpoint cannot hide a side entrance.
        Set<BlockPos> positions = new LinkedHashSet<>();
        BlockPos center = target.blockPosition();
        int radius = vanilla ? VanillaMeleePursuit.attackPositionRadius(mob, target) : 2;
        int below = vanilla ? Math.max(2, (int)Math.ceil(mob.getBbHeight())) : 2;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) for (int y = -below; y <= 1; y++) {
            BlockPos feet = center.offset(x, y, z);
            if (!mob.isWithinRestriction(feet) || !standingPosition(mob, feet, vanilla)) continue;
            Vec3 point = entityPosition(mob, feet);
            if (canStrikeFrom(mob, target, point, vanilla)) positions.add(feet);
        }
        if (!positions.isEmpty()) {
            Path detour = find(mob, positions, 0, true, vanilla, asynchronous ? target : null);
            if (reaches(mob, detour, target, vanilla)) return cache.rememberComplete(mob, target, now, detour);
        }
        cache.failures = Math.min(3, cache.failures + 1);
        cache.remember(mob, target, now);
        cache.retryAt = now + Math.min(MiteConfig.pursuitSearchBudgetEnabled() ? 160 : MAX_FAILURE_RETRY_TICKS,
                (MiteConfig.pursuitSearchBudgetEnabled() ? 40 : 20) << (cache.failures - 1))
                + Math.floorMod(mob.getId(), RETRY_STAGGER_TICKS);
        cache.complete = null; cache.partial = direct;
        return direct;
    }

    public static boolean reaches(PathfinderMob mob, @Nullable Path path, LivingEntity target) {
        return reaches(mob, path, target, false);
    }

    public static boolean reachesVanilla(PathfinderMob mob, @Nullable Path path, LivingEntity target) {
        return reaches(mob, path, target, true);
    }

    private static boolean reaches(PathfinderMob mob, @Nullable Path path, LivingEntity target, boolean vanilla) {
        return path != null && path.canReach() && path.getEndNode() != null
                && canStrikeFrom(mob, target, standingPoint(mob, path, path.getNodeCount() - 1), vanilla);
    }

    private static boolean canStrikeFrom(PathfinderMob mob, LivingEntity target, Vec3 feet, boolean vanilla) {
        return vanilla ? VanillaMeleePursuit.canStrikeFrom(mob, target, feet) : SourceMeleeGoal.canStrikeFrom(mob, target, feet);
    }

    /** Hidden first acquisition must verify the entire current route, including a cached far end. */
    public static boolean completeRouteClear(PathfinderMob mob, @Nullable Path path, LivingEntity target) {
        return completeRouteClear(mob, path, target, false);
    }

    public static boolean completeVanillaRouteClear(PathfinderMob mob, @Nullable Path path, LivingEntity target) {
        return completeRouteClear(mob, path, target, true);
    }

    private static boolean completeRouteClear(PathfinderMob mob, @Nullable Path path, LivingEntity target, boolean vanilla) {
        if (!reaches(mob, path, target, vanilla)) return false;
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            Vec3 point = standingPoint(mob, path, i);
            if (!loadedBody(mob, point) || !doorPolicyAllows(mob, path.getNode(i).asBlockPos(), vanilla)) return false;
            AABB body = bodyAt(mob, point);
            if (!collisionFree(mob, body, vanilla)) return false;
            // Recheck supporting terrain too: removing a bridge must not leave an imaginary
            // complete ground route. Native water nodes retain their existing swimming rules.
            if (collisionFree(mob, body.move(0D, -.0625D, 0D), vanilla)
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

    private static boolean clearNextNodes(PathfinderMob mob, Path path, boolean vanilla) {
        for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 6); i++) {
            Vec3 point = standingPoint(mob, path, i);
            if (!loadedBody(mob, point) || !doorPolicyAllows(mob, path.getNode(i).asBlockPos(), vanilla)
                    || !collisionFree(mob, bodyAt(mob, point), vanilla)) return false;
            for (BlockPos pos : new BlockPos[]{path.getNode(i).asBlockPos(), path.getNode(i).asBlockPos().above()}) {
                BlockState state = mob.level().getBlockState(pos);
                if ((state.getBlock() instanceof DoorBlock || SourceBreakPortalGoal.woodenPortal(state))
                        && state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN)
                        && !mayOpenDoor(mob, state, vanilla)) return false;
            }
        }
        return true;
    }

    private static AABB bodyAt(PathfinderMob mob, Vec3 point) {
        return mob.getBoundingBox().move(point.subtract(mob.position())).deflate(.001D);
    }

    private static boolean mayOpenDoor(PathfinderMob mob, BlockState state, boolean vanilla) {
        return vanilla && mob.getNavigation().getNodeEvaluator().canOpenDoors()
                && mob.getNavigation().getNodeEvaluator().canPassDoors() && DoorBlock.isWoodenDoor(state);
    }

    private static boolean doorPolicyAllows(PathfinderMob mob, BlockPos feet, boolean vanilla) {
        if (!vanilla) return true;
        for (BlockPos position : new BlockPos[]{feet, feet.above()}) {
            if (!mob.level().hasChunkAt(position)) return false;
            BlockState state = mob.level().getBlockState(position);
            if (state.getBlock() instanceof DoorBlock) {
                if (!mob.getNavigation().getNodeEvaluator().canPassDoors()) return false;
                if (!state.getValue(DoorBlock.OPEN) && !mayOpenDoor(mob, state, true)) return false;
            }
        }
        return true;
    }

    /** Native open-door routes keep the door node and test the real open shape, never air. */
    private static boolean collisionFree(PathfinderMob mob, AABB body, boolean vanilla) {
        if (!vanilla || !mob.getNavigation().getNodeEvaluator().canOpenDoors()
                || !mob.getNavigation().getNodeEvaluator().canPassDoors()) return mob.level().noCollision(mob, body);
        var minimum = BlockPos.containing(body.minX - 1D, body.minY - 1D, body.minZ - 1D);
        var maximum = BlockPos.containing(body.maxX + 1D, body.maxY + 1D, body.maxZ + 1D);
        boolean hasClosedDoor = false;
        for (BlockPos position : BlockPos.betweenClosed(minimum, maximum)) {
            if (mob.level().isOutsideBuildHeight(position) || !mob.level().hasChunkAt(position)) continue;
            BlockState state = mob.level().getBlockState(position);
            if (mayOpenDoor(mob, state, true) && !state.getValue(DoorBlock.OPEN)) { hasClosedDoor = true; break; }
        }
        if (!hasClosedDoor) return mob.level().noCollision(mob, body);
        if (!mob.level().getEntityCollisions(mob, body).isEmpty()) return false;
        var volume = Shapes.create(body);
        var border = mob.level().getWorldBorder();
        if (border.isInsideCloseToBorder(mob, body)
                && Shapes.joinIsNotEmpty(border.getCollisionShape(), volume, BooleanOp.AND)) return false;
        var context = CollisionContext.of(mob);
        for (BlockPos position : BlockPos.betweenClosed(minimum, maximum)) {
            if (mob.level().isOutsideBuildHeight(position)) continue;
            if (!mob.level().hasChunkAt(position)) return false;
            BlockState state = mob.level().getBlockState(position);
            if (mayOpenDoor(mob, state, true)) state = state.setValue(DoorBlock.OPEN, true);
            var shape = state.getCollisionShape(mob.level(), position, context).move(position.getX(), position.getY(), position.getZ());
            if (Shapes.joinIsNotEmpty(shape, volume, BooleanOp.AND)) return false;
        }
        return true;
    }

    private static boolean loadedBody(PathfinderMob mob, Vec3 point) {
        AABB body = bodyAt(mob, point);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY - 1D, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (mob.level().isOutsideBuildHeight(pos) || !mob.level().hasChunkAt(pos)) return false;
        }
        return true;
    }

    private static boolean standingPosition(PathfinderMob mob, BlockPos feet, boolean vanilla) {
        Vec3 point = entityPosition(mob, feet);
        if (!loadedBody(mob, point)) return false;
        AABB body = bodyAt(mob, point);
        return collisionFree(mob, body, vanilla) && !mob.level().containsAnyLiquid(body)
                && !collisionFree(mob, body.move(0D, -.0625D, 0D), vanilla);
    }

    /** Cautious includes harmless cells beside dripstone, not just the actual obstacle. */
    private static boolean cautiousPositionFits(PathfinderMob mob, BlockPos feet, boolean vanilla) {
        Vec3 point = entityPosition(mob, feet);
        if (!loadedBody(mob, point)) return false;
        AABB body = bodyAt(mob, point);
        if (!collisionFree(mob, body, vanilla)) return false;
        // A wither rose has no collision, but entering its occupied cell still causes damage.
        for (BlockPos position : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (mob.level().getBlockState(position).is(Blocks.WITHER_ROSE)) return false;
        }
        return true;
    }

    private static boolean forbiddenTerrain(PathType type) {
        return type == PathType.LAVA || type == PathType.DAMAGE_FIRE || type == PathType.DAMAGE_OTHER
                || type == PathType.POWDER_SNOW;
    }

    /** Fresh native evaluator avoids navigation's cached same-target partial path. No chunk is loaded. */
    @Nullable static Path find(PathfinderMob mob, Set<BlockPos> targets, int accuracy, boolean detour) {
        try (PathSearchBudget.Decision ignored = PathSearchBudget.open(mob)) {
            Path result = find(mob, targets, accuracy, detour, false, null);
            PathSearchBudget.cancel(mob);
            return result;
        } catch (AsyncMeleePursuit.Pending pending) {
            return null;
        } catch (RuntimeException | Error failure) {
            PathSearchBudget.cancel(mob);
            throw failure;
        }
    }

    @Nullable private static Path find(PathfinderMob mob, Set<BlockPos> targets, int accuracy, boolean detour, boolean vanilla, @Nullable LivingEntity pursuitTarget) {
        BlockPos start = mob.blockPosition();
        Set<BlockPos> loaded = new LinkedHashSet<>();
        for (BlockPos target : targets) {
            if (start.distSqr(target) <= RANGE * RANGE && mob.level().hasChunkAt(target)) loaded.add(target);
        }
        if (loaded.isEmpty()) {
            if (pursuitTarget != null) AsyncMeleePursuit.cancel(mob);
            return null;
        }
        var region = new LoadedPathNavigationRegion(mob.level(), start.offset(-RANGE - 8, -RANGE - 8, -RANGE - 8),
                start.offset(RANGE + 8, RANGE + 8, RANGE + 8));
        region.cautiousValidator(feet -> cautiousPositionFits(mob, feet, vanilla));
        PathfinderMob pursuitMob = mob;
        WalkNodeEvaluator nodes = new CachedWalkNodeEvaluator() {
            @Override public Set<PathType> getPathTypeWithinMobBB(PathfindingContext context, int x, int y, int z) {
                Set<PathType> types = super.getPathTypeWithinMobBB(context, x, y, z);
                // Keep the native cautious cost on safe neighboring cells. Actual obstructions
                // must participate in step-up and diagonal decisions before A* chooses a route.
                // Native getCachedPathType caches this result for this one bounded search.
                if (types.contains(PathType.DAMAGE_CAUTIOUS)) {
                    BlockPos feet = new BlockPos(x, y, z);
                    boolean fits = cautiousPositionFits(pursuitMob, feet, vanilla);
                    region.recordCautious(feet, fits);
                    if (!fits) types.add(PathType.BLOCKED);
                }
                return types;
            }

            @Override public int getNeighbors(Node[] neighbors, Node node) {
                int count = super.getNeighbors(neighbors, node), accepted = 0;
                for (int i = 0; i < count; i++) {
                    Node next = neighbors[i];
                    if (Math.abs(next.x - start.getX()) <= RANGE && Math.abs(next.y - start.getY()) <= RANGE
                            && Math.abs(next.z - start.getZ()) <= RANGE
                            && !forbiddenTerrain(next.type)) neighbors[accepted++] = next;
                }
                return accepted;
            }
        };
        nodes.setCanOpenDoors(vanilla && mob.getNavigation().getNodeEvaluator().canOpenDoors());
        nodes.setCanPassDoors(!vanilla || mob.getNavigation().getNodeEvaluator().canPassDoors());
        nodes.setCanFloat(mob.getNavigation().getNodeEvaluator().canFloat());
        int maxNodes = MiteConfig.pursuitSearchBudgetEnabled() ? (detour ? 4096 : 1024) : (detour ? DETOUR_NODES : MAX_NODES);
        float range = detour ? DETOUR_LENGTH * 2 : RANGE;
        Runnable countSearch = () -> ++SEARCHES.computeIfAbsent(mob, ignored -> new SearchCache()).searches;
        java.util.function.Supplier<Path> synchronous = () -> PathSearchBudget.synchronous(mob, () -> {
            countSearch.run();
            return new PathFinder(nodes, maxNodes).findPath(region, mob, loaded, range, accuracy, 1F);
        });
        Path path;
        if (pursuitTarget != null) AsyncMeleePursuit.begin(mob, pursuitTarget, vanilla);
        if (pursuitTarget != null) path = AsyncMeleePursuit.query(mob, region, nodes, loaded, maxNodes, range, accuracy, 1F,
                countSearch, synchronous);
        else path = synchronous.get();
        if (path == null) return null;
        Vec3 previous = mob.position();
        double length = 0D;
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (Math.abs(node.x - start.getX()) > RANGE || Math.abs(node.y - start.getY()) > RANGE
                    || Math.abs(node.z - start.getZ()) > RANGE || !mob.level().hasChunkAt(node.asBlockPos())
                    || forbiddenTerrain(node.type)) return null;
            Vec3 point = standingPoint(mob, path, i);
            if (!loadedBody(mob, point) || !doorPolicyAllows(mob, node.asBlockPos(), vanilla)
                    || !collisionFree(mob, bodyAt(mob, point), vanilla)) return null;
            length += previous.distanceTo(point);
            previous = point;
        }
        // Native exploration needs room to try alternatives; the accepted route itself stays bounded.
        if (detour && length > DETOUR_LENGTH) return null;
        return path;
    }
}
