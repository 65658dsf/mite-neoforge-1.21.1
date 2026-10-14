package io.github.ningmeng123.mite.entity.animal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

/** EntityAISeekOpenSpaceIfCrowded default priority three. @author NingMeng123 */
public final class LivestockFreedomGoal032 extends Goal {
    private final PathfinderMob mob;
    public LivestockFreedomGoal032(PathfinderMob mob) { this.mob=mob; setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
    @Override public boolean canUse() {
        float freedom=Livestock032.needs(mob).freedom();
        return freedom<=.5F && (!Livestock032.isCrowded(mob) || mob.getRandom().nextInt(freedom<.25F?10:40)==0);
    }
    @Nullable public Path shortestPath() {
        boolean very=Livestock032.needs(mob).freedom()<.25F;
        Path best=null; int found=0;
        for(int attempt=0;attempt<(very?16:4);attempt++) {
            int radius=attempt%2==0?8:16;
            BlockPos pos=SourceMovement032Goal.findFoot(mob,mob.blockPosition().offset(mob.getRandom().nextInt(radius*2+1)-radius,mob.getRandom().nextInt(7)-3,mob.getRandom().nextInt(radius*2+1)-radius));
            if(mob.level().getFluidState(pos.below()).is(FluidTags.WATER) || Livestock032.isCrowded(mob,pos)) continue;
            Path path=SourceMovement032Goal.pathTo(mob,pos,16);
            if(path==null || path.getEndNode()==null) continue;
            var end=path.getEndNode(); if(Livestock032.isCrowded(mob,new BlockPos(end.x,end.y,end.z))) continue;
            if(best!=null && path.getNodeCount()>=best.getNodeCount()) continue;
            best=path; if(++found>(very?2:1)) break;
        }
        return best;
    }
    @Override public void start() { if(Livestock032.isCrowded(mob)) mob.getNavigation().moveTo(shortestPath(),1D); }
    @Override public boolean canContinueToUse() {
        if(Livestock032.needs(mob).freedom()>.95F) return false;
        if(!Livestock032.isCrowded(mob)) { stop(); return true; }
        return !mob.getNavigation().isDone();
    }
    @Override public void stop() { mob.getNavigation().stop(); }
}
