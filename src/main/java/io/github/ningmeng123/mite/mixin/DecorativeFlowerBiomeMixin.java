package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import io.github.ningmeng123.mite.world.DecorativePlants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 flower biome conditions cover both native counterparts and Dahlia. @author NingMeng123 */
@Mixin(BushBlock.class)
public abstract class DecorativeFlowerBiomeMixin {
    @Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true)
    private void mite$sourceFlowerClimate(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        int subtype = MiteDecorativeContent.flowerSubtype(state.getBlock());
        if (subtype >= 0 && cir.getReturnValueZ()) cir.setReturnValue(DecorativePlants.canFlowerSurvive(subtype, level, pos));
    }
}
