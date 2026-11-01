package io.github.ningmeng123.mite.entity.animal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/** One escape candidate at a time, with the original dry/allow-water search order. @author NingMeng123 */
final class AnimalFleeSession {
    private static final int RANGE=48;
    private static final PathType[] TYPES=PathType.values();
    // Partial routes retain the high-score/full-comparison policy used in closed pens.
    // A complete safe native route can start without comparing eleven alternatives.
    private static final double IMMEDIATE_SCORE=9.5D;
    final PathfinderMob mob;
    @Nullable private final LivingEntity threat;
    private final Level level;
    private final PathNavigation navigation;
    private final Vec3 origin,danger;
    private final BlockPos originBlock;
    private final float originalWater,width,height,step;
    private final boolean floats,opens,passes;
    private final int[] malus;
    private final long stimulus;
    private final Set<BlockPos> visited=new HashSet<>();
    private int index;
    private boolean randomTried,done;
    @Nullable private Path selected;
    @Nullable private Terrain selectedTerrain;
    private double best=Double.NEGATIVE_INFINITY;
    @Nullable private Terrain terrain;
    @Nullable Path result;
    boolean wet,invalid,active,queued,urgent;
    int requestedAt,lastAdvancedAt=Integer.MIN_VALUE,deferredAt=Integer.MIN_VALUE;

    AnimalFleeSession(PathfinderMob mob,@Nullable LivingEntity threat,float originalWater,boolean urgent) {
        this.mob=mob;this.threat=threat;this.originalWater=originalWater;this.urgent=urgent;
        level=mob.level();navigation=mob.getNavigation();origin=mob.position();originBlock=mob.blockPosition().immutable();
        danger=threat==null?origin:threat.position();stimulus=AnimalFleeBudget.generation(mob);
        width=mob.getBbWidth();height=mob.getBbHeight();step=mob.maxUpStep();
        var evaluator=navigation.getNodeEvaluator();floats=evaluator.canFloat();opens=evaluator.canOpenDoors();passes=evaluator.canPassDoors();
        malus=new int[TYPES.length];
        for(int i=0;i<TYPES.length;i++) malus[i]=Float.floatToIntBits(TYPES[i]==PathType.WATER?originalWater:mob.getPathfindingMalus(TYPES[i]));
    }

    boolean pending() {return !invalid && !done;}

    boolean contextValid() {
        if(mob.level()!=level || mob.getNavigation()!=navigation || !mob.isAlive() || mob.isRemoved() || mob.isNoAi()
                || mob.isPassenger() || mob.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player
                || !mob.blockPosition().equals(originBlock) || mob.position().distanceToSqr(origin)>=1D
                || mob.getBbWidth()!=width || mob.getBbHeight()!=height || mob.maxUpStep()!=step
                || AnimalFleeBudget.generation(mob)!=stimulus || R196AnimalAi032.lastAttacker(mob)!=threat
                || threat!=null && (!threat.isAlive() || threat.level()!=level || threat.position().distanceToSqr(danger)>=4D)) return false;
        var evaluator=navigation.getNodeEvaluator();
        if(evaluator.canFloat()!=floats || evaluator.canOpenDoors()!=opens || evaluator.canPassDoors()!=passes) return false;
        for(int i=0;i<TYPES.length;i++) if(TYPES[i]!=PathType.WATER
                && Float.floatToIntBits(mob.getPathfindingMalus(TYPES[i]))!=malus[i]) return false;
        return true;
    }

