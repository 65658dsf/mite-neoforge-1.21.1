package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import io.github.ningmeng123.mite.rules.DecorativeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve exact source flower selection-box dimensions with modern positional offsets. @author NingMeng123 */
@Mixin(FlowerBlock.class)
public abstract class DecorativeFlowerShapeMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void mite$sourceFlowerShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        int subtype = MiteDecorativeContent.flowerSubtype(state.getBlock());
        if (subtype < 0) return;
        float half = subtype == 1 ? 4.8F : 3.2F;
        var offset = state.getOffset(level, pos);
        cir.setReturnValue(Block.box(8 - half, 0, 8 - half, 8 + half, DecorativeRules.flowerHeight(subtype) * 16, 8 + half)
                .move(offset.x, offset.y, offset.z));
    }
}
