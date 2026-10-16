package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Replace the padded single-radius packet gate with exact melee/interaction validation. @author NingMeng123 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SourcePlayerEntityReachPacketMixin {
    @Shadow public ServerPlayer player;

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;canInteractWithBlock(Lnet/minecraft/core/BlockPos;D)Z"))
    private boolean mite$offhandBlockPacketReach(ServerPlayer actor, BlockPos pos, double padding,
            Operation<Boolean> original, ServerboundUseItemOnPacket packet) {
        return SourcePlayerReach.canReachBlock(actor, pos, packet.getHand());
    }

    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z"))
    private boolean mite$deferToSpecificAction(ServerPlayer actor, AABB box, double padding, Operation<Boolean> original) {
        return true; // World border/thread/invalid-entity checks stay native; every dispatched action below is checked.
    }

    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;dispatch(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket$Handler;)V"))
    private void mite$validateSpecificAction(ServerboundInteractPacket packet, ServerboundInteractPacket.Handler handler,
            Operation<Void> original) {
        Entity target = packet.getTarget(player.serverLevel());
        if (target == null) return;
        original.call(packet, new ServerboundInteractPacket.Handler() {
            @Override public void onInteraction(InteractionHand hand) {
                if (SourcePlayerReach.canInteract(player, target)) handler.onInteraction(hand);
            }
            @Override public void onInteraction(InteractionHand hand, Vec3 relative) {
                if (SourcePlayerReach.canInteractAt(player, target, relative)) handler.onInteraction(hand, relative);
            }
            @Override public void onAttack() {
                if (SourcePlayerReach.canMelee(player, target)) handler.onAttack();
            }
        });
    }
}
