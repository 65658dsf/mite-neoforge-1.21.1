package io.github.ningmeng123.mite.block.entity;

import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Native name storage and animated book with a dedicated valid block entity type. @author NingMeng123 */
public final class EmeraldEnchantingTableBlockEntity extends EnchantingTableBlockEntity {
    public EmeraldEnchantingTableBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }
    @Override public BlockEntityType<?> getType() { return MiteFunctionalBlocks.EMERALD_ENTITY.get(); }
}
