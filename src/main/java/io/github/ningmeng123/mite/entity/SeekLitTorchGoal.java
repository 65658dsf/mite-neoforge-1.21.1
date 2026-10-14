package io.github.ningmeng123.mite.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;

/** Source stalker/Shadow sample nearest eight lamps in a 16 by 4 search before choosing a reachable path. @author NingMeng123 */
public final class SeekLitTorchGoal extends Goal {
    private final R196Undead mob; private Path path;
    public SeekLitTorchGoal(R196Undead mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    public static boolean lit(BlockState state) {
        return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.JACK_O_LANTERN)
                || (state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)) && state.getValue(BlockStateProperties.LIT);
    }
    @Override public boolean canUse() {
        if (mob.getRandom().nextInt(mob.miteKind() == MobKind.SHADOW ? 40 : 200) != 0 || mob.getTarget() != null) return false;
        ArrayList<BlockPos> candidates = new ArrayList<>(); BlockPos origin = mob.blockPosition();
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) for (int y = -4; y <= 4; y++) {
            BlockPos pos = origin.offset(x, y, z);
            if (mob.level().hasChunkAt(pos) && lit(mob.level().getBlockState(pos))) candidates.add(pos);
        }
        candidates.sort(Comparator.comparingDouble(p -> mob.distanceToSqr(p.getCenter())));
        for (BlockPos pos : candidates.stream().limit(8).toList()) {
            Path candidate = mob.getNavigation().createPath(pos, 1);
            if (candidate != null && candidate.getEndNode() != null && candidate.getEndNode().asBlockPos().distSqr(pos) <= 4D) { path = candidate; return true; }
        }
        return false;
    }
    @Override public boolean canContinueToUse() { return !mob.getNavigation().isDone(); }
    @Override public void start() { mob.getNavigation().moveTo(path, 1D); }
    @Override public void stop() { path = null; mob.getNavigation().stop(); }
}
