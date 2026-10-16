package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.client.BucketPlacementInput;
import io.github.ningmeng123.mite.world.BucketPlacementController;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps local fluid prediction and the following server request in the same Ctrl mode. @author NingMeng123 */
@Mixin(MultiPlayerGameMode.class)
public abstract class BucketPlacementInputMixin {
    @WrapMethod(method = "useItem")
    private InteractionResult mite$predictBucketMode(Player player, InteractionHand hand,
            Operation<InteractionResult> original) {
        return BucketPlacementController.withIntent(player, Screen.hasControlDown(), () -> original.call(player, hand));
    }

    @WrapOperation(method = "startPrediction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void mite$sendBucketModeFirst(ClientPacketListener connection, Packet<?> packet, Operation<Void> original) {
        BucketPlacementInput.beforeSend(packet);
        original.call(connection, packet);
    }
}
