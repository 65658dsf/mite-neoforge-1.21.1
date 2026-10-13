package io.github.ningmeng123.mite.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;

/** aqs metadata 3 picks its host; modern silverfish supplies the explicitly documented mob adaptation. @author NingMeng123 */
public final class R196InfestedNetherrackBlock extends InfestedBlock {
    public static final MapCodec<R196InfestedNetherrackBlock> CODEC = simpleCodec(R196InfestedNetherrackBlock::new);
    public R196InfestedNetherrackBlock(Properties properties) { super(Blocks.NETHERRACK, properties); }
    @Override public MapCodec<R196InfestedNetherrackBlock> codec() { return CODEC; }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) { return new ItemStack(Items.NETHERRACK); }
}
