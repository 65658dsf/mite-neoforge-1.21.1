package io.github.ningmeng123.mite.entity.animal;

import io.github.ningmeng123.mite.config.MiteConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/** Food and water source goals keep the actual abw spiral candidate order and source urgency thresholds. @author NingMeng123 */
public final class LivestockSeeking032 extends SourceMovement032Goal {
    private final boolean water;
    private boolean pathStarted;
    private boolean budgeted, running, finished, installed, yielding, modeChanged, waterCaptured;
    @Nullable private LivestockSearchSession session;
    private float originalWaterCost;
    private long waitingAt, resumeAt = Long.MIN_VALUE, retryAt = Long.MIN_VALUE;
    private int failures, backoffTier, stationary, stuckAfter;
    @Nullable private BlockPos backoffOrigin, progressPosition;
    public LivestockSeeking032(PathfinderMob mob, boolean water) { super(mob,1F,!water); this.water=water; }
    public boolean seeksWater() { return water; }
    private float resource() { var needs=Livestock032.needs(mob); return water?needs.water():needs.food(); }
    private boolean nearby() { return water?Livestock032.isNearWater(mob):Livestock032.isNearFood(mob); }
    @Override public boolean canUse() {
        if(!slicedEnabled()) return resource()<.5F && (nearby() || mob.getRandom().nextInt(LivestockRules032.searchRoll(resource()))==0);
        // A started courtship gets to finish; repeatedly taking MOVE back prevents breeding forever.
        if(breeding() || resource()>=.5F) return false;
        if(nearby()) return true;
        adoptWaitingWaterChange();
        long now=mob.level().getGameTime();
        if(backoffOrigin!=null && (!backoffOrigin.equals(mob.blockPosition())
                || backoffTier!=LivestockRules032.searchDistance(resource()))) clearFailure();
        if(now<resumeAt) return false;
        if(now<retryAt) { LivestockSearchBudget.countBackoff(mob); return false; }
        if(session!=null) {
            if(!session.contextValid(resource()) || session.invalid) { discardSession(); waterCaptured=false; }
            else if(session.pending() || session.resultValid()) return true;
            else discardSession();
        }
        if(failures>0) return true; // An elapsed explicit retry is not followed by a second random delay.
        return mob.getRandom().nextInt(LivestockRules032.searchRoll(resource()))==0;
    }
    @Override public void start() {
        if(budgeted) { if(!installed) adoptWaitingWaterChange(); else restoreWater(); }
        running=true; modeChanged=false; budgeted=slicedEnabled();
        pathStarted=false;
        if(budgeted) { startSliced(); return; }
        discardSession(); restoreWater(); waterCaptured=false;
        if (nearby()) { mob.getNavigation().stop(); return; }
        pathStarted=true;
        super.start();
    }
    @Override public boolean canContinueToUse() {
        if(budgeted && !installed) adoptWaitingWaterChange();
        if(modeChanged || running && budgeted!=slicedEnabled()) { changedMode(); return false; }
        if(resource()>.95F) return false;
        if(nearby()) {
            if(budgeted) { discardSession(); restoreWater(); clearFailure(); installed=false; }
            mob.getNavigation().stop(); return true;
        }
        if(budgeted) {
            if(finished || breeding()) return false;
            if(!installed) {
                // The post-tick pump may have completed exactly at the waiting deadline.
                // Let tick consume that decision before considering another pending yield.
                if(session!=null && !session.pending()) return true;
                if(mob.level().getGameTime()-waitingAt>=20L) {
                    yielding=true;
                    resumeAt=mob.level().getGameTime()+4L+Math.floorMod(mob.getId(),4);
                    LivestockSearchBudget.pause(session); restoreWater(); mob.getNavigation().stop();
                    return false;
                }
                return true;
            }
            return stationary<=stuckAfter && !mob.getNavigation().isDone();
        }
        return super.canContinueToUse();
    }
    @Override public void tick() {
        if(modeChanged || running && budgeted!=slicedEnabled()) { changedMode(); return; }
        if(!budgeted) { super.tick(); return; }
        if(!running || finished) return;
        if(!installed) adoptWaitingWaterChange();
        if(nearby()) { discardSession(); restoreWater(); clearFailure(); installed=false; mob.getNavigation().stop(); return; }
        if(!installed) {
            if(session==null || session.invalid || !session.contextValid(resource())) {
                discardSession(); restoreWater();
                session=new LivestockSearchSession(mob,water,resource(),originalWaterCost);
            }
            if(session.pending()) LivestockSearchBudget.request(session);
            else consume();
            return;
        }
        mob.getNavigation().setSpeedModifier(movementSpeed());
        BlockPos current=mob.blockPosition();
        if(progressPosition!=null && current.getX()==progressPosition.getX() && current.getZ()==progressPosition.getZ()) stationary++;
        else { progressPosition=current; stationary=0; }
    }
    @Override public void stop() {
        if(budgeted) {
            if(!installed) adoptWaitingWaterChange();
            LivestockSearchBudget.pause(session); restoreWater(); mob.getNavigation().stop();
            if(modeChanged || session!=null && !session.pending()) discardSession();
            if(session!=null && session.pending() && !yielding)
                resumeAt=mob.level().getGameTime()+4L+Math.floorMod(mob.getId(),4);
        } else if(pathStarted) super.stop();
        else mob.getNavigation().stop();
        pathStarted=false; running=false; installed=false; yielding=false;
        // Retained cursors keep their original cost; a later unrelated episode captures a fresh one.
        if(session==null) waterCaptured=false;
    }

