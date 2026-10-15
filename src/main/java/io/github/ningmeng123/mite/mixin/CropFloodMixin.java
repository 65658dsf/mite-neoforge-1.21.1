package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.survival.CropHarvest;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import io.github.ningmeng123.mite.survival.SpecialPlantGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A real water-flow destruction carries the source flood reason through native drops. @author NingMeng123 */
@Mixin(WaterFluid.class)
public abstract class CropFloodMixin {
    @WrapOperation(method = "beforeDestroyingBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void mite$floodCrop(BlockState state, LevelAccessor level, BlockPos pos, BlockEntity blockEntity, Operation<Void> original) {
        if (level instanceof ServerLevel server && (CropLifecycle.managed(state) || SpecialPlantGrowth.managedStem(state)))
            CropHarvest.withReason(CropHarvest.Reason.FLOOD, server, pos, () -> original.call(state, level, pos, blockEntity));
        else original.call(state, level, pos, blockEntity);
    }
}
