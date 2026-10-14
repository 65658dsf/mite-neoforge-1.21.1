package io.github.ningmeng123.mite.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Seeks dry reachable ground and roof cover using modern navigation, rather than fighting rain indefinitely. @author NingMeng123 */
final class ElementalShelterGoal extends Goal {
    private final PathfinderMob mob;
    private BlockPos destination;
    ElementalShelterGoal(PathfinderMob mob) { this.mob=mob; setFlags(EnumSet.of(Flag.MOVE)); }
    @Override public boolean canUse() {
        if (!mob.isInWaterOrRain()) return false;
        for (int i=0;i<20;i++) {
            BlockPos pos=mob.blockPosition().offset(mob.getRandom().nextInt(21)-10, mob.getRandom().nextInt(7)-3, mob.getRandom().nextInt(21)-10);
            if (mob.level().isOutsideBuildHeight(pos) || !mob.level().getFluidState(pos).isEmpty()
                    || mob.level().isRainingAt(pos) || !mob.level().getBlockState(pos.below()).isSolid()) continue;
            var path=mob.getNavigation().createPath(pos,0);
            if (path!=null && path.canReach()) { destination=pos; return true; }
        }
        return false;
    }
    @Override public void start() { mob.getNavigation().moveTo(destination.getX()+.5,destination.getY(),destination.getZ()+.5,1); }
    @Override public boolean canContinueToUse() { return !mob.getNavigation().isDone() && mob.isInWaterOrRain(); }
}
