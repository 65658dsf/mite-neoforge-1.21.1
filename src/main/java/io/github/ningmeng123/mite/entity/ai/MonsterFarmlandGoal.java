package io.github.ningmeng123.mite.entity.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded idle navigation and deliberate one-cell trampling, added at the user's request. @author NingMeng123 */
public final class MonsterFarmlandGoal extends Goal {
    public static final int HORIZONTAL_RADIUS = 8;
    public static final int VERTICAL_RADIUS = 2;
    public static final int SCAN_INTERVAL = 200;
    public static final int MAX_PATH_ATTEMPTS = 8;
    public static final int TRAVEL_TIMEOUT = 200;
    public static final int BREAK_TICKS = 40;
    private final PathfinderMob mob;
    @Nullable private BlockPos target;
    @Nullable private Path path;
    private long nextScanTick;
    private int scanSequence, travelTicks, workTicks, lastStage = -1;

    public MonsterFarmlandGoal(PathfinderMob mob) {
        this.mob = mob;
        // Stagger newly joined monsters without consuming their source equipment/AI RNG streams.
        nextScanTick = (long) mob.tickCount + Math.floorMod(mob.getId(), SCAN_INTERVAL);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static void install(PathfinderMob mob) {
        if (MonsterFarmland.handles(mob) && mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof MonsterFarmlandGoal))
            mob.goalSelector.addGoal(5, new MonsterFarmlandGoal(mob));
    }

    @Nullable public BlockPos targetBlock() { return target; }
    public int workTicks() { return workTicks; }

    @Override public boolean canUse() {
        if (!MonsterFarmland.idleAndAllowed(mob) || mob.tickCount < nextScanTick) return false;
        nextScanTick = (long) mob.tickCount + SCAN_INTERVAL + Math.floorMod(mob.getId() * 31 + scanSequence++, 100);
        target = null; path = null;
        ServerLevel level = (ServerLevel) mob.level(); BlockPos origin = mob.blockPosition();
        var candidates = new ArrayList<BlockPos>();
        for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++)
            for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++)
                for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                    if (dx * dx + dz * dz > HORIZONTAL_RADIUS * HORIZONTAL_RADIUS) continue;
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (MonsterFarmland.farm(level, pos)) candidates.add(pos);
                }
        candidates.sort(Comparator.comparingDouble(pos -> mob.position().distanceToSqr(standing(pos))));
        for (int i = 0; i < Math.min(MAX_PATH_ATTEMPTS, candidates.size()); i++) {
            BlockPos candidate = candidates.get(i);
            Path candidatePath = mob.getNavigation().createPath(candidate.above(), 0, HORIZONTAL_RADIUS * 2);
            if (candidatePath != null && candidatePath.canReach()) {
                target = candidate; path = candidatePath; return true;
            }
        }
        return false;
    }

    private static Vec3 standing(BlockPos soil) { return Vec3.atBottomCenterOf(soil.above()); }

    private boolean clearWorkReach() {
        var hit = mob.level().clip(new ClipContext(mob.getEyePosition(), target.getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    @Override public void start() {
        workTicks = 0; travelTicks = 0; lastStage = -1;
        if (path != null) mob.getNavigation().moveTo(path, 1D);
    }

    @Override public boolean canContinueToUse() {
        return target != null && MonsterFarmland.idleAndAllowed(mob) && travelTicks < TRAVEL_TIMEOUT
                && MonsterFarmland.farm((ServerLevel) mob.level(), target);
    }

    @Override public void tick() {
        if (!canContinueToUse()) { stop(); return; }
        mob.getLookControl().setLookAt(target.getX() + .5D, target.getY() + 1D, target.getZ() + .5D, 30F, 30F);
        if (mob.position().distanceToSqr(standing(target)) > 2.25D || !clearWorkReach()) {
            if (workTicks > 0) clearProgress();
            workTicks = 0; travelTicks++;
            if (travelTicks % 20 == 0 && mob.getNavigation().isDone())
                mob.getNavigation().moveTo(target.getX() + .5D, target.getY() + 1D, target.getZ() + .5D, 1D);
            return;
        }
        mob.getNavigation().stop();
        if (workTicks % 10 == 0) mob.swing(InteractionHand.MAIN_HAND);
        int stage = Math.min(9, workTicks * 10 / BREAK_TICKS);
        if (stage != lastStage) {
            ((ServerLevel) mob.level()).destroyBlockProgress(mob.getId(), target, stage); lastStage = stage;
        }
        if (++workTicks >= BREAK_TICKS) { MonsterFarmland.flatten(mob, target); stop(); }
    }

    private void clearProgress() {
        if (target != null && mob.level() instanceof ServerLevel level) level.destroyBlockProgress(mob.getId(), target, -1);
        lastStage = -1;
    }

    @Override public void stop() {
        clearProgress(); mob.getNavigation().stop(); target = null; path = null; workTicks = 0; travelTicks = 0;
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }
}
