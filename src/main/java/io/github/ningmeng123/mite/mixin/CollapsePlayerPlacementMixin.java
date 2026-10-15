package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.world.CollapsePhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe every committed placement snapshot after NeoForge's complete placement event, including multi-place items. @author NingMeng123 */
@Mixin(CommonHooks.class)
public abstract class CollapsePlayerPlacementMixin {
    @WrapOperation(method = "onPlaceItemIntoWorld", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;onPlace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private static void mite$acceptedSoilPlacement(BlockState state, Level level, BlockPos pos, BlockState previous,
            boolean moved, Operation<Void> original, @Local(argsOnly = true) UseOnContext context) {
        original.call(state, level, pos, previous, moved);
        Player player = context.getPlayer();
        if (player != null && level instanceof ServerLevel server && CollapsePhysics.managed(state)
                && level.getBlockState(pos).equals(state))
            CollapsePhysics.playerPlaced(server, pos, player);
    }
}
