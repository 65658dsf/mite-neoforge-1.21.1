package io.github.ningmeng123.mite.campfire;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Idle monsters inspect visible firelight without changing combat or native fire path costs. @author NingMeng123 */
public final class InvestigateCampfireGoal extends Goal {
    public static final int SCAN_INTERVAL = 40;
    public static final int MAX_PATH_ATTEMPTS = 8;
    public static final int INVESTIGATION_TICKS = 200;
    public static final double STOP_DISTANCE = 2.25D;
    private final Monster mob;
    @Nullable private BlockPos fire;
    @Nullable private Path path;
    private long nextScan;
    private int elapsed;
    private int scanSequence;

    public InvestigateCampfireGoal(Monster mob) {
        this.mob = mob;
        nextScan = (long) mob.tickCount + Math.floorMod(mob.getId(), SCAN_INTERVAL);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static void install(Monster mob) {
        if (mob.goalSelector.getAvailableGoals().stream().noneMatch(wrapped -> wrapped.getGoal() instanceof InvestigateCampfireGoal))
            mob.goalSelector.addGoal(5, new InvestigateCampfireGoal(mob));
    }

    @Nullable public BlockPos targetFire() { return fire; }

    private boolean idle() {
        return mob.level() instanceof ServerLevel && mob.isAlive() && !mob.isNoAi() && mob.getTarget() == null
                && !mob.isPassenger() && !mob.isInWaterOrBubble() && !mob.isOnFire();
    }

    @Override public boolean canUse() {
        if (!idle() || mob.tickCount < nextScan) return false;
        nextScan = (long) mob.tickCount + SCAN_INTERVAL + Math.floorMod(mob.getId() * 31 + scanSequence++, SCAN_INTERVAL);
        fire = null;
        path = null;
        ServerLevel level = (ServerLevel) mob.level();
        BlockPos candidate = CampfireEnvironment.findNearestFire(level, mob.position(), CampfireRules.ATTRACTION_RADIUS,
                this::visible);
        if (candidate == null || !mob.isWithinRestriction(candidate)) return false;
        if (mob.position().distanceToSqr(candidate.getCenter()) <= STOP_DISTANCE * STOP_DISTANCE) {
            fire = candidate;
            return true;
        }
        var standing = new ArrayList<BlockPos>();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int horizontalSquared = dx * dx + dz * dz;
                    if (horizontalSquared < 4 || horizontalSquared > 8) continue;
                    BlockPos position = candidate.offset(dx, dy, dz);
                    if (safeStanding(level, position)) standing.add(position);
                }
            }
        }
        standing.sort(Comparator.comparingDouble(pos -> mob.position().distanceToSqr(Vec3.atBottomCenterOf(pos))));
        for (int i = 0; i < Math.min(MAX_PATH_ATTEMPTS, standing.size()); i++) {
            Path candidatePath = mob.getNavigation().createPath(standing.get(i), 0);
            if (candidatePath != null && candidatePath.canReach()) {
                fire = candidate;
                path = candidatePath;
                return true;
            }
        }
        return false;
    }

    private boolean safeStanding(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.hasChunkAt(pos.above()) && level.hasChunkAt(pos.below())
                && mob.isWithinRestriction(pos) && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private boolean visible(BlockPos pos) {
        var hit = mob.level().clip(new ClipContext(mob.getEyePosition(), Vec3.atBottomCenterOf(pos).add(0D, .8D, 0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    @Override public void start() {
        elapsed = 0;
        if (path != null) mob.getNavigation().moveTo(path, 1D);
    }

    @Override public boolean canContinueToUse() {
        return idle() && fire != null && elapsed < INVESTIGATION_TICKS
                && mob.position().distanceToSqr(fire.getCenter()) <= CampfireRules.ATTRACTION_RADIUS * CampfireRules.ATTRACTION_RADIUS
                && mob.level().hasChunkAt(fire) && CampfireController.isBurning(mob.level(), fire) && visible(fire);
    }

    @Override public void tick() {
        if (!canContinueToUse()) {
            stop();
            return;
        }
        elapsed++;
        mob.getLookControl().setLookAt(fire.getX() + .5D, fire.getY() + .8D, fire.getZ() + .5D, 30F, 30F);
        if (mob.position().distanceToSqr(fire.getCenter()) <= STOP_DISTANCE * STOP_DISTANCE) mob.getNavigation().stop();
        else if (mob.getNavigation().isDone()) stop();
    }

    @Override public void stop() {
        mob.getNavigation().stop();
        fire = null;
        path = null;
        elapsed = 0;
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }
}
