package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/** Prevents tripwire updates from restoring removed lines and hooks. @author NingMeng123 */
@Mixin(TripWireHookBlock.class)
public abstract class TripWireDuplicationMixin {
    @WrapOperation(method = "calculateState", require = 2,
            slice = @Slice(to = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z")),
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean mite$updateExistingHook(Level level, BlockPos pos, BlockState state, int flags,
            Operation<Boolean> original) {
        BlockState current = level.getBlockState(pos);
        if (!current.is(state.getBlock())
                || current.getValue(TripWireHookBlock.FACING) != state.getValue(TripWireHookBlock.FACING)) {
            return false;
        }

        return original.call(level, pos, state, flags);
    }

    @WrapOperation(method = "calculateState",
            slice = @Slice(from = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z")),
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean mite$updateExistingTripwire(Level level, BlockPos pos, BlockState state, int flags,
            Operation<Boolean> original) {
        BlockState current = level.getBlockState(pos);
        if (!current.is(state.getBlock())) {
            return false;
        }

        return original.call(level, pos, current.trySetValue(TripWireBlock.ATTACHED,
                state.getOptionalValue(TripWireBlock.ATTACHED).orElse(false)), flags);
    }
}
