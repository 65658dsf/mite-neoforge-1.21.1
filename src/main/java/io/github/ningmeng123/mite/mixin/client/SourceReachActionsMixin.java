package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A selectable entity may be within interaction reach but outside the shorter melee reach. @author NingMeng123 */
@Mixin(MultiPlayerGameMode.class)
public abstract class SourceReachActionsMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void mite$meleePointReach(Player player, Entity target, CallbackInfo ci) {
        var hit = Minecraft.getInstance().hitResult;
        if (!SourcePlayerReach.canMelee(player, target) || hit instanceof EntityHitResult entity && entity.getEntity() == target
                && !SourcePlayerReach.canReachEntityAt(player, target, hit.getLocation(), true, 1F)) ci.cancel();
    }
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void mite$interactReach(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!SourcePlayerReach.canInteract(player, target)) cir.setReturnValue(InteractionResult.PASS);
    }
    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void mite$interactPointReach(Player player, Entity target, EntityHitResult hit, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!SourcePlayerReach.canReachEntityAt(player, target, hit.getLocation(), false, 1F)) cir.setReturnValue(InteractionResult.PASS);
    }
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void mite$blockUseReach(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!SourcePlayerReach.canReachBlock(player, hit.getBlockPos(), hand)) cir.setReturnValue(InteractionResult.FAIL);
    }
    @Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("HEAD"), cancellable = true)
    private void mite$blockDigReach(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        var player = Minecraft.getInstance().player;
        if (player != null && !SourcePlayerReach.canReachBlock(player, pos, InteractionHand.MAIN_HAND)) cir.setReturnValue(false);
    }
}
