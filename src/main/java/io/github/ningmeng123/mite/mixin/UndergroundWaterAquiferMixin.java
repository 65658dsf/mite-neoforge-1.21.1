package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.rules.UndergroundWaterRules;
import io.github.ningmeng123.mite.worldgen.OverworldAquiferAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reduces isolated underground reservoirs while preserving surface-connected water. @author NingMeng123 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class UndergroundWaterAquiferMixin implements OverworldAquiferAccess {
    @Shadow @Final private NoiseChunk noiseChunk;
    @Shadow @Final private Aquifer.FluidPicker globalFluidPicker;
    @Shadow @Final private PositionalRandomFactory positionalRandomFactory;
    @Unique private PositionalRandomFactory mite$waterRegions;
    @Unique private boolean mite$undergroundWaterReduction;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mite$initializeWaterRegions(CallbackInfo callback) {
        mite$waterRegions = positionalRandomFactory.fromHashOf(UndergroundWaterRules.RANDOM_SALT).forkPositional();
    }

    @Override public boolean mite$undergroundWaterReduction() {
        return mite$undergroundWaterReduction;
    }

    @Override public void mite$setUndergroundWaterReduction(boolean enabled) {
        mite$undergroundWaterReduction = enabled;
    }

    @WrapOperation(method = "computeFluid", at = @At(value = "NEW", target =
            "(ILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/levelgen/Aquifer$FluidStatus;"))
    private Aquifer.FluidStatus mite$reduceUndergroundWater(int fluidLevel, BlockState fluidType,
            Operation<Aquifer.FluidStatus> original, int blockX, int blockY, int blockZ) {
        if (mite$undergroundWaterReduction && fluidLevel != DimensionType.WAY_BELOW_MIN_Y && fluidType.is(Blocks.WATER)) {
            int surfaceWaterY = noiseChunk.preliminarySurfaceLevel(blockX, blockZ) + 8;
            if (!globalFluidPicker.computeFluid(blockX, surfaceWaterY, blockZ).at(surfaceWaterY).isAir()) {
                return original.call(fluidLevel, fluidType);
            }
            long sample = mite$waterRegions.at(UndergroundWaterRules.horizontalRegion(blockX),
                    UndergroundWaterRules.verticalRegion(blockY), UndergroundWaterRules.horizontalRegion(blockZ)).nextLong();
            if (UndergroundWaterRules.suppress(sample)) fluidLevel = DimensionType.WAY_BELOW_MIN_Y;
        }
        return original.call(fluidLevel, fluidType);
    }
}
