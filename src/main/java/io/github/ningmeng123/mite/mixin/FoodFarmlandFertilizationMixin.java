package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.FoodAgriculture;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds R196's fertilizer bit to native farmland, saved in the chunk block state. @author NingMeng123 */
@Mixin(FarmBlock.class)
public abstract class FoodFarmlandFertilizationMixin extends Block {
    protected FoodFarmlandFertilizationMixin(BlockBehaviour.Properties properties) { super(properties); }
    @Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
    private void mite$fertilizerProperty(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(FoodAgriculture.FERTILIZED);
    }
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mite$initialFertilization(BlockBehaviour.Properties properties, CallbackInfo ci) {
        registerDefaultState(defaultBlockState().setValue(FoodAgriculture.FERTILIZED, false));
    }
}
