package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.TimedCraftingCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Automation cannot bypass the same batch duration by sending additional redstone pulses. @author NingMeng123 */
@Mixin(CrafterBlock.class)
public abstract class TimedCraftingCrafterMixin {
    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void mite$timeAutomation(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (TimedCraftingCrafter.delay(state, level, pos)) ci.cancel();
    }
}
