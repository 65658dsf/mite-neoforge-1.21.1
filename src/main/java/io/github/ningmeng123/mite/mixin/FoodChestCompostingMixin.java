package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.ChestWormComposting;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Chest composting follows native section random ticks; client lid ticking stays native. @author NingMeng123 */
@Mixin(ChestBlock.class)
public abstract class FoodChestCompostingMixin extends AbstractChestBlock<ChestBlockEntity> {
    protected FoodChestCompostingMixin(BlockBehaviour.Properties properties,
            Supplier<BlockEntityType<? extends ChestBlockEntity>> type) {
        super(properties, type);
    }

    @Override protected boolean isRandomlyTicking(BlockState state) { return true; }

    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) ChestWormComposting.process(chest, random);
    }
}
