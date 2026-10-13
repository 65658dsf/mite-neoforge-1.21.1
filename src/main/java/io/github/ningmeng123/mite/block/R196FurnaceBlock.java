package io.github.ningmeng123.mite.block;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.block.entity.R196FurnaceBlockEntity;
import io.github.ningmeng123.mite.registry.MiteFurnaces;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.item.ItemStack;
import java.util.function.BiConsumer;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Source furnace blocks use the modern furnace interaction and animations. @author NingMeng123 */
public final class R196FurnaceBlock extends FurnaceBlock {
    public static final MapCodec<FurnaceBlock> CODEC = simpleCodec(R196FurnaceBlock::new);

    public R196FurnaceBlock(Properties properties) { super(properties); }
    @Override public MapCodec<FurnaceBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new R196FurnaceBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createFurnaceTicker(level, type, MiteFurnaces.BLOCK_ENTITY.get());
    }
    @Override protected void openContainer(Level level, BlockPos pos, Player player) {
        if (!io.github.ningmeng123.mite.furnace.FurnaceThermalController.frontAccessible(level, pos, level.getBlockState(pos))) return;
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            player.openMenu((MenuProvider) furnace);
            player.awardStat(Stats.INTERACT_WITH_FURNACE);
        }
    }
    @Override protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion,
            BiConsumer<ItemStack, BlockPos> drops) {
        FurnaceExplosionDrops.explode(state, level, pos, explosion, drops);
    }
}
