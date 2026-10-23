package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.ClientIngestionDelay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject early ingestion before local use state and its server packet can diverge. @author NingMeng123 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientIngestionUseMixin {
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void mite$ingestionReadyBeforeUse(Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        // Consume the input without starting use, so a waiting meal cannot fall through to the offhand.
        if (!ClientIngestionDelay.ready(Minecraft.getInstance(), player.getItemInHand(hand)))
            cir.setReturnValue(InteractionResult.CONSUME);
    }
}
