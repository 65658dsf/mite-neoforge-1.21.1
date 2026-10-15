package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.survival.CropHarvest;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import io.github.ningmeng123.mite.survival.SpecialPlantGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Source snowfall destroys crops only inside native precipitation and accumulation checks. @author NingMeng123 */
@Mixin(ServerLevel.class)
public abstract class CropSnowfallMixin {
    private static boolean mite$snowReplaceable(BlockState state) {
        return CropLifecycle.managed(state) || SpecialPlantGrowth.managedStem(state);
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean mite$snowCanCoverCrop(Biome biome, LevelReader level, BlockPos pos, Operation<Boolean> original) {
        if (original.call(biome, level, pos)) return true;
        return level instanceof ServerLevel server && server.isAreaLoaded(pos, 1)
                && pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()
                && biome.getPrecipitationAt(pos) == Biome.Precipitation.SNOW
                && level.getBrightness(LightLayer.BLOCK, pos) < 10 && mite$snowReplaceable(level.getBlockState(pos))
                && Blocks.SNOW.defaultBlockState().canSurvive(level, pos);
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean mite$dropCropBeforeSnow(ServerLevel level, BlockPos pos, BlockState replacement, Operation<Boolean> original) {
        BlockState crop = level.getBlockState(pos);
        if (!replacement.is(Blocks.SNOW) || !mite$snowReplaceable(crop)) return original.call(level, pos, replacement);
        boolean[] changed = {false};
        CropHarvest.withReason(CropHarvest.Reason.SNOW, level, pos, () -> {
            Block.dropResources(crop, level, pos);
            changed[0] = original.call(level, pos, replacement);
        });
        return changed[0];
    }
}
