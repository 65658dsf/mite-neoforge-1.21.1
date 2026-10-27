package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.ZombieRouteRules;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** Bounded pursuit compares real walking routes with verified, short ground-level openings. @author NingMeng123 */
public final class ZombieRoutePlanner {
    public static final int REPLAN_TICKS = 10;
    private static final int FAILED_REPLAN_TICKS = 40;
    private static final int MAX_BLOCKS = 8;
    private final PathfinderMob mob;
    private final R196DiggerGoal digger;
    private Plan cached;
    private long plannedAt = Long.MIN_VALUE;
    private Vec3 origin, destination;
    private LivingEntity prey;
    private ItemStack held = ItemStack.EMPTY;
    private boolean griefing, frenzy;
    private boolean attackPosition;
    @Nullable private BlockState openingState;
    private final java.util.LinkedHashMap<BlockPos,Long> rejected=new java.util.LinkedHashMap<>();

    public record Plan(@Nullable Path path, @Nullable BlockPos firstBlock, boolean portal, double walkingTicks, double breakingTicks) {
        public boolean completeWalkingRoute() { return Double.isFinite(walkingTicks); }
        public boolean choosesBreaking() { return firstBlock != null; }
    }

    public ZombieRoutePlanner(PathfinderMob mob, R196DiggerGoal digger) { this.mob = mob; this.digger = digger; }

    public void invalidate() {
        cached = null; attackPosition = false;
        MeleePursuitPaths.invalidate(mob);
    }

    /** A real protection veto is remembered briefly; speculative path queries never fire destroy events. */
    public void reject(BlockPos pos) {
        rejected.put(pos.immutable(),mob.level().getGameTime()+200L);
        while(rejected.size()>32)rejected.remove(rejected.keySet().iterator().next());
        invalidate();
    }

    public boolean rejected(BlockPos pos) {
        Long until=rejected.get(pos);if(until==null)return false;
        if(until<=mob.level().getGameTime()) {rejected.remove(pos);return false;}
        return true;
    }

    public static boolean applies(PathfinderMob mob) {
        return mob instanceof Zombie && R196MonsterAi.managed(mob) && mob.getTarget() instanceof Player player
                && player.isAlive() && player.level() == mob.level();
    }

