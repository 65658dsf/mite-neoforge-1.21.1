package io.github.ningmeng123.mite.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import io.github.ningmeng123.mite.item.RunestoneItem;
import io.github.ningmeng123.mite.portal.MitePortals;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import io.github.ningmeng123.mite.registry.MiteShards;
import java.util.function.BiConsumer;

/** R196's sixteen metadata symbols as persistent modern block states. @author NingMeng123 */
public final class RunestoneBlock extends Block {
    public static final IntegerProperty RUNE = IntegerProperty.create("rune", 0, 15);
    public static final String[] NAMES = {"Nul", "Quas", "Por", "An", "Nox", "Flam", "Vas", "Des",
            "Ort", "Tym", "Corp", "Lor", "Mani", "Jux", "Ylem", "Sanct"};
    public RunestoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNE, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNE);
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (!state.is(previous.getBlock())) MitePortals.refreshRuneRoutes(level, pos);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        super.onRemove(state, level, pos, replacement, moved);
        if (!state.is(replacement.getBlock())) MitePortals.refreshRuneRoutes(level, pos);
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return RunestoneItem.withRune(new ItemStack(this), state.getValue(RUNE));
    }
    @Override protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> drops) {
        if (explosion.getBlockInteraction() == Explosion.BlockInteraction.TRIGGER_BLOCK
                || explosion.getBlockInteraction() == Explosion.BlockInteraction.KEEP) return;
        if (level instanceof ServerLevel && state.canDropFromExplosion(level, pos, explosion)) {
            int count = 0;
            for (int i = 0; i < 6; i++) if (level.random.nextFloat() < .5F) count++;
            if (count > 0) drops.accept(new ItemStack(MiteShards.OBSIDIAN_SHARD.get(), count), pos.immutable());
        }
        state.onBlockExploded(level, pos, explosion);
    }
}
