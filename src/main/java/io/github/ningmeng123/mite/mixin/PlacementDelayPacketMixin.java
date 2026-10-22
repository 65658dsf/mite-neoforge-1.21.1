package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.PlacementDelay;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlacementDelayPacketMixin {
    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayerGameMode;useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult mite$preparePlacement(ServerPlayerGameMode gameMode, ServerPlayer player, Level level,
            ItemStack stack, InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
        if (!player.hasInfiniteMaterials() && !player.isSpectator() && stack.getItem() instanceof BlockItem)
            return PlacementDelay.defer(player, hand, hit, false);
        return original.call(gameMode, player, level, stack, hand, hit);
    }
}