    public Plan plan() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            invalidate();
            return new Plan(null, null, false, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        }
        long now = mob.level().getGameTime();
        boolean permitted = EventHooks.canEntityGrief(mob.level(), mob), frenzied = R196MonsterAi.frenzied(mob);
        // Equipment, permissions and an opening changing must also bypass the shared failed-route
        // backoff. Expiring an unchanged plan alone intentionally keeps that backoff intact.
        if (cached != null && (prey != target || griefing != permitted || frenzy != frenzied
                || !ItemStack.isSameItemSameComponents(held, mob.getMainHandItem())
                || cached.firstBlock() != null && !mob.level().getBlockState(cached.firstBlock()).equals(openingState)))
            invalidate();
        // All three pursuit goals ask for a plan even while a zombie can already attack. Checking
        // the actual weapon reach and physical rays avoids an otherwise unnecessary path search.
        if (SourceMeleeGoal.canStrike(mob, target)) {
            if (cached == null || !attackPosition || prey != target || !origin.equals(mob.position())
                    || griefing != permitted || frenzy != frenzied
                    || !ItemStack.isSameItemSameComponents(held, mob.getMainHandItem())) {
                remember(target, now, permitted, frenzied);
                attackPosition = true;
                cached = new Plan(new Path(java.util.List.of(new Node(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ())),
                        mob.blockPosition(), true), null, false, 0D, Double.POSITIVE_INFINITY);
            }
            return cached;
        }
        // A target can leave melee reach without moving a whole block. Never retain the zero-step
        // plan after that happens, even while the ordinary movement cache would still be valid.
        if (attackPosition) invalidate();
        long retryAt = MeleePursuitPaths.failedRetryAt(mob);
        long lifetime = cached != null && !cached.completeWalkingRoute() && !cached.choosesBreaking()
                ? Math.max(FAILED_REPLAN_TICKS + Math.floorMod(mob.getId(), MeleePursuitPaths.RETRY_STAGGER_TICKS),
                        retryAt > plannedAt ? retryAt - plannedAt : 0L) : REPLAN_TICKS;
        // A short movement deferral is not a failed search. Do not extend it into
        // the much longer failed-plan lifetime when the player stops moving.
        long movingRetryAt = MeleePursuitPaths.movingRetryAt(mob);
        if (movingRetryAt > plannedAt) lifetime = Math.min(lifetime, movingRetryAt - plannedAt);
        if (cached != null && prey == target && now >= plannedAt && now - plannedAt < lifetime
                && origin.distanceToSqr(mob.position()) < 1D && destination.distanceToSqr(target.position()) < 1D
                && griefing == permitted && frenzy == frenzied && ItemStack.isSameItemSameComponents(held, mob.getMainHandItem())
                && (cached.firstBlock() == null || mob.level().getBlockState(cached.firstBlock()).equals(openingState))) return cached;
        remember(target, now, permitted, frenzied);
        Path walk = MeleePursuitPaths.find(mob, target, cached == null ? null : cached.path());
        double walking = reaches(walk, target) ? pathTicks(walk) : Double.POSITIVE_INFINITY;
        Opening opening = permitted ? directOpening(target) : null;
        double breaking = opening == null ? Double.POSITIVE_INFINITY : opening.ticks();
        if (opening != null && ZombieRouteRules.preferBreaking(walking, breaking)) {
            boolean reachable = opening.portal() ? mob.distanceToSqr(opening.first().getCenter()) < 4D
                    : digger.canDestroy(opening.first(), true);
            Path approach = reachable ? new Path(java.util.List.of(new Node(mob.getBlockX(),mob.getBlockY(),mob.getBlockZ())),
                    mob.blockPosition(),true) : MeleePursuitPaths.movingRetryAt(mob) > now ? null : findPath(opening.approach(), 0);
            // A speculative opening never directs the mob through an intervening wall.
            if (reachable || approach != null && approach.canReach()) {
                openingState = mob.level().getBlockState(opening.first());
                return cached = new Plan(approach, opening.first(), opening.portal(), walking, breaking);
            }
        }
        return cached = new Plan(walk, null, false, walking, breaking);
    }

    private void remember(LivingEntity target, long now, boolean permitted, boolean frenzied) {
        prey = target; origin = mob.position(); destination = target.position(); plannedAt = now;
        held = mob.getMainHandItem().copy(); griefing = permitted; frenzy = frenzied;
        attackPosition = false; openingState = null;
    }

    /** Never loads a chunk, and closed portals are obstacles rather than imaginary open doorways. */
    @Nullable private Path findPath(BlockPos target, int reach) {
        return MeleePursuitPaths.find(mob, Set.of(target), reach, false);
    }

    private boolean reaches(@Nullable Path path, LivingEntity target) {
        return MeleePursuitPaths.reaches(mob, path, target);
    }

    private double speed() { return Math.max(.01D, mob.getAttributeValue(Attributes.MOVEMENT_SPEED)); }

    private double pathTicks(Path path) {
        Vec3 previous = mob.position(); double length = 0D, rise = 0D, water = 0D, malus = 0D;
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            Node node = path.getNode(i); Vec3 point = MeleePursuitPaths.standingPoint(mob, path, i);
            double segment = previous.distanceTo(point); length += segment;
            rise += Math.max(0D, point.y - previous.y); malus += Math.max(0D, node.costMalus);
            if (node.type == PathType.WATER || node.type == PathType.WATER_BORDER) water += segment;
            previous = point;
        }
        return ZombieRouteRules.walkingTicks(length, speed(), rise, water, malus);
    }

    private record Opening(BlockPos first, BlockPos approach, boolean portal, double ticks) {}

    private boolean poundingAvailable(BlockPos pos) {
        BlockState state=mob.level().getBlockState(pos);
        return mob.level().getDifficulty()==Difficulty.HARD&&SourceBreakPortalGoal.first(mob)!=null
                && !rejected(pos)
                && SourceBreakPortalGoal.woodenPortal(state)&&state.hasProperty(BlockStateProperties.OPEN)
                && !state.getValue(BlockStateProperties.OPEN)&&state.canEntityDestroy(mob.level(),pos,mob);
    }

    private double poundingTicks(BlockPos pos) {
        var goal=SourceBreakPortalGoal.first(mob);if(goal==null)return Double.POSITIVE_INFINITY;
        BlockState state=mob.level().getBlockState(pos);
        int remaining=goal.threshold(state)-(pos.equals(goal.targetBlock())?goal.progress():0);
        int increment=(int)(Math.max(R196DiggerGoal.sourceStrength(mob,state),1F)+.5F);
        return Math.max(0D,Math.ceil((double)remaining/increment));
    }

    /** Checks the complete swept body and supporting floor before estimating any excavation shortcut. */
    @Nullable private Opening directOpening(LivingEntity target) {
        if (Math.abs(target.getY() - mob.getY()) > .25D || mob.distanceToSqr(target) > 32D * 32D) return null;
        Vec3 direction = target.position().subtract(mob.position());
        double length = direction.horizontalDistance(); if (length < 1.5D) return null;
        direction = direction.scale(1D / length);
        LinkedHashSet<BlockPos> blocks = new LinkedHashSet<>();
        BlockPos approach = mob.blockPosition(); int foot = mob.getBlockY();
        double width = mob.getBbWidth() * .5D;
        for (double distance = 0D; distance <= Math.max(0D, length - 1D); distance += .25D) {
            Vec3 point = mob.position().add(direction.scale(distance));
            AABB body = new AABB(point.x - width, mob.getY() + .05D, point.z - width,
                    point.x + width, mob.getY() + mob.getBbHeight() - .05D, point.z + width);
            BlockPos floor = BlockPos.containing(point.x, foot - 1, point.z);
            if (!mob.level().hasChunkAt(floor) || !mob.level().getBlockState(floor).isFaceSturdy(mob.level(), floor, Direction.UP)) return null;
            boolean clear = true;
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                    BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
                if (!mob.level().hasChunkAt(pos)) return null;
                BlockState state = mob.level().getBlockState(pos);
                if (!state.getFluidState().isEmpty()) return null;
                boolean collision = state.getCollisionShape(mob.level(), pos).toAabbs().stream()
                        .anyMatch(shape -> shape.move(pos).intersects(body));
                if (!collision) continue;
                if (pos.getY() < foot || pos.getY() > foot + 1 || !digger.canDestroyMaterial(pos)&&!poundingAvailable(pos)) return null;
                clear = false; blocks.add(pos.immutable()); if (blocks.size() > MAX_BLOCKS) return null;
            }
            if (clear && blocks.isEmpty()) approach = BlockPos.containing(point);
        }
        if (blocks.isEmpty()) return null;
        double ticks = ZombieRouteRules.walkingTicks(Math.max(0D, length - 1D), speed(), 0D, 0D, 0D);
        boolean firstPortal=false;Set<BlockPos> counted=new java.util.HashSet<>();
        BlockPos first=blocks.iterator().next();
        for (BlockPos block : blocks) {
            if(!counted.add(block))continue;
            BlockState state=mob.level().getBlockState(block);
            double excavation=digger.canDestroyMaterial(block)?digger.remainingBreakTicks(block):Double.POSITIVE_INFINITY;
            if(state.getBlock() instanceof DoorBlock) {
                BlockPos partner=state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER?block.below():block.above();
                if(blocks.contains(partner)&&mob.level().getBlockState(partner).is(state.getBlock())) {
                    // Both staged excavation and portal pounding remove a door's matching half as well.
                    counted.add(partner);
                }
            }
            double pounding=poundingAvailable(block)?poundingTicks(block):Double.POSITIVE_INFINITY;
            if(block.equals(first))firstPortal=pounding<excavation;
            ticks+=Math.min(excavation,pounding);
        }
        return new Opening(first, approach, firstPortal, ticks);
    }
}