    private boolean slicedEnabled() { return MiteConfig.animalSearchBudgetEnabled() && mob.level() instanceof ServerLevel; }
    private boolean breeding() {
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.isRunning() && goal.getGoal() instanceof BreedGoal);
    }
    private void startSliced() {
        finished=false; installed=false; yielding=false;
        adoptWaitingWaterChange();
        if(session==null) waterCaptured=false;
        if(!waterCaptured) { originalWaterCost=mob.getPathfindingMalus(PathType.WATER); waterCaptured=true; }
        restoreWater(); mob.getNavigation().stop();
        waitingAt=mob.level().getGameTime(); progressPosition=mob.blockPosition(); stationary=0;
        stuckAfter=30+mob.getRandom().nextInt(20);
        if(nearby()) { discardSession(); clearFailure(); return; }
        if(session==null || session.invalid || !session.contextValid(resource())) {
            discardSession(); session=new LivestockSearchSession(mob,water,resource(),originalWaterCost);
        }
        if(session.pending()) LivestockSearchBudget.request(session); else consume();
    }
    private void consume() {
        if(session==null) return;
        if(session.invalid || !session.contextValid(resource()) || !session.resultValid()) {
            discardSession(); return; // Live inputs changed while paused: retry without a false failure.
        }
        Path result=session.result;
        boolean found=session.resourceFound, wet=session.wet;
        discardSession();
        if(found) clearFailure(); else rememberFailure();
        if(result==null) { finished=true; restoreWater(); mob.getNavigation().stop(); return; }
        mob.setPathfindingMalus(PathType.WATER,wet?0F:originalWaterCost);
        installed=mob.getNavigation().moveTo(result,movementSpeed());
        progressPosition=mob.blockPosition(); stationary=0;
        if(!installed) { finished=true; restoreWater(); }
    }
    private void rememberFailure() {
        failures=Math.min(3,failures+1);
        retryAt=mob.level().getGameTime()+(20L<<(failures-1))+Math.floorMod(mob.getId(),10);
        backoffOrigin=mob.blockPosition(); backoffTier=LivestockRules032.searchDistance(resource());
        LivestockSearchBudget.countBackoff(mob);
    }
    private void clearFailure() { failures=0; retryAt=Long.MIN_VALUE; backoffOrigin=null; }
    private void discardSession() { LivestockSearchBudget.pause(session); session=null; }
    private void adoptWaitingWaterChange() {
        if(!waterCaptured) return;
        float current=mob.getPathfindingMalus(PathType.WATER);
        if(Float.floatToIntBits(current)!=Float.floatToIntBits(originalWaterCost)) {
            discardSession(); originalWaterCost=current;
        }
    }
    private void restoreWater() { if(waterCaptured) mob.setPathfindingMalus(PathType.WATER,originalWaterCost); }
    private void changedMode() {
        if(modeChanged) return;
        modeChanged=true;
        if(budgeted && !installed) adoptWaitingWaterChange();
        discardSession();
        if(budgeted) restoreWater();
        else if(pathStarted) { super.stop(); pathStarted=false; }
        mob.getNavigation().stop();
    }
    @Nullable @Override protected Path movementPath() {
        int distance=LivestockRules032.searchDistance(resource());
        var sources=new LivestockSourceScan(mob,water,this::sourceAt);
        Path selected=scanCandidates(candidateOrigin(),distance,distance/8,LivestockRules032.searchCandidates(resource()),sources,candidate -> {
            try {
                Path path=pathTo(mob,candidate,distance);
                if(path==null || path.getEndNode()==null) return null;
                var last=path.getEndNode(); BlockPos end=new BlockPos(last.x,last.y,last.z);
                return (water?Livestock032.isNearWater(mob,end):Livestock032.isNearFood(mob,end))?path:null;
            } finally { sources.invalidate(); }
        });
        if(selected!=null) return selected;
        Vec3 away=DefaultRandomPos.getPosAway(mob,16,7,mob.position());
        return away==null?null:pathTo(mob,BlockPos.containing(away),32);
    }
    /** Legacy candidates are the first matching blocks in a spiral, not a complete cube sorted by distance. */
    public List<BlockPos> candidates(int horizontal,int vertical,int maximum) {
        List<BlockPos> result=new ArrayList<>(maximum);
        scanCandidates(candidateOrigin(),horizontal,vertical,maximum,new LivestockSourceScan(mob,water,this::sourceAt),
                pos -> { result.add(pos); return null; });
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
