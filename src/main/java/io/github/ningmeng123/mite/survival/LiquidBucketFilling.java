package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.world.TemporaryFluidChanges;
import io.github.ningmeng123.mite.world.BucketPlacementController;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Ordinary collection preserves liquid; only the matching Ctrl use removes its target. @author NingMeng123 */
public final class LiquidBucketFilling {
    private LiquidBucketFilling() {}

    public static BlockHitResult includeFlowingLiquids(Level level, Player player, BlockHitResult nativeHit) {
        var eye = player.getEyePosition();
        var end = eye.add(player.getViewVector(1F).scale(player.blockInteractionRange()));
        var liquidHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (liquidHit.getType() == HitResult.Type.BLOCK
                && isCollectible(level.getBlockState(liquidHit.getBlockPos()))
                && (nativeHit.getType() == HitResult.Type.MISS
                || eye.distanceToSqr(liquidHit.getLocation()) < eye.distanceToSqr(nativeHit.getLocation()))) {
            return liquidHit;
        }
        return nativeHit;
    }

    public static ItemStack pickup(Player player, LevelAccessor level, BlockPos pos, BlockState state,
            Supplier<ItemStack> nativePickup) {
        // Waterlogged blocks, powder snow and other modded pickup blocks retain native handling.
        if (!isCollectible(state)) return nativePickup.get();
        if (BucketPlacementController.controlDown(player)) {
            if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11)) return ItemStack.EMPTY;
            if (level instanceof ServerLevel server) TemporaryFluidChanges.get(server).cancel(pos);
        }
        return new ItemStack(state.is(Blocks.WATER) ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
    }

    private static boolean isCollectible(BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.LAVA);
    }
}
