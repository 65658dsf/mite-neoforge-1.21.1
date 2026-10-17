package io.github.ningmeng123.mite.entity;

import java.util.EnumSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** qn uses four times the nominal squared range and a fixed maximum interval. @author NingMeng123 */
public final class SourceRangedAttackGoal extends Goal {
    private final Mob mob;
    private final RangedAttackMob shooter;
    private final double speed;
    private final int interval;
    private final float nominalRange;
    private LivingEntity target;
    private int attackTime=-1,seen;
    private int blockedTicks, repositionCooldown;
    private Path firingPath;
    public SourceRangedAttackGoal(RangedAttackMob shooter,double speed,int interval,float nominalRange) {
        if(!(shooter instanceof Mob owner)) throw new IllegalArgumentException("Ranged owner must be a mob");
        this.mob=owner;this.shooter=shooter;this.speed=speed;this.interval=interval;this.nominalRange=nominalRange;
        setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));
    }
    @Override public boolean canUse() {
        LivingEntity current = mob.getTarget();
        if (current == null || !current.isAlive()) return false;
        if (current != target) {
            blockedTicks = repositionCooldown = seen = 0;
            firingPath = null;
        }
        target = current;
        return true;
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void start() {
        // Modern skeleton models and bow item overrides read synchronized aggression/use flags.
        // Keep this cosmetic lifecycle separate from qn's fixed source firing countdown.
        if (skeletonBow()) mob.setAggressive(true);
    }
    @Override public void stop() {
        target=null;seen=0;attackTime=-1;
        blockedTicks = repositionCooldown = 0;
        firingPath = null;
        mob.getNavigation().stop();
        if (mob instanceof AbstractSkeleton) {
            mob.setAggressive(false);
            mob.stopUsingItem();
        }
    }
    private boolean skeletonBow() {
        return mob instanceof AbstractSkeleton && mob.getMainHandItem().getItem() instanceof BowItem;
    }
    private void updateBowAnimation(boolean canAim) {
        if (!(mob instanceof AbstractSkeleton)) return;
        if (skeletonBow() && canAim) {
            mob.setAggressive(true);
            if (!mob.isUsingItem()) mob.startUsingItem(InteractionHand.MAIN_HAND);
        } else mob.stopUsingItem();
    }
    public static boolean clearShot(Mob mob,LivingEntity target) {
        if (mob instanceof AbstractSkeleton) return SkeletonAiming.aimPoint(mob, target) != null;
        Vec3 from=mob.getEyePosition();
        return clear(mob,from,target.getEyePosition()) || clear(mob,from,target.position().add(0,target.getBbHeight()*.25F,0));
    }
    public static boolean clearShot(Mob mob, Vec3 point) { return clear(mob, mob.getEyePosition(), point); }
    public static boolean clearShot(Mob mob, Vec3 from, Vec3 to) { return clear(mob, from, to); }
    private static boolean clear(Mob mob,Vec3 from,Vec3 to) {
        var context=new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob) {
            @Override public VoxelShape getBlockShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos) {
                if(state.is(Blocks.IRON_BARS) || io.github.ningmeng123.mite.registry.MiteDecorativeContent.BARS.values().stream().anyMatch(b -> state.is(b.get()))
                        || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.VINE)
                        || state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)
                        // qn's piercing ray has no projectile originator: Raycast.isFullyImpeded(11) is false.
                        || state.is(BlockTags.LEAVES)) return Shapes.empty();
                return super.getBlockShape(state,level,pos);
            }
        };
        return mob.level().clip(context).getType()==HitResult.Type.MISS;
    }
    public int attackInterval() { return R196MonsterAi.frenzied(mob)?(int)(interval*.67F):interval; }
    public double rangeSquared() { return nominalRange*nominalRange*4F; }
    public int remainingAttackTicks() { return attackTime; }
    private void moveForShot(double distance, boolean visible) {
        if (repositionCooldown > 0) --repositionCooldown;
        if (visible) {
            blockedTicks = 0;
            // Hold a newly gained firing lane instead of walking straight back behind the obstacle.
            if (firingPath != null && distance <= rangeSquared()) seen = 20;
            firingPath = null;
        } else if (skeletonBow()) {
            ++blockedTicks;
            // Finish a reachable detour before resampling; failed samples must not cancel an active route.
            if (firingPath != null && !mob.getNavigation().isDone()) return;
            if (blockedTicks >= 10 && repositionCooldown == 0) {
                firingPath = SkeletonFiringPositions.find((AbstractSkeleton)mob, target, rangeSquared());
                repositionCooldown = 20;
                if (firingPath != null) mob.getNavigation().moveTo(firingPath, speed);
            }
            if (firingPath != null && !mob.getNavigation().isDone()) return;
        }
        if (distance <= rangeSquared() && seen >= 20) mob.getNavigation().stop();
        else mob.getNavigation().moveTo(target, speed);
    }
    @Override public void tick() {
        if(target==null || !target.isAlive()) return;
        double distance=mob.distanceToSqr(target.getX(),target.getBoundingBox().minY,target.getZ());
        boolean visible=clearShot(mob,target);
        updateBowAnimation(visible && distance<=rangeSquared());
        seen=visible?seen+1:0;
        moveForShot(distance, visible);
        mob.getLookControl().setLookAt(target,30,30);
        if(--attackTime==0) {
            if(distance>rangeSquared()||!visible) return;
            float power=Math.clamp((float)Math.sqrt(distance)/nominalRange,.1F,1F);
            BlockPos eyePos=BlockPos.containing(mob.getX(),mob.getEyeY(),mob.getZ());
            var eye=mob.level().getBlockState(eyePos);
            if(eye.getCollisionShape(mob.level(),eyePos).isEmpty() || skeletonBow() && eye.is(BlockTags.LEAVES)) {
                if (skeletonBow()) mob.stopUsingItem();
                shooter.performRangedAttack(target,power);
            }
            attackTime=attackInterval();
        } else if(attackTime<0) attackTime=attackInterval();
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
