package io.github.ningmeng123.mite.block;

import io.github.ningmeng123.mite.block.entity.EmeraldEnchantingTableBlockEntity;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Emerald tables use native enchanting with their existing lower point price. @author NingMeng123 */
public final class EmeraldEnchantingTableBlock extends EnchantingTableBlock {
    public EmeraldEnchantingTableBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EmeraldEnchantingTableBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, MiteFunctionalBlocks.EMERALD_ENTITY.get(), EnchantingTableBlockEntity::bookAnimationTick) : null;
    }
}
