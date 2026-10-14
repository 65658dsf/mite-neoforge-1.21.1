package io.github.ningmeng123.mite.entity.animal;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Food and water source goals keep the actual abw spiral candidate order and source urgency thresholds. @author NingMeng123 */
public final class LivestockSeeking032 extends SourceMovement032Goal {
    private final boolean water;
    private boolean pathStarted;
    public LivestockSeeking032(PathfinderMob mob, boolean water) { super(mob,1F,!water); this.water=water; }
    public boolean seeksWater() { return water; }
    private float resource() { var needs=Livestock032.needs(mob); return water?needs.water():needs.food(); }
    private boolean nearby() { return water?Livestock032.isNearWater(mob):Livestock032.isNearFood(mob); }
    @Override public boolean canUse() {
        return resource()<.5F && (nearby() || mob.getRandom().nextInt(LivestockRules032.searchRoll(resource()))==0);
    }
    @Override public void start() {
        pathStarted=false;
        if (nearby()) { mob.getNavigation().stop(); return; }
        pathStarted=true;
        super.start();
    }
    @Override public boolean canContinueToUse() {
        if(resource()>.95F) return false;
        if(nearby()) { mob.getNavigation().stop(); return true; }
        return super.canContinueToUse();
    }
    @Override public void stop() { if(pathStarted) super.stop(); else mob.getNavigation().stop(); pathStarted=false; }
    @Nullable @Override protected Path movementPath() {
        int distance=LivestockRules032.searchDistance(resource());
        for(BlockPos candidate:candidates(distance,distance/8,LivestockRules032.searchCandidates(resource()))) {
            Path path=pathTo(mob,candidate,distance);
            if(path==null || path.getEndNode()==null) continue;
            var last=path.getEndNode(); BlockPos end=new BlockPos(last.x,last.y,last.z);
            if(water?Livestock032.isNearWater(mob,end):Livestock032.isNearFood(mob,end)) return path;
        }
        Vec3 away=DefaultRandomPos.getPosAway(mob,16,7,mob.position());
        return away==null?null:pathTo(mob,BlockPos.containing(away),32);
    }
    /** Legacy candidates are the first matching blocks in a spiral, not a complete cube sorted by distance. */
    public List<BlockPos> candidates(int horizontal,int vertical,int maximum) {
        List<BlockPos> result=new ArrayList<>(maximum);
        int x=mob.getBlockX(), z=mob.getBlockZ(), centerY=(int)Math.floor(mob.getY()+mob.getBbHeight()*.75F);
        collect(result,x,z,centerY,vertical,maximum);
        int run=1,width=horizontal*2+1;
        while(result.size()<maximum) {
            if(run>=width) { for(int i=1;i<run && result.size()<maximum;i++) collect(result,++x,z,centerY,vertical,maximum); break; }
            for(int i=1;i<=run && result.size()<maximum;i++) collect(result,++x,z,centerY,vertical,maximum);
            for(int i=1;i<=run && result.size()<maximum;i++) collect(result,x,++z,centerY,vertical,maximum);
            run++;
            for(int i=1;i<=run && result.size()<maximum;i++) collect(result,--x,z,centerY,vertical,maximum);
            for(int i=1;i<=run && result.size()<maximum;i++) collect(result,x,--z,centerY,vertical,maximum);
            run++;
        }
        return result;
    }
    private void collect(List<BlockPos> result,int x,int z,int y,int vertical,int maximum) {
        for(int dy=-vertical;dy<=vertical && result.size()<maximum;dy++) {
            BlockPos pos=new BlockPos(x,y+dy,z);
            if(pos.getY()<mob.level().getMinBuildHeight() || pos.getY()>=mob.level().getMaxBuildHeight() || !mob.level().hasChunkAt(pos)) continue;
            if(water?Livestock032.waterBlock(mob,pos):Livestock032.foodBlock(mob,mob.level().getBlockState(pos))) result.add(pos);
        }
    }
}
