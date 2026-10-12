package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.mining.MiningProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shares R196 mining progress between client prediction and server authority. @author NingMeng123 */
@Mixin(BlockBehaviour.class)
public abstract class BlockMiningProgressMixin {
    @Inject(method = "getDestroyProgress(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void mite$applyMiningProgress(BlockState state, Player player, BlockGetter level, BlockPos pos,
            CallbackInfoReturnable<Float> cir) {
        if (!player.isCreative() && !player.isSpectator()) {
            cir.setReturnValue(MiningProgress.destroyProgress(player, state, level, pos));
        }
    }
}
