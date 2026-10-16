package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.enchantment.TreeFellingHarvestAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Revalidate both the first dig packet and its eventual harvest using the current effective tool. @author NingMeng123 */
@Mixin(ServerPlayerGameMode.class)
public abstract class SourcePlayerBlockReachMixin {
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void mite$startDigReach(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face,
            int maxHeight, int sequence, CallbackInfo ci) {
        if (action != ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK
                && !SourcePlayerReach.canReachBlock(player, pos, InteractionHand.MAIN_HAND)) ci.cancel();
    }
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void mite$completeDigReach(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        boolean felling = player.gameMode instanceof TreeFellingHarvestAccess access && access.mite$consumeExtraLogReach(pos);
        if (!felling && !SourcePlayerReach.canReachBlock(player, pos, InteractionHand.MAIN_HAND)) cir.setReturnValue(false);
    }
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void mite$useBlockReach(ServerPlayer actor, Level level, ItemStack stack, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!SourcePlayerReach.canReachBlock(actor, hit.getBlockPos(), hand)) cir.setReturnValue(InteractionResult.FAIL);
    }
}
