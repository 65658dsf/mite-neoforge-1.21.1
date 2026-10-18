package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** qa's path/strike cadence and cooperation with EntityAIWatchAnimal. @author NingMeng123 */
public final class SourceMeleeGoal extends Goal {
    private final PathfinderMob mob;
    private final double speed;
    private final boolean followUnseen;
    @Nullable private final Class<? extends LivingEntity> targetClass;
    private Path path;
    private int attackCooldown;
    private int recalculate;
    private int suppressed;
    private LivingEntity pathTarget;
    private Vec3 progressPosition;
    private int stalledTicks;
    private long lastProgressAt;

    public SourceMeleeGoal(PathfinderMob mob, double speed, boolean followUnseen) {
        this(mob, null, speed, followUnseen);
    }

    public SourceMeleeGoal(PathfinderMob mob, @Nullable Class<? extends LivingEntity> targetClass, double speed, boolean followUnseen) {
        this.mob = mob;
        this.speed = speed;
        this.followUnseen = followUnseen;
        this.targetClass = targetClass;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable public static SourceMeleeGoal first(PathfinderMob mob) {
        return mob.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                .filter(SourceMeleeGoal.class::isInstance).map(SourceMeleeGoal.class::cast).findFirst().orElse(null);
    }

    public void suppress(int ticks) {
        suppressed = Math.max(suppressed, ticks);
        attackCooldown = Math.max(attackCooldown, ticks);
    }

    public int attackCooldown() { return attackCooldown; }
    public int suppressionTicks() { return suppressed; }

    /** Source og checks true distance to the target feet, then three physical-reach rays. */
    public static boolean canStrike(PathfinderMob mob, LivingEntity target) {
        return canStrikeFrom(mob,target,mob.position());
    }

    /** Checks a planned standing position without moving the entity or changing the world. */
    public static boolean canStrikeFrom(PathfinderMob mob, LivingEntity target, Vec3 feet) {
        if (target == null || !target.isAlive()) return false;
        if (io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat.supported(mob))
            return io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat.canStrikeFrom(mob, target, feet);
        var definition = MiteTools.definition(mob.getMainHandItem());
        double reach = 1.5D + (definition == null ? 0D : definition.reach() * .6D);
        if (feet.distanceToSqr(new Vec3(target.getX(),target.getBoundingBox().minY,target.getZ())) > reach * reach) return false;
        Vec3 origin = feet.add(0, mob.getBbHeight() * .75D, 0);
        for (double fraction : new double[]{.5D, .9D, .1D}) {
            if (clearPhysicalReach(mob, origin, target.position().add(0, target.getBbHeight() * fraction, 0))) return true;
        }
        return false;
    }

    public static boolean clearPhysicalReach(PathfinderMob mob, Vec3 origin, Vec3 limit) {
        return mob.level().clip(new ClipContext(origin, limit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob) {
            @Override public VoxelShape getBlockShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos position) {
                // R196 physical reach explicitly ignores liquids, plants, open gates and an uncovered cauldron.
                if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)
                        || state.getBlock() instanceof AbstractCauldronBlock
                        && !level.getBlockState(position.above()).isFaceSturdy(level, position.above(), net.minecraft.core.Direction.DOWN)) return Shapes.empty();
                return super.getBlockShape(state, level, position);
            }
        }).getType() == HitResult.Type.MISS;
    }

    private Path findPath(LivingEntity target) {
        if(ZombieRoutePlanner.applies(mob)) {
            var digger=R196DiggerGoal.first(mob);
            if(digger!=null) return digger.pursuitPlan().path();
        }
        if (MeleePursuitPaths.applies(mob) && MeleePursuitPaths.inRange(mob, target))
            return MeleePursuitPaths.find(mob, target, path);
        return mob.getNavigation().createPath(target.blockPosition(), 0, (int)Math.ceil(SourceMonsterTargeting.effectiveRange(mob)));
    }

    @Override public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (targetClass != null && !targetClass.isInstance(target)) return false;
        if (targetClass == net.minecraft.world.entity.animal.Animal.class && target instanceof R196Wolf wolf && wolf.miteKind() == MobKind.HELLHOUND) return false;
        if (!mob.level().getEntitiesOfClass(FallingBlockEntity.class, mob.getBoundingBox().inflate(1).move(0, 1, 0)).isEmpty()) {
            suppress(10);
            return false;
        }
        if (!MeleePursuitPaths.applies(mob) && suppressed < 1 && MobCombat.ticksWithOffset(mob) % 10 == 0 && mob.distanceTo(target) < 3F && !canStrike(mob, target)) {
            var limit = target.position().add(0, target.getBbHeight() * .5D, 0);
            var collision = mob.level().clip(new ClipContext(mob.position().add(0, mob.getBbHeight() * .75D, 0), limit,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            if (collision.getType() == HitResult.Type.MISS) collision = mob.level().clip(new ClipContext(
                    mob.position().add(0, mob.getBbHeight() * .25D, 0), limit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            boolean cactusBlocksEdging = collision.getType() == HitResult.Type.BLOCK
                    && mob.level().getBlockState(collision.getBlockPos()).is(Blocks.CACTUS);
            if (!cactusBlocksEdging) {
                mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 2D);
                mob.getLookControl().setLookAt(target, 30, 30);
            }
        }
        if (suppressed > 0) --suppressed;
        attackCooldown = Math.max(0, attackCooldown - 1);
        path = findPath(target);
        return canStrike(mob, target) || path != null;
    }

    @Override public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (!followUnseen && !MeleePursuitPaths.applies(mob)) return !mob.getNavigation().isDone();
        return mob.isWithinRestriction(target.blockPosition());
    }

    @Override public void start() {
        mob.getNavigation().moveTo(path, speed);
        path = mob.getNavigation().getPath();
        pathTarget = mob.getTarget(); progressPosition = mob.position(); stalledTicks = 0; recalculate = 0;
        lastProgressAt = mob.level().getGameTime();
    }
    @Override public void stop() { mob.getNavigation().stop(); pathTarget = null; progressPosition = null; }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    /** Existing target memory only lasts longer while an actual complete pursuit route is making progress. */
    public boolean followsReachableRoute(LivingEntity target) {
        return MeleePursuitPaths.applies(mob) && pathTarget == target && stalledTicks < 60
                && mob.level().getGameTime() - lastProgressAt < 60L && mob.getNavigation().getPath() == path
                && !mob.getNavigation().isDone() && !mob.getNavigation().isStuck()
                && MeleePursuitPaths.reaches(mob, mob.getNavigation().getPath(), target);
    }

    public boolean canStrikeNow() {
        return suppressed <= 0 && attackCooldown <= 0 && canStrike(mob, mob.getTarget());
    }

    /** Called by the higher-priority digging goal; qa.c is decremented there before the immediate strike check. */
    public boolean tickWhenDigging() {
        attackCooldown = Math.max(0, attackCooldown - 1);
        if (!canStrikeNow()) return false;
        tick();
        return true;
    }

    @Override public void tick() {
        if (suppressed > 0) { --suppressed; return; }
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        if (pathTarget != target || progressPosition == null || progressPosition.distanceToSqr(mob.position()) >= .25D) {
            pathTarget = target; progressPosition = mob.position(); stalledTicks = 0;
            lastProgressAt = mob.level().getGameTime();
        } else ++stalledTicks;
        if (MeleePursuitPaths.applies(mob) && stalledTicks > 0 && stalledTicks % 60 == 0) {
            mob.getNavigation().stop();
            var digger = R196DiggerGoal.first(mob);
            if (digger != null) digger.invalidatePursuit();
            recalculate = 0;
        }
        mob.getLookControl().setLookAt(target, 30, 30);
        if ((followUnseen || MeleePursuitPaths.applies(mob) || SourceMonsterTargeting.canSee(mob, target, false)) && --recalculate <= 0) {
            recalculate = 4 + mob.getRandom().nextInt(7);
            path = findPath(target);
            if (MeleePursuitPaths.applies(mob) && MeleePursuitPaths.inRange(mob, target)
                    && !MeleePursuitPaths.reaches(mob, path, target)) recalculate = 20;
            // Reissuing the same route resets vanilla's stuck timer, hiding a blocked passage forever.
            if (path != mob.getNavigation().getPath() || mob.getNavigation().isDone()) mob.getNavigation().moveTo(path, speed);
            path = mob.getNavigation().getPath();
        }
        if (MeleePursuitPaths.applies(mob) && mob.getNavigation().isDone()
                && MeleePursuitPaths.reaches(mob, path, target) && !canStrike(mob, target)) {
            // Native waypoint tolerance can finish just outside real melee reach. Finish the verified last step.
            Vec3 end = MeleePursuitPaths.standingPoint(mob, path, path.getNodeCount() - 1);
            if (mob.level().noCollision(mob, mob.getBoundingBox().move(end.subtract(mob.position())).deflate(.001D)))
                mob.getMoveControl().setWantedPosition(end.x, end.y, end.z, speed);
        }
        attackCooldown = Math.max(0, attackCooldown - 1);
        if (attackCooldown > 0) return;
        if (canStrike(mob, target)) {
            boolean weaponCombat = io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat.supported(mob);
            attackCooldown = weaponCombat ? 0 : 20;
            if (!weaponCombat && !mob.getMainHandItem().isEmpty()) mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        } else if (R196MonsterAi.diggingEnabled(mob)) {
            int attackerY = net.minecraft.util.Mth.floor(mob.getY() + .5D);
            int targetY = net.minecraft.util.Mth.floor(target.getY() + .5D);
            if (targetY > attackerY) {
                var digger = R196DiggerGoal.first(mob);
                if (digger != null) digger.setBlockToDig(new BlockPos(target.getBlockX(), targetY - 1, target.getBlockZ()), true);
                attackCooldown = 20;
            }
        }
    }
}
