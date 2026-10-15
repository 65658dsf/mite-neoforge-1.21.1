package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only actual final template writes, excluding temporary barriers and neighbor repairs. @author NingMeng123 */
@Mixin(StructureTemplate.class)
public abstract class TrialTemplatePlacementMixin {
    @WrapOperation(method = "placeInWorld", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/ServerLevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", ordinal = 1))
    private boolean mite$rememberTemplateBlock(ServerLevelAccessor level, BlockPos pos, BlockState state, int flags,
            Operation<Boolean> original) {
        boolean placed = original.call(level, pos, state, flags);
        if (placed && level instanceof WorldGenLevel generationLevel)
            NaturalTrialBlocks.recordGenerated(generationLevel, pos, state);
        return placed;
    }
}
