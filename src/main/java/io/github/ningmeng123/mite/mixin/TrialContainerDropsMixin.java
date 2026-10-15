package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.TrialBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppress only destruction spills; opening containers and vault ejection stay native. @author NingMeng123 */
@Mixin(Containers.class)
public abstract class TrialContainerDropsMixin {
    @Inject(method = "dropContentsOnDestroy", at = @At("HEAD"), cancellable = true)
    private static void mite$naturalTrialContents(BlockState oldState, BlockState newState, Level level,
            BlockPos pos, CallbackInfo ci) {
        // Pot contents are exploration rewards and deliberately survive the pot's destruction.
        if (!oldState.is(Blocks.DECORATED_POT) && !oldState.is(newState.getBlock())
                && TrialBlockDrops.suppresses(level, pos)) {
            if (level.getBlockEntity(pos) instanceof Container) {
                level.updateNeighbourForOutputSignal(pos, oldState.getBlock());
            }
            ci.cancel();
        }
    }
}
