package io.github.ningmeng123.mite.entity.animal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
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
        Path selected=scanCandidates(candidateOrigin(),distance,distance/8,LivestockRules032.searchCandidates(resource()),this::sourceAt,candidate -> {
            Path path=pathTo(mob,candidate,distance);
            if(path==null || path.getEndNode()==null) return null;
            var last=path.getEndNode(); BlockPos end=new BlockPos(last.x,last.y,last.z);
            return (water?Livestock032.isNearWater(mob,end):Livestock032.isNearFood(mob,end))?path:null;
        });
        if(selected!=null) return selected;
        Vec3 away=DefaultRandomPos.getPosAway(mob,16,7,mob.position());
        return away==null?null:pathTo(mob,BlockPos.containing(away),32);
    }
    /** Legacy candidates are the first matching blocks in a spiral, not a complete cube sorted by distance. */
    public List<BlockPos> candidates(int horizontal,int vertical,int maximum) {
        List<BlockPos> result=new ArrayList<>(maximum);
        scanCandidates(candidateOrigin(),horizontal,vertical,maximum,this::sourceAt,pos -> { result.add(pos); return null; });
        return result;
    }
    private BlockPos candidateOrigin() {
        return new BlockPos(mob.getBlockX(),(int)Math.floor(mob.getY()+mob.getBbHeight()*.75F),mob.getBlockZ());
    }
    private boolean sourceAt(BlockPos pos) {
        if(pos.getY()<mob.level().getMinBuildHeight() || pos.getY()>=mob.level().getMaxBuildHeight() || !mob.level().hasChunkAt(pos)) return false;
        return water?Livestock032.waterBlock(mob,pos):Livestock032.foodBlock(mob,mob.level().getBlockState(pos));
    }
    /** Query each source only as needed; a rejected route still spends one source candidate. */
    @Nullable static <T> T scanCandidates(BlockPos origin,int horizontal,int vertical,int maximum,
            Predicate<BlockPos> source,Function<BlockPos,T> accept) {
        if(maximum<=0) return null;
        var scan=new CandidateScan<T>(maximum,source,accept);
        int x=origin.getX(), z=origin.getZ(), centerY=origin.getY();
        if(scan.collect(x,z,centerY,vertical)) return scan.selected;
        int run=1,width=horizontal*2+1;
        while(true) {
            if(run>=width) { for(int i=1;i<run;i++) if(scan.collect(++x,z,centerY,vertical)) return scan.selected; break; }
            for(int i=1;i<=run;i++) if(scan.collect(++x,z,centerY,vertical)) return scan.selected;
            for(int i=1;i<=run;i++) if(scan.collect(x,++z,centerY,vertical)) return scan.selected;
            run++;
            for(int i=1;i<=run;i++) if(scan.collect(--x,z,centerY,vertical)) return scan.selected;
            for(int i=1;i<=run;i++) if(scan.collect(x,--z,centerY,vertical)) return scan.selected;
            run++;
        }
        return null;
    }
    private static final class CandidateScan<T> {
        private int remaining;
        private final Predicate<BlockPos> source;
        private final Function<BlockPos,T> accept;
        @Nullable private T selected;
        CandidateScan(int maximum,Predicate<BlockPos> source,Function<BlockPos,T> accept) {
            remaining=maximum; this.source=source; this.accept=accept;
        }
        boolean collect(int x,int z,int y,int vertical) {
            for(int dy=-vertical;dy<=vertical;dy++) {
                BlockPos pos=new BlockPos(x,y+dy,z);
                if(!source.test(pos)) continue;
                --remaining;
                selected=accept.apply(pos);
                if(selected!=null || remaining==0) return true;
            }
            return false;
        }
    }
}
