package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.progression.PlayerPlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player sand retains its identity through falling, saving and concrete conversion. @author NingMeng123 */
@Mixin(FallingBlockEntity.class)
public abstract class GearPlacedFallingMixin {
    @Unique private static final String MITE_PLAYER_PLACED = "mite:player_placed_gear_block";
    @Unique private BlockPos mite$gearLanding;

    @WrapMethod(method = "fall")
    private static FallingBlockEntity mite$carryPlayerOrigin(Level level, BlockPos pos, BlockState state,
            Operation<FallingBlockEntity> original) {
        boolean placed = PlayerPlacedBlocks.isPlaced(level, pos);
        FallingBlockEntity falling = original.call(level, pos, state);
        if (placed) falling.getPersistentData().putBoolean(MITE_PLAYER_PLACED, true);
        return falling;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mite$beginPlayerLanding(CallbackInfo ci) { mite$gearLanding = null; }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean mite$landPlayerBlock(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        FallingBlockEntity self = (FallingBlockEntity)(Object)this;
        boolean placed = self.getPersistentData().getBoolean(MITE_PLAYER_PLACED);
        boolean previousPlaced = placed && PlayerPlacedBlocks.isPlaced(level, pos);
        if (placed) PlayerPlacedBlocks.set(level, pos, true);
        boolean changed = original.call(level, pos, state, flags);
        if (placed && changed && !level.getBlockState(pos).isAir()) {
            PlayerPlacedBlocks.set(level, pos, true);
            mite$gearLanding = pos.immutable();
        } else if (placed && !changed) PlayerPlacedBlocks.set(level, pos, previousPlaced);
        else if (placed && level.getBlockState(pos).isAir()) PlayerPlacedBlocks.set(level, pos, false);
        return changed;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void mite$keepConvertedPlayerBlock(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity)(Object)this;
        if (mite$gearLanding != null && !self.level().getBlockState(mite$gearLanding).isAir())
            PlayerPlacedBlocks.set(self.level(), mite$gearLanding, true);
        mite$gearLanding = null;
    }
}
