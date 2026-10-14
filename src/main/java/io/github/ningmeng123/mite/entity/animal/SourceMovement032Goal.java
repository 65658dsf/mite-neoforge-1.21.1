package io.github.ningmeng123.mite.entity.animal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;

/** EntityAIMovementTask: stuck timeout, path fallback and temporary water avoidance. @author NingMeng123 */
public abstract class SourceMovement032Goal extends Goal {
    protected final PathfinderMob mob;
    protected final float movementSpeed;
    private final boolean swimIfNecessary;
    private float oldWaterCost;
    private BlockPos previousPosition;
    private int stationaryTicks;
    private int stuckThreshold;
    protected SourceMovement032Goal(PathfinderMob mob,float speed,boolean swimIfNecessary) {
        this.mob=mob; this.movementSpeed=speed; this.swimIfNecessary=swimIfNecessary;
        setFlags(EnumSet.of(Flag.MOVE));
    }
    @Nullable protected abstract Path movementPath();
    protected float movementSpeed() { return movementSpeed; }
    protected boolean isStuck() { return stationaryTicks>stuckThreshold; }
    public int stationaryTicks() { return stationaryTicks; }
    public int stuckThreshold() { return stuckThreshold; }
    @Override public void start() {
        previousPosition=mob.blockPosition(); stationaryTicks=0; stuckThreshold=30+mob.getRandom().nextInt(20);
        oldWaterCost=mob.getPathfindingMalus(PathType.WATER);
        Path path=movementPath();
        if (!valid(path) && oldWaterCost>0F && swimIfNecessary) {
            mob.setPathfindingMalus(PathType.WATER,0F); path=movementPath();
        }
        mob.getNavigation().moveTo(path,movementSpeed());
    }
    private boolean valid(@Nullable Path path) {
        return path!=null && !path.isDone() && path.getEndNode()!=null
                && mob.position().distanceToSqr(new net.minecraft.world.phys.Vec3(path.getEndNode().x,path.getEndNode().y,path.getEndNode().z))>=1D;
    }
    @Override public void tick() {
        mob.getNavigation().setSpeedModifier(movementSpeed());
        BlockPos current=mob.blockPosition();
        if(previousPosition!=null && current.getX()==previousPosition.getX() && current.getZ()==previousPosition.getZ()) stationaryTicks++;
        else { previousPosition=current; stationaryTicks=0; }
    }
    @Override public boolean canContinueToUse() { return !isStuck() && !mob.getNavigation().isDone(); }
    @Override public void stop() { mob.getNavigation().stop(); mob.setPathfindingMalus(PathType.WATER,oldWaterCost); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    /** Source range is explicit and independent of the normal target-acquisition attribute. */
    @Nullable public static Path pathTo(PathfinderMob mob,BlockPos destination,int range) {
        if (!mob.level().hasChunkAt(destination) || destination.getY()<mob.level().getMinBuildHeight() || destination.getY()>=mob.level().getMaxBuildHeight()) return null;
        return mob.getNavigation().createPath(destination,0,range);
    }
    public static boolean occupied(PathfinderMob mob,BlockPos pos) {
        return !mob.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(pos),e->e!=mob && e.isAlive()).isEmpty();
    }
    public static boolean passable(PathfinderMob mob,BlockPos pos,boolean includeLiquid) {
        return pos.getY()>=mob.level().getMinBuildHeight() && pos.getY()<mob.level().getMaxBuildHeight()
                && mob.level().hasChunkAt(pos) && !mob.level().getBlockState(pos).blocksMotion()
                && (includeLiquid || mob.level().getFluidState(pos).isEmpty());
    }
    /** Source candidates descend then ascend by at most eight blocks; modern lower/upper boundaries remain authoritative. */
    public static BlockPos findFoot(PathfinderMob mob,BlockPos candidate) {
        BlockPos pos=candidate;
        for(int i=0;i<8 && passable(mob,pos.below(),false);i++) pos=pos.below();
        for(int i=0;i<8 && !passable(mob,pos,false) && pos.getY()<mob.level().getMaxBuildHeight()-1;i++) pos=pos.above();
        return pos;
    }
}
