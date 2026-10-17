package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.event.EventHooks;

/** Seek reachable quality upgrades; native collision pickup remains the only item transfer. @author NingMeng123 */
public final class SeekBetterEquipmentGoal extends Goal {
    private static final double SEARCH_RADIUS = 12D;
    private static final int SCAN_INTERVAL = 20, REPLAN_INTERVAL = 10, MAX_PURSUIT_TICKS = 200;
    private final PathfinderMob mob;
    private ItemEntity target;
    private Path path;
    private int nextScan, started, nextReplan, closeTicks;
    private UUID rejected;
    private int rejectedUntil;

    public SeekBetterEquipmentGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable public ItemEntity getTargetItem() { return target; }

    private boolean canSearch() {
        if (!MobEquipmentUpgrades.handles(mob) || mob.level().isClientSide || !mob.isAlive() || mob.isNoAi()
                || mob.isPassenger() || mob.isUsingItem() || !mob.canPickUpLoot()
                || mob.getTarget() != null && mob.getTarget().isAlive()) return false;
        if (mob instanceof AbstractPiglin) {
            if (mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(t -> t.isAlive()).isPresent()) return false;
            if (mob.getBrain().checkMemory(MemoryModuleType.AVOID_TARGET, MemoryStatus.REGISTERED)
                    && mob.getBrain().getMemory(MemoryModuleType.AVOID_TARGET).filter(t -> t.isAlive()).isPresent()) return false;
            if (mob instanceof Piglin piglin && (piglin.isBaby()
                    || piglin.getBrain().getMemory(MemoryModuleType.ADMIRING_ITEM).orElse(false))) return false;
        }
        return EventHooks.canEntityGrief(mob.level(), mob);
    }

    private boolean candidate(ItemEntity item) {
        return item.isAlive() && !item.hasPickUpDelay() && !item.getItem().isEmpty()
                && mob.distanceToSqr(item) <= SEARCH_RADIUS * SEARCH_RADIUS
                && !(item.getUUID().equals(rejected) && mob.tickCount < rejectedUntil)
                && MobEquipmentUpgrades.isUpgrade(mob, item.getItem()) && mob.wantsToPickUp(item.getItem())
                && mob.hasLineOfSight(item);
    }

    @Override public boolean canUse() {
        if (mob.tickCount < nextScan || !canSearch()) return false;
        nextScan = mob.tickCount + SCAN_INTERVAL;
        target = null; path = null;
        var candidates = mob.level().getEntitiesOfClass(ItemEntity.class,
                mob.getBoundingBox().inflate(SEARCH_RADIUS, 4D, SEARCH_RADIUS), this::candidate);
        candidates.sort(Comparator.<ItemEntity>comparingInt(item -> EquipmentDurability.quality(item.getItem()).ordinal())
                .reversed().thenComparingDouble(mob::distanceToSqr));
        int attempts = 0;
        for (ItemEntity item : candidates) {
            if (++attempts > 12) break;
            Path route = mob.getNavigation().createPath(item, 0);
            if (route == null || !route.canReach()) continue;
            target = item; path = route;
            return true;
        }
        return false;
    }

    @Override public void start() {
        started = mob.tickCount;
        nextReplan = mob.tickCount + REPLAN_INTERVAL;
        closeTicks = 0;
        mob.getNavigation().moveTo(path, 1.1D);
        updateBrainTarget();
    }

    @Override public boolean canContinueToUse() {
        return target != null && canSearch() && candidate(target) && mob.tickCount - started < MAX_PURSUIT_TICKS
                && mob.distanceToSqr(target) <= 256D
                && (!mob.getNavigation().isDone() || touchingTarget());
    }

    private boolean touchingTarget() {
        return target != null && mob.getBoundingBox().inflate(1D, 0D, 1D).intersects(target.getBoundingBox());
    }

    @Override public void tick() {
        if (target == null) return;
        mob.getLookControl().setLookAt(target, 30F, 30F);
        // Mob.aiStep handles the transfer after contact. Back off if another rule refuses it.
        if (touchingTarget()) {
            if (++closeTicks >= 10) abandonTarget();
            return;
        }
        closeTicks = 0;
        if (mob.tickCount < nextReplan) return;
        nextReplan = mob.tickCount + REPLAN_INTERVAL;
        Path route = mob.getNavigation().createPath(target, 0);
        if (route == null || !route.canReach()) { abandonTarget(); return; }
        path = route;
        mob.getNavigation().moveTo(path, 1.1D);
        updateBrainTarget();
    }

    private void updateBrainTarget() {
        if (target != null && mob instanceof AbstractPiglin) {
            mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 1.1F, 0));
            mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        }
    }

    private void clearBrainTarget() {
        if (!(mob instanceof AbstractPiglin) || target == null) return;
        var brain = mob.getBrain();
        brain.getMemory(MemoryModuleType.WALK_TARGET).filter(walk -> walk.getTarget() instanceof EntityTracker tracker
                && tracker.getEntity() == target).ifPresent(walk -> brain.eraseMemory(MemoryModuleType.WALK_TARGET));
        brain.getMemory(MemoryModuleType.LOOK_TARGET).filter(look -> look instanceof EntityTracker tracker
                && tracker.getEntity() == target).ifPresent(look -> brain.eraseMemory(MemoryModuleType.LOOK_TARGET));
    }

    private void abandonTarget() {
        rejected = target.getUUID(); rejectedUntil = mob.tickCount + 100;
        clearBrainTarget();
        target = null;
        mob.getNavigation().stop();
    }

    @Override public void stop() {
        if (target != null && target.isAlive() && canSearch()
                && (mob.tickCount - started >= MAX_PURSUIT_TICKS || mob.getNavigation().isDone())) {
            rejected = target.getUUID(); rejectedUntil = mob.tickCount + 100;
        }
        clearBrainTarget();
        mob.getNavigation().stop();
        target = null; path = null;
        nextScan = Math.max(nextScan, mob.tickCount + SCAN_INTERVAL);
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }
}
