package io.github.ningmeng123.mite.entity.ai;

import io.github.ningmeng123.mite.entity.animal.SourceMovement032Goal;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

/** tw goal 3 pursues trees below elevated players only while burning. @author NingMeng123 */
public final class ZombieMoveToTreeGoal extends SourceMovement032Goal {
    public ZombieMoveToTreeGoal(PathfinderMob mob) { super(mob,1F,false); }
    @Override public boolean canUse() { return mob.getRandom().nextInt(40)==0 && mob.isOnFire(); }
    public static boolean playerAboveTree(Player player,BlockPos log) {
        int y=player.blockPosition().getY(),dx=player.blockPosition().getX()-log.getX(),dz=player.blockPosition().getZ()-log.getZ();
        return !player.isSpectator() && player.isAlive() && y>log.getY()+2 && y<log.getY()+9 && dx*dx+dz*dz<=32;
    }
    @Nullable @Override protected Path movementPath() { return findPathToTree(); }
    @Nullable public Path findPathToTree() {
        for(BlockPos log:treeCandidates()) for(Player player:mob.level().players()) if(playerAboveTree(player,log)) {
            Path path=pathTo(mob,log,16);
            if(path!=null&&path.getEndNode()!=null&&path.getEndNode().asBlockPos().distSqr(log)<2D) {
                SourceMonsterFire.boost(mob,100); return path;
            }
        }
        return null;
    }
    /** abw's first eight matches, not the closest eight after sorting by Euclidean distance. */
    public List<BlockPos> treeCandidates() {
        ArrayList<BlockPos> candidates=new ArrayList<>(8);
        BlockPos origin=BlockPos.containing(mob.getX(),mob.getY()+mob.getBbHeight()*.75F,mob.getZ());
        int x=origin.getX(),z=origin.getZ(),run=1;
        if(scanColumn(candidates,x,origin.getY(),z)) return List.copyOf(candidates);
        while(run<33) {
            for(int i=0;i<run;i++) if(scanColumn(candidates,++x,origin.getY(),z)) return List.copyOf(candidates);
            for(int i=0;i<run;i++) if(scanColumn(candidates,x,origin.getY(),++z)) return List.copyOf(candidates);
            ++run;
            for(int i=0;i<run;i++) if(scanColumn(candidates,--x,origin.getY(),z)) return List.copyOf(candidates);
            for(int i=0;i<run;i++) if(scanColumn(candidates,x,origin.getY(),--z)) return List.copyOf(candidates);
            ++run;
        }
        // Finish the north edge of the outermost square, as abw does when run reaches width.
        for(int i=1;i<run;i++) if(scanColumn(candidates,++x,origin.getY(),z)) return List.copyOf(candidates);
        return List.copyOf(candidates);
    }
    private boolean scanColumn(ArrayList<BlockPos> candidates,int x,int centerY,int z) {
        for(int y=centerY-4;y<=centerY+4;y++) {
            BlockPos pos=new BlockPos(x,y,z);
            if(!mob.level().isOutsideBuildHeight(pos)&&mob.level().hasChunkAt(pos)&&mob.level().getBlockState(pos).is(BlockTags.LOGS_THAT_BURN)) {
                candidates.add(pos);
                if(candidates.size()==8) return true;
            }
        }
        return false;
    }
    // The actual subclass overrides continuation rather than using its parent's stuck countdown.
    @Override public boolean canContinueToUse() { return !mob.getNavigation().isDone(); }
}
