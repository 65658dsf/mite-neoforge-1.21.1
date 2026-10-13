package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source furnace openings must remain accessible. @author NingMeng123 */
@Mixin(AbstractFurnaceBlock.class)
public abstract class FurnaceFrontMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void mite$front(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (FurnaceThermalController.profile(state) != null && !FurnaceThermalController.frontAccessible(level, pos, state))
            cir.setReturnValue(InteractionResult.FAIL);
    }
}
