package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.BucketPlacementController;
import io.github.ningmeng123.mite.world.BucketPlacementIntent;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only the corresponding real use packet may consume a pending Ctrl placement request. @author NingMeng123 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BucketPlacementInputMixin {
    @WrapOperation(method = "handleUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItem(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult mite$consumeBucketIntent(ServerPlayerGameMode gameMode, ServerPlayer player,
            Level level, ItemStack stack, InteractionHand hand, Operation<InteractionResult> original,
            ServerboundUseItemPacket packet) {
        boolean sourceRequested = BucketPlacementIntent.consume(player, hand, packet.getSequence());
        return BucketPlacementController.withIntent(player, sourceRequested,
                () -> original.call(gameMode, player, level, stack, hand));
    }
}
