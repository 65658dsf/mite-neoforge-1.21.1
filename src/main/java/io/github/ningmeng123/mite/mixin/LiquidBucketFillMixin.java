package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.survival.LiquidBucketFilling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Retains native bucket permissions, inventory handling, sounds and advancement triggers. @author NingMeng123 */
@Mixin(BucketItem.class)
public abstract class LiquidBucketFillMixin {
    @WrapOperation(method = "use", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/BucketItem;getPlayerPOVHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private BlockHitResult mite$targetFlowingLiquid(Level level, Player player, ClipContext.Fluid mode,
            Operation<BlockHitResult> original) {
        BlockHitResult hit = original.call(level, player, mode);
        return mode == ClipContext.Fluid.SOURCE_ONLY ? LiquidBucketFilling.includeFlowingLiquids(level, player, hit) : hit;
    }

    @WrapOperation(method = "use", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/BucketPickup;pickupBlock(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mite$collectLiquid(BucketPickup pickup, Player player, LevelAccessor level,
            BlockPos pos, BlockState state, Operation<ItemStack> original) {
        return LiquidBucketFilling.pickup(player, level, pos, state, () -> original.call(pickup, player, level, pos, state));
    }
}
