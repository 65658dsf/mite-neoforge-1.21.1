package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.worldgen.R196OutdoorChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** anr's extra blood-moon blight, using existing disease states and source integrated-server mode. @author NingMeng123 */
public final class LunarCropEffects {
    private LunarCropEffects() {}
    public static boolean exposed(ServerLevel level,BlockPos pos) {
        int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,pos.getX(),pos.getZ());
        for(BlockPos.MutableBlockPos above=pos.above().mutable();above.getY()<top;above.move(0,1,0))
            if(level.getBlockState(above).isSolidRender(level,above)) return R196OutdoorChecks.outdoors(level,pos);
        return true;
    }
    public static boolean infectOnRandomTick(ServerLevel level,BlockPos pos,BlockState state,RandomSource random) {
        if(!(state.getBlock() instanceof CropBlock) || CubeCropBlight.blighted(state) || CubeCropBlight.dead(state)
                || !LunarCalendar.state(level.getDayTime(),level.dimension().equals(Level.OVERWORLD)).bloodNight()) return false;
        // R196 tests the draw before its dedicated-server exclusion; avoid consuming draws on other moon periods.
        return random.nextFloat()<.25F && !level.getServer().isDedicatedServer() && exposed(level,pos)
                && CubeCropBlight.infect(level,pos);
    }
}
