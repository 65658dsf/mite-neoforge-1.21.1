package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.CollapseFallingBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source dirt and soul sand reuse the complete native falling entity lifecycle. @author NingMeng123 */
@Mixin(FallingBlockEntity.class)
public abstract class CollapseFallingEntityMixin {
    @Inject(method = "fall", at = @At("RETURN"))
    private static void mite$enableSoilImpact(Level level, BlockPos pos, BlockState state,
            CallbackInfoReturnable<FallingBlockEntity> cir) {
        CollapseFallingBlocks.configureImpact(cir.getReturnValue());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mite$restoreSoilImpact(CompoundTag tag, CallbackInfo ci) {
        // Also upgrade soil already falling in a save made before impact damage was enabled.
        CollapseFallingBlocks.configureImpact((FallingBlockEntity)(Object)this);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/item/FallingBlockEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            shift = At.Shift.AFTER))
    private void mite$sourceCollapseCollision(CallbackInfo ci) {
        CollapseFallingBlocks.afterMove((FallingBlockEntity)(Object)this);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/FallingBlock;isFree(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean mite$sourceCollapseSupport(BlockState state, Operation<Boolean> original) {
        return CollapseFallingBlocks.canContinueThroughSupport((FallingBlockEntity)(Object)this, original.call(state));
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;canBeReplaced(Lnet/minecraft/world/item/context/BlockPlaceContext;)Z"))
    private boolean mite$respectCollapseObstacleVeto(BlockState state, BlockPlaceContext context, Operation<Boolean> original) {
        return CollapseFallingBlocks.canLandReplace((FallingBlockEntity)(Object)this, context.getClickedPos(), state,
                original.call(state, context));
    }
}
