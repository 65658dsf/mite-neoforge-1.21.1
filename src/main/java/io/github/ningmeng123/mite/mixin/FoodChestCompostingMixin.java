package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.ChestWormComposting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds server compost checks while preserving the native client chest lid ticker. @author NingMeng123 */
@Mixin(ChestBlock.class)
public abstract class FoodChestCompostingMixin {
    @Inject(method = "getTicker", at = @At("RETURN"), cancellable = true)
    private <T extends BlockEntity> void mite$serverComposting(Level level, BlockState state, BlockEntityType<T> type,
            CallbackInfoReturnable<BlockEntityTicker<T>> cir) {
        if (level.isClientSide || type != BlockEntityType.CHEST && type != BlockEntityType.TRAPPED_CHEST
                && type != io.github.ningmeng123.mite.registry.MiteFunctionalBlocks.STRONGBOX_ENTITY.get()) return;
        BlockEntityTicker<T> previous = cir.getReturnValue();
        cir.setReturnValue((world, pos, blockState, entity) -> {
            if (previous != null) previous.tick(world, pos, blockState, entity);
            if (world instanceof ServerLevel server && entity instanceof ChestBlockEntity chest) ChestWormComposting.tick(server, chest);
        });
    }
}
