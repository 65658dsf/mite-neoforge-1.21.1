package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Direct actions, including fake-player calls, obey the same target-dependent reach as selection. @author NingMeng123 */
@Mixin(Player.class)
public abstract class SourcePlayerReachMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void mite$meleeReach(Entity target, CallbackInfo ci) {
        if (!SourcePlayerReach.canMelee((Player)(Object)this, target)) ci.cancel();
    }
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void mite$interactionReach(Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!SourcePlayerReach.canInteract((Player)(Object)this, target)) cir.setReturnValue(InteractionResult.PASS);
    }
    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void mite$sourceBlockReach(BlockPos pos, double padding, CallbackInfoReturnable<Boolean> cir) {
        // Padding4 is the native open-menu retention radius, not a new click/harvest.
        // Source containers also retain their independent validity distance after being opened.
        if (padding <= 1D) cir.setReturnValue(SourcePlayerReach.canReachBlock((Player)(Object)this, pos, InteractionHand.MAIN_HAND));
    }
}
