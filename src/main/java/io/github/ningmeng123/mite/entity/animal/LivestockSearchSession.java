package io.github.ningmeng123.mite.entity.animal;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/** Resumable source spiral. No native navigation or world access leaves the server thread. @author NingMeng123 */
final class LivestockSearchSession {
    final PathfinderMob mob;
    final boolean water;
    final float originalWaterCost;
    final int distance,maximum;
    final Vec3 origin;
    final BlockPos originBlock,scanOrigin;
    private final Level level;
    private final PathNavigation navigation;
    private final float width,height,step;
    private final int maxFall;
    private final int[] malus;
    private final boolean floats,opens,passes;
    private final LivestockSourceScan sources;
    private final BlockPos.MutableBlockPos position=new BlockPos.MutableBlockPos();
    private final int vertical,columns;
    private int column,x,z,dy,direction,run=1,steps,legs,found;
    private int phase;
    private static final int SCAN=0,CANDIDATE=1,FALLBACK=2,FALLBACK_QUERY=3,DONE=4;
    @Nullable private BlockPos candidate,fallback;
    @Nullable private Terrain resultTerrain;
    @Nullable Path result;
    boolean resourceFound,wet,invalid,active,queued;
    int requestedAt,lastSliceAt=Integer.MIN_VALUE;
    private int deferredAt=Integer.MIN_VALUE;

    LivestockSearchSession(PathfinderMob mob,boolean water,float resource,float originalWaterCost) {
        this.mob=mob;this.water=water;this.originalWaterCost=originalWaterCost;
        level=mob.level();navigation=mob.getNavigation();origin=mob.position();originBlock=mob.blockPosition().immutable();
        scanOrigin=new BlockPos(mob.getBlockX(),(int)Math.floor(mob.getY()+mob.getBbHeight()*.75F),mob.getBlockZ());
        distance=LivestockRules032.searchDistance(resource);maximum=LivestockRules032.searchCandidates(resource);
        vertical=distance/8;columns=(distance*2+1)*(distance*2+1);dy=-vertical;
        width=mob.getBbWidth();height=mob.getBbHeight();step=mob.maxUpStep();maxFall=mob.getMaxFallDistance();
        var evaluator=navigation.getNodeEvaluator();
        floats=evaluator.canFloat();opens=evaluator.canOpenDoors();passes=evaluator.canPassDoors();
        PathType[] types=PathType.values();malus=new int[types.length];
        for(int i=0;i<types.length;i++) malus[i]=Float.floatToIntBits(types[i]==PathType.WATER?originalWaterCost:mob.getPathfindingMalus(types[i]));
        sources=new LivestockSourceScan(mob,water,this::sourceAt);
    }

    boolean pending() { return !invalid && phase!=DONE; }
    void clearHints() { sources.invalidate(); }

    /** Moving to another block, a changed need tier or a changed movement policy starts a new search. */
    boolean contextValid(float resource) {
        if(invalid || mob.level()!=level || mob.getNavigation()!=navigation || !mob.blockPosition().equals(originBlock)
                || mob.position().distanceToSqr(origin)>=1D || mob.getBbWidth()!=width || mob.getBbHeight()!=height
                || mob.maxUpStep()!=step || mob.getMaxFallDistance()!=maxFall
                || LivestockRules032.searchDistance(resource)!=distance || LivestockRules032.searchCandidates(resource)!=maximum) return false;
        var evaluator=navigation.getNodeEvaluator();
        if(evaluator.canFloat()!=floats || evaluator.canOpenDoors()!=opens || evaluator.canPassDoors()!=passes) return false;
        PathType[] types=PathType.values();
        for(int i=0;i<types.length;i++) if(Float.floatToIntBits(mob.getPathfindingMalus(types[i]))!=malus[i]) return false;
        return true;
    }

