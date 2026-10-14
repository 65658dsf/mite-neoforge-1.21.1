package io.github.ningmeng123.mite.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent aqs metadata4 identity; host appearance and clone behavior remain ordinary stone. @author NingMeng123 */
public final class R196CopperspineBlock extends InfestedBlock {
    public static final MapCodec<R196CopperspineBlock> CODEC=simpleCodec(R196CopperspineBlock::new);
    public R196CopperspineBlock(Properties properties) { super(Blocks.STONE,properties); }
    @Override public MapCodec<R196CopperspineBlock> codec() { return CODEC; }
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state) { return new ItemStack(Items.STONE); }
}
