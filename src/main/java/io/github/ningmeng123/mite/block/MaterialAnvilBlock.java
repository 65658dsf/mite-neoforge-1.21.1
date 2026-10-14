package io.github.ningmeng123.mite.block;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Three wear appearances share one material identity and persistent damage. @author NingMeng123 */
public final class MaterialAnvilBlock extends AnvilBlock implements EntityBlock {
    public static final IntegerProperty DAMAGE_STAGE = IntegerProperty.create("damage_stage", 0, 2);
    private final String material;
    public MaterialAnvilBlock(String material, BlockBehaviour.Properties properties) {
        super(properties);
        this.material = material;
        registerDefaultState(defaultBlockState().setValue(DAMAGE_STAGE, 0));
    }
    public String material() { return material; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DAMAGE_STAGE);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MaterialAnvilBlockEntity(pos, state);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof MaterialAnvilBlockEntity entity) entity.refreshAppearance();
    }
}