    private boolean sourceAt(BlockPos pos) {
        if(level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return false;
        return water?Livestock032.waterBlock(mob,pos):Livestock032.foodBlock(mob,level.getBlockState(pos));
    }

    /** Each selected block spends exactly one original candidate, even when its native route is rejected. */
    void advance(LivestockSearchBudget.State budget,int cellLimit) {
        if(!pending()) return;
        float resource=water?Livestock032.needs(mob).water():Livestock032.needs(mob).food();
        if(!contextValid(resource)) { invalid=true;clearHints();return; }
        clearHints();
        int checked=0;
        try {
            while(pending() && budget.timeAvailable()) {
                if(phase==SCAN) {
                    if(found>=maximum || column>=columns) { phase=FALLBACK;continue; }
                    if(checked>=cellLimit) return;
                    position.set(scanOrigin.getX()+x,scanOrigin.getY()+dy,scanOrigin.getZ()+z);
                    boolean matches=sources.test(position);
                    ++checked;budget.countCell();
                    if(matches) { candidate=position.immutable();++found;phase=CANDIDATE; }
                    advanceCell();
                } else if(phase==CANDIDATE) {
                    // A source may have been removed during a previous tick or by a path hook.
                    if(candidate==null || !sourceAt(candidate)) { phase=SCAN;continue; }
                    if(!admit(budget)) return;
                    Path path=query(candidate,distance);
                    if(path!=null && path.getEndNode()!=null) {
                        var end=path.getEndNode();BlockPos endpoint=new BlockPos(end.x,end.y,end.z);
                        if(water?Livestock032.isNearWater(mob,endpoint):Livestock032.isNearFood(mob,endpoint)) {
                            finish(path,true);return;
                        }
                    }
                    phase=SCAN;
                    // No second path call can be admitted for this mob in the same tick.
                    return;
                } else if(phase==FALLBACK) {
                    float previous=mob.getPathfindingMalus(PathType.WATER);
                    Vec3 away;
                    try {
                        mob.setPathfindingMalus(PathType.WATER,wet?0F:originalWaterCost);
                        away=DefaultRandomPos.getPosAway(mob,16,7,mob.position());
                    } finally { mob.setPathfindingMalus(PathType.WATER,previous);clearHints(); }
                    if(away==null) { finish(null,false);return; }
                    fallback=BlockPos.containing(away);phase=FALLBACK_QUERY;
                } else if(phase==FALLBACK_QUERY) {
                    if(fallback==null || level.isOutsideBuildHeight(fallback) || !level.hasChunkAt(fallback)) {
                        finish(null,false);return;
                    }
                    if(!admit(budget)) return;
                    finish(query(fallback,32),false);return;
                }
            }
        } finally {
            clearHints();
            if(pending() && deferredAt!=budget.tick) { deferredAt=budget.tick;++budget.deferred; }
        }
    }

    private boolean admit(LivestockSearchBudget.State budget) {
        if(budget.queryAvailable(mob)) { budget.countQuery(mob);return true; }
        if(deferredAt!=budget.tick) { deferredAt=budget.tick;++budget.deferred; }
        return false;
    }

    @Nullable private Path query(BlockPos target,int range) {
        float previous=mob.getPathfindingMalus(PathType.WATER);
        try {
            mob.setPathfindingMalus(PathType.WATER,wet?0F:originalWaterCost);
            return SourceMovement032Goal.pathTo(mob,target,range);
        } finally { mob.setPathfindingMalus(PathType.WATER,previous);clearHints(); }
    }

    private void finish(@Nullable Path path,boolean source) {
        if(!validMovement(path) && !water && !wet && originalWaterCost>0F) {
            // Match the original complete dry search followed by a complete swimming search.
            wet=true;column=x=z=direction=steps=legs=found=0;run=1;dy=-vertical;
            candidate=fallback=null;phase=SCAN;return;
        }
        result=path;resourceFound=source;phase=DONE;
        if(path!=null) resultTerrain=new Terrain(mob,path);
    }

    private boolean validMovement(@Nullable Path path) {
        return path!=null && !path.isDone() && path.getEndNode()!=null
                && mob.position().distanceToSqr(new Vec3(path.getEndNode().x,path.getEndNode().y,path.getEndNode().z))>=1D;
    }

    /** The classic clockwise square spiral, with all vertical cells visited before the next column. */
    private void advanceCell() {
        if(++dy<=vertical) return;
        dy=-vertical;if(++column>=columns) return;
        switch(direction) { case 0 -> ++x;case 1 -> ++z;case 2 -> --x;default -> --z; }
        if(++steps==run) {
            steps=0;direction=(direction+1)&3;
            if(++legs==2) { legs=0;++run; }
        }
    }

    /** No route prepared on an earlier tick is installed through changed terrain or a removed source. */
    boolean resultValid() {
        if(invalid || phase!=DONE || resultTerrain!=null && !resultTerrain.unchanged()) return false;
        if(resourceFound && result!=null && result.getEndNode()!=null) {
            var end=result.getEndNode();BlockPos endpoint=new BlockPos(end.x,end.y,end.z);
            return water?Livestock032.isNearWater(mob,endpoint):Livestock032.isNearFood(mob,endpoint);
        }
        return true;
    }

    /** Loaded chunk identities and the swept body/support corridor are retained only until route consumption. */
    private static final class Terrain {
        private final ServerLevel level;
        private final Long2ObjectOpenHashMap<BlockState> states=new Long2ObjectOpenHashMap<>();
        private final Map<Long,LevelChunk> chunks=new HashMap<>();
        Terrain(PathfinderMob mob,Path path) {
            level=(ServerLevel)mob.level();
            double half=mob.getBbWidth()*.5D+.1D;
            Vec3 previous=null;
            BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
            for(int i=0;i<path.getNodeCount();i++) {
                Vec3 point=path.getEntityPosAtNode(mob,i);
                Vec3 from=previous==null?point:previous;
                int minX=(int)Math.floor(Math.min(from.x,point.x)-half),maxX=(int)Math.floor(Math.max(from.x,point.x)+half);
                int minZ=(int)Math.floor(Math.min(from.z,point.z)-half),maxZ=(int)Math.floor(Math.max(from.z,point.z)+half);
                int minY=(int)Math.floor(Math.min(from.y,point.y)-1D),maxY=(int)Math.floor(Math.max(from.y,point.y)+mob.getBbHeight()+.1D);
                for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
                    long key=ChunkPos.asLong(x>>4,z>>4);
                    LevelChunk chunk;
                    if(chunks.containsKey(key)) chunk=chunks.get(key);
                    else { chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);chunks.put(key,chunk); }
                    if(chunk==null) continue;
                    for(int y=minY;y<=maxY;y++) {
                        pos.set(x,y,z);
                        if(!level.isOutsideBuildHeight(pos)) states.put(pos.asLong(),chunk.getBlockState(pos));
                    }
                }
                previous=point;
            }
        }
        boolean unchanged() {
            for(var entry:chunks.entrySet()) {
                long key=entry.getKey();
                if(level.getChunkSource().getChunkNow(ChunkPos.getX(key),ChunkPos.getZ(key))!=entry.getValue()) return false;
            }
            for(var entry:states.long2ObjectEntrySet()) {
                BlockPos pos=BlockPos.of(entry.getLongKey());
                LevelChunk chunk=chunks.get(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));
                if(chunk==null || chunk.getBlockState(pos)!=entry.getValue()) return false;
            }
            return true;
        }
    }
}
