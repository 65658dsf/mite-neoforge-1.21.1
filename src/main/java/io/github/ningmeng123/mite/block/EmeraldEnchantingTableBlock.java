package io.github.ningmeng123.mite.block;

import io.github.ningmeng123.mite.block.entity.EmeraldEnchantingTableBlockEntity;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Source emerald table is the lower-power alternative to diamond. @author NingMeng123 */
public final class EmeraldEnchantingTableBlock extends EnchantingTableBlock {
    public EmeraldEnchantingTableBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockState(pos.above()).isCollisionShapeFullBlock(level, pos.above())) return InteractionResult.PASS;
        return super.useWithoutItem(state, level, pos, player, hit);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EmeraldEnchantingTableBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, MiteFunctionalBlocks.EMERALD_ENTITY.get(), EnchantingTableBlockEntity::bookAnimationTick) : null;
    }
}
