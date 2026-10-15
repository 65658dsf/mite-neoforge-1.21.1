package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.enchantment.EnduranceActions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server charges active mining, including the first successful progress request. @author NingMeng123 */
@Mixin(ServerPlayerGameMode.class)
public abstract class EnduranceMiningMixin {
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "incrementDestroyProgress", at = @At("HEAD"))
    private void mite$sourceMiningTick(BlockState state, BlockPos pos, int start, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        EnduranceActions.mining(player);
    }

    @WrapOperation(method = "handleBlockBreakAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float mite$sourceInitialMining(BlockState state, Player miner, BlockGetter level, BlockPos target, Operation<Float> original,
            BlockPos actionPos, ServerboundPlayerActionPacket.Action action, Direction face, int maximumY, int sequence) {
        float progress = original.call(state, miner, level, target);
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && progress > 0F) EnduranceActions.mining(miner);
        return progress;
    }
}
