package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.world.TemporaryFluidChanges;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
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

/** Player buckets can collect flowing water without changing lava or dispenser pickup. @author NingMeng123 */
public final class WaterBucketFilling {
    private WaterBucketFilling() {}

    public static BlockHitResult includeFlowingWater(Level level, Player player, BlockHitResult nativeHit) {
        var eye = player.getEyePosition();
        var end = eye.add(player.getViewVector(1F).scale(player.blockInteractionRange()));
        var waterHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.WATER, player));
        if (waterHit.getType() == HitResult.Type.BLOCK
                && level.getBlockState(waterHit.getBlockPos()).is(Blocks.WATER)
                && eye.distanceToSqr(waterHit.getLocation()) < eye.distanceToSqr(nativeHit.getLocation())) {
            return waterHit;
        }
        return nativeHit;
    }

    public static ItemStack pickup(LevelAccessor level, BlockPos pos, BlockState state, Supplier<ItemStack> nativePickup) {
        ItemStack filled;
        if (state.is(Blocks.WATER) && !state.getFluidState().isSource()) {
            filled = level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11) ? new ItemStack(Items.WATER_BUCKET) : ItemStack.EMPTY;
        } else {
            filled = nativePickup.get();
        }
        if (!filled.isEmpty() && state.getFluidState().is(FluidTags.WATER) && level instanceof ServerLevel server) {
            TemporaryFluidChanges.get(server).cancel(pos);
        }
        return filled;
    }
}