    /** Budget admission happens before this call; no path or temporary malus is changed while queued. */
    void advance(AnimalFleeBudget.State budget) {
        if(!pending()) return;
        if(!contextValid()) {invalid=true;return;}
        // Native ground navigation temporarily rejects a query during knockback.
        // That is neither an evaluated escape candidate nor a failed search: keep
        // the cursor and the actual victim's first-query priority until it can run.
        if(!((AnimalFleeNavigationAccess)navigation).mite$fleeCanUpdatePath()) return;
        BlockPos target=nextCandidate();
        if(target==null) {
            finishPhase();
            return;
        }
        budget.query(this);
        Path path=query(target);
        double score=score(path);
        if(validMovement(path) && (threat==null || score>=IMMEDIATE_SCORE
                || path.canReach() && Double.isFinite(score))) {
            finish(path,new Terrain(mob,path));
            return;
        }
        if(score>best) {best=score;selected=path;selectedTerrain=path==null?null:new Terrain(mob,path);}
        // Completing a phase is bookkeeping, not another native query. Do it while
        // this slot is held instead of putting a finished result behind the whole herd.
        if(!done && (threat==null?randomTried:index>=AnimalEscapePath.candidateCount())) finishPhase();
    }

    private void finishPhase() {
        if(validMovement(selected)) {
            if(selectedTerrain!=null && selectedTerrain.unchanged()) finish(selected,selectedTerrain);else invalid=true;
        } else if(!wet && originalWater>0F) {
            wet=true;index=0;randomTried=false;selected=null;selectedTerrain=null;best=Double.NEGATIVE_INFINITY;visited.clear();
        } else finish(null,null);
    }

    @Nullable private BlockPos nextCandidate() {
        if(threat==null) {
            if(randomTried) return null;randomTried=true;
            Vec3 point=DefaultRandomPos.getPos(mob,5,4);
            if(point==null) return null;
            BlockPos target=BlockPos.containing(point);
            return level.hasChunkAt(target)?target:null;
        }
        while(index<AnimalEscapePath.candidateCount()) {
            BlockPos target=AnimalEscapePath.candidate(mob,threat,origin,danger,index++);
            if(target!=null && visited.add(target)) return target;
        }
        return null;
    }

    @Nullable private Path query(BlockPos target) {
        float previous=mob.getPathfindingMalus(PathType.WATER);
        AnimalFleeNavigationAccess state=(AnimalFleeNavigationAccess)navigation;
        Path retained=navigation.getPath();BlockPos oldTarget=state.mite$fleeTarget();
        int oldAccuracy=state.mite$fleeAccuracy();double oldSpeed=state.mite$fleeSpeed();
        try {
            mob.setPathfindingMalus(PathType.WATER,wet?0F:originalWater);
            // Temporarily bypass the native same-target active-path cache. The active
            // route and its recomputation target are restored before another tick runs.
            navigation.stop();
            return SourceMovement032Goal.pathTo(mob,target,RANGE);
        } finally {
            mob.setPathfindingMalus(PathType.WATER,previous);
            try {if(retained==null) navigation.stop();else navigation.moveTo(retained,oldSpeed);}
            finally {state.mite$fleeTarget(oldTarget);state.mite$fleeAccuracy(oldAccuracy);state.mite$fleeSpeed(oldSpeed);}
        }
    }

