package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.loot.NaturalFoodLootModifier;
import io.github.ningmeng123.mite.survival.LeafFoodWindfall;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attached natural leaves must receive random ticks too, rather than only decaying leaves. @author NingMeng123 */
@Mixin(LeavesBlock.class)
public abstract class FoodLeavesWindfallMixin {
    @Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true)
    private void mite$naturalTreeTicks(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (NaturalFoodLootModifier.originalLeaf(state.getBlock()) && !state.getValue(LeavesBlock.PERSISTENT)) cir.setReturnValue(true);
    }
    @Inject(method = "randomTick", at = @At("TAIL"))
    private void mite$foodWindfall(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        LeafFoodWindfall.tryDrop(state, level, pos, random);
    }
}
