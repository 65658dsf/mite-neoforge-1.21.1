package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.mining.FunctionalBlockMining;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Portable blocks spare modern and third-party tools as well as source tools. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class PortableToolWearMixin {
    @Inject(method = "mineBlock", at = @At("HEAD"), cancellable = true)
    private void mite$portableWear(Level level, BlockState state, BlockPos pos, Player player, CallbackInfo ci) {
        if (FunctionalBlockMining.portable(player, state, level, pos)) ci.cancel();
    }
}
