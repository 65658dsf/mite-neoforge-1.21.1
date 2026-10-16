package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.worldgen.MantleHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** apd.handleFreefall moves an unsupported whole liquid cell before ordinary spreading. @author NingMeng123 */
public final class SourceFluidFall {
    private SourceFluidFall() {}
    public static int decayDelay(boolean water,boolean ultraWarm) {
        return io.github.ningmeng123.mite.rules.UnderworldSpringRules.freefallDecayDelay(water,ultraWarm);
    }
    public static boolean tryFall(Level level,BlockPos pos,FluidState state) {
        if (!(level instanceof ServerLevel server) || !(level.getBlockState(pos).getBlock() instanceof LiquidBlock)
                || !state.is(FluidTags.WATER) && !state.is(FluidTags.LAVA)
                || pos.getY()<=level.getMinBuildHeight() || !level.hasChunkAt(pos.below()) || !level.getBlockState(pos.below()).isAir()) return false;
        boolean near=level.players().stream().anyMatch(player->player.isAlive()&&!player.isSpectator()
                && square(pos.getX()-player.blockPosition().getX())+square(pos.getZ()-player.blockPosition().getZ())<256D);
        if (!near) return false;
        var type=state.getType();
        if (level.getFluidState(pos.above()).getType().isSame(type)) return false;
        for (Direction direction:Direction.Plane.HORIZONTAL) {
            var neighbor=level.getFluidState(pos.relative(direction));
            if (level.getBlockState(pos.relative(direction)).getBlock() instanceof LiquidBlock
                    && neighbor.isSource() && neighbor.getType().isSame(type)) return false;
        }
        boolean water=state.is(FluidTags.WATER);
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        TemporaryFluidChanges.get(server).cancel(pos);
        BlockPos target=pos.below();
        if (water && MantleHeat.evaporatesWater(level,target)) {
            level.levelEvent(1501,target,0);return true;
        }
        var fluid=water?Fluids.WATER:Fluids.LAVA;
        level.setBlock(target,fluid.defaultFluidState().createLegacyBlock(),3);
        TemporaryFluidChanges.get(server).schedule(target,water,level.getGameTime()+decayDelay(water,level.dimensionType().ultraWarm()));
        level.scheduleTick(target,fluid,fluid.getTickDelay(level));
        return true;
    }
    private static double square(double value) { return value*value; }
}
