package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.NetherOrePlacement;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep each block of a native Nether ore vein inside its edited height range. @author NingMeng123 */
@Mixin(OreFeature.class)
public abstract class NetherOreHeightMixin {
    @Inject(method = "canPlaceOre", at = @At("HEAD"), cancellable = true)
    private static void mite$restrictNetherVeinHeight(BlockState host, Function<BlockPos, BlockState> lookup, RandomSource random,
            OreConfiguration configuration, OreConfiguration.TargetBlockState target, BlockPos.MutableBlockPos pos,
            CallbackInfoReturnable<Boolean> result) {
        if (!NetherOrePlacement.allows(target.state, pos)) result.setReturnValue(false);
    }
}
