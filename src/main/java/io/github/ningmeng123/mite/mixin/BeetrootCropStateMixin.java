package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Beetroot defines its own age property without invoking the parent state builder. @author NingMeng123 */
@Mixin(BeetrootBlock.class)
public abstract class BeetrootCropStateMixin {
    @Inject(method="createBlockStateDefinition",at=@At("TAIL"))
    private void mite$diseaseState(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci) {
        builder.add(CubeCropBlight.BLIGHTED,CubeCropBlight.DEAD);
    }
}