    private double score(@Nullable Path path) {
        float previous=mob.getPathfindingMalus(PathType.WATER);
        try {
            mob.setPathfindingMalus(PathType.WATER,wet?0F:originalWater);
            return threat==null?(validMovement(path)?0D:Double.NEGATIVE_INFINITY)
                    :AnimalEscapePath.score(mob,path,origin,danger,firstUnreachedNode(path));
        } finally {mob.setPathfindingMalus(PathType.WATER,previous);}
    }
    private int firstUnreachedNode(@Nullable Path path) {
        if(path==null) return 0;
        int index=path.getNextNodeIndex();
        if(index>=path.getNodeCount()-1) return index;
        if(!(navigation instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation) || mob.isInWater()) return index;
        Vec3 point=path.getEntityPosAtNode(mob,index);
        if(!path.canReach()) {
            // Keep the accepted closed-pen response: incomplete escape candidates
            // retain their previous conservative start-node scoring and comparison.
            double threshold=mob.getBbWidth()>.75F?mob.getBbWidth()*.5D:.75D-mob.getBbWidth()*.5D;
            return Math.abs(mob.getX()-point.x)<=threshold && Math.abs(mob.getZ()-point.z)<=threshold
                    && Math.abs(Math.floor(mob.getY()+.5D)-point.y)<1D?index+1:index;
        }
        float threshold=mob.getBbWidth()>.75F?mob.getBbWidth()/2F:.75F-mob.getBbWidth()/2F;
        // Match both native followThePath branches without advancing the real cursor.
        // A wide animal near a block edge can be outside the axis tolerance while
        // native navigation still skips the centre behind it in favour of the next node.
        if(Math.abs(mob.getX()-point.x)<=threshold && Math.abs(mob.getZ()-point.z)<=threshold
                && Math.abs(mob.getY()-point.y)<1D) return index+1;
        if(!navigation.canCutCorner(path.getNode(index).type)) return index;
        var access=(AnimalFleeNavigationAccess)navigation;
        Vec3 current=access.mite$fleeTempMobPos();
        Vec3 first=Vec3.atBottomCenterOf(path.getNode(index).asBlockPos());
        if(!current.closerThan(first,2D)) return index;
        if(access.mite$fleeCanMoveDirectly(current,point)) return index+1;
        Vec3 next=Vec3.atBottomCenterOf(path.getNode(index+1).asBlockPos());
        Vec3 toFirst=first.subtract(current),toNext=next.subtract(current);
        double firstDistance=toFirst.lengthSqr(),nextDistance=toNext.lengthSqr();
        return (nextDistance<firstDistance || firstDistance<.5D)
                && toNext.normalize().dot(toFirst.normalize())<0D?index+1:index;
    }
    private boolean validMovement(@Nullable Path path) {
        return path!=null && !path.isDone() && path.getEndNode()!=null
                && mob.position().distanceToSqr(new Vec3(path.getEndNode().x,path.getEndNode().y,path.getEndNode().z))>=1D;
    }
    private void finish(@Nullable Path path,@Nullable Terrain evidence) {
        result=path;done=true;terrain=evidence;
    }

    boolean resultValid() {
        return done && !invalid && contextValid() && (terrain==null || terrain.unchanged());
    }

    /** Result corridors live for at most the interval until the goal consumes them. */
    private static final class Terrain {
        private final ServerLevel level;
        private final Map<Long,BlockState> states=new HashMap<>();
        private final Map<Long,LevelChunk> chunks=new HashMap<>();
        Terrain(PathfinderMob mob,Path path) {
            level=(ServerLevel)mob.level();double half=mob.getBbWidth()*.5D+.1D;
            Vec3 previous=null;
            BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
            for(int i=0;i<path.getNodeCount();i++) {
                Vec3 point=path.getEntityPosAtNode(mob,i),from=previous==null?point:previous;
                int minX=(int)Math.floor(Math.min(from.x,point.x)-half),maxX=(int)Math.floor(Math.max(from.x,point.x)+half);
                int minZ=(int)Math.floor(Math.min(from.z,point.z)-half),maxZ=(int)Math.floor(Math.max(from.z,point.z)+half);
                int minY=(int)Math.floor(Math.min(from.y,point.y)-1D),maxY=(int)Math.floor(Math.max(from.y,point.y)+mob.getBbHeight()+.1D);
                for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
                    long key=net.minecraft.world.level.ChunkPos.asLong(x>>4,z>>4);
                    LevelChunk chunk;
                    if(chunks.containsKey(key)) chunk=chunks.get(key);
                    else {chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);chunks.put(key,chunk);}
                    if(chunk==null) continue;
                    for(int y=minY;y<=maxY;y++) {pos.set(x,y,z);if(!level.isOutsideBuildHeight(pos)) states.put(pos.asLong(),chunk.getBlockState(pos));}
                }
                previous=point;
            }
        }
        boolean unchanged() {
            for(var entry:chunks.entrySet()) {
                long key=entry.getKey();
                if(level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(key),net.minecraft.world.level.ChunkPos.getZ(key))!=entry.getValue()) return false;
            }
            for(var entry:states.entrySet()) {
                BlockPos pos=BlockPos.of(entry.getKey());
                if(!level.hasChunkAt(pos) || level.getBlockState(pos)!=entry.getValue()) return false;
            }
            return true;
        }
    }
}
