package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source anvils are portable regardless of held tool. @author NingMeng123 */
@Mixin(Player.class)
public abstract class FunctionalPlayerHarvestMixin {
    @Inject(method = "hasCorrectToolForDrops", at = @At("HEAD"), cancellable = true)
    private void mite$portableAnvil(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof AnvilBlock) cir.setReturnValue(true);
    }
}
