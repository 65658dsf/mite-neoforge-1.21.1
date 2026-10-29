package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.world.PlacementDelay;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gate actual block placement, including water plants, without blocking other right-click actions. @author NingMeng123 */
@Mixin(BlockItem.class)
public abstract class PlacementCooldownBlockMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void mite$checkPlacementCooldown(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> callback) {
        if (PlacementDelay.placementBlocked(context.getPlayer())) callback.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void mite$completedBlockPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> callback) {
        if (callback.getReturnValue().consumesAction()) PlacementDelay.blockPlaced(context.getPlayer(), context.getLevel());
    }
}
