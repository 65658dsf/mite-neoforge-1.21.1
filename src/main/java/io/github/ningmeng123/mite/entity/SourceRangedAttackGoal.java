package io.github.ningmeng123.mite.entity;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
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
    public SourceRangedAttackGoal(RangedAttackMob shooter,double speed,int interval,float nominalRange) {
        if(!(shooter instanceof Mob owner)) throw new IllegalArgumentException("Ranged owner must be a mob");
        this.mob=owner;this.shooter=shooter;this.speed=speed;this.interval=interval;this.nominalRange=nominalRange;
        setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));
    }
    @Override public boolean canUse() { if(mob.getTarget()==null) return false;target=mob.getTarget();return true; }
    @Override public boolean canContinueToUse() { return canUse()||!mob.getNavigation().isDone(); }
    @Override public void stop() { target=null;seen=0;attackTime=-1; }
    public static boolean clearShot(Mob mob,LivingEntity target) {
        Vec3 from=mob.getEyePosition();
        return clear(mob,from,target.getEyePosition()) || clear(mob,from,target.position().add(0,target.getBbHeight()*.25F,0));
    }
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
    @Override public void tick() {
        if(target==null) return;
        double distance=mob.distanceToSqr(target.getX(),target.getBoundingBox().minY,target.getZ());
        boolean visible=clearShot(mob,target);
        seen=visible?seen+1:0;
        if(distance<=rangeSquared()&&seen>=20) mob.getNavigation().stop();
        else mob.getNavigation().moveTo(target,speed);
        mob.getLookControl().setLookAt(target,30,30);
        if(--attackTime==0) {
            if(distance>rangeSquared()||!visible) return;
            float power=Math.clamp((float)Math.sqrt(distance)/nominalRange,.1F,1F);
            BlockPos eyePos=BlockPos.containing(mob.getX(),mob.getEyeY(),mob.getZ());
            var eye=mob.level().getBlockState(eyePos);
            if(eye.getCollisionShape(mob.level(),eyePos).isEmpty()) shooter.performRangedAttack(target,power);
            attackTime=attackInterval();
        } else if(attackTime<0) attackTime=attackInterval();
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
