package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.RemovedCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Existing blocks return their inventory before any automatic crafting can run. @author NingMeng123 */
@Mixin(CrafterBlock.class)
public abstract class RemovedCrafterMixin {
    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void mite$removeBeforeCrafting(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        RemovedCrafter.remove(level, pos);
        ci.cancel();
    }

    @Inject(method = "getTicker", at = @At("HEAD"), cancellable = true)
    private <T extends BlockEntity> void mite$removeTickingCrafter(Level level, BlockState state, BlockEntityType<T> type,
            CallbackInfoReturnable<BlockEntityTicker<T>> cir) {
        if (level instanceof ServerLevel server && type == BlockEntityType.CRAFTER) {
            cir.setReturnValue((owner, pos, current, entity) -> RemovedCrafter.remove(server, pos));
        }
    }

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void mite$removeBeforeOpening(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (level instanceof ServerLevel server) RemovedCrafter.remove(server, pos);
        cir.setReturnValue(InteractionResult.FAIL);
    }
}
