package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.world.BucketPlacementController;
import io.github.ningmeng123.mite.world.PlacementDelay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/** Shared by vanilla iron buckets and the inherited metal-bucket placement path. @author NingMeng123 */
@Mixin(BucketItem.class)
public abstract class BucketSourcePlacementMixin {
    @WrapMethod(method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z")
    private boolean mite$placeSourceWithExperience(Player player, Level level, BlockPos pos, BlockHitResult hit,
            ItemStack container, Operation<Boolean> original) {
        if (PlacementDelay.blocked(player)) return false;
        BucketItem bucket = (BucketItem) (Object) this;
        boolean placed = BucketPlacementController.supports(bucket)
                ? BucketPlacementController.empty(bucket, player, level, pos, hit, container,
                        () -> original.call(player, level, pos, hit, container))
                : original.call(player, level, pos, hit, container);
        if (placed) PlacementDelay.placed(player);
        return placed;
    }
}
