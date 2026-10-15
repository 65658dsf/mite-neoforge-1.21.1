package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.FoodAgriculture;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Manure increases crop growth by 1.5 and has a 1/256 loss chance after growth. @author NingMeng123 */
@Mixin(CropBlock.class)
public abstract class FoodCropFertilizationMixin {
    @Inject(method = "getGrowthSpeed", at = @At("RETURN"), cancellable = true)
    private static void mite$fertilizedGrowth(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (state.getBlock() instanceof CropBlock && FoodAgriculture.fertilized(level.getBlockState(pos.below())))
            cir.setReturnValue(cir.getReturnValue() * 1.5F);
    }
    @Inject(method = "randomTick", at = @At("TAIL"))
    private void mite$fertilizerWear(BlockState before, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        BlockState after = level.getBlockState(pos);
        CropBlock crop = (CropBlock) (Object) this;
        if (!CropLifecycle.managed(before) && after.is(before.getBlock()) && crop.getAge(after) > crop.getAge(before))
            FoodAgriculture.onCropGrowth(level, pos.below(), random);
    }
}
