package io.github.ningmeng123.mite.block;

import io.github.ningmeng123.mite.registry.MiteFurnaces;
import io.github.ningmeng123.mite.registry.MiteShards;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** aok's material replacement drops, retaining the native inventory removal callback. @author NingMeng123 */
public final class FurnaceExplosionDrops {
    private FurnaceExplosionDrops() {}
    public static void explode(BlockState state, Level level, BlockPos pos, Explosion explosion,
            BiConsumer<ItemStack, BlockPos> drops) {
        if (explosion.getBlockInteraction() == Explosion.BlockInteraction.KEEP
                || explosion.getBlockInteraction() == Explosion.BlockInteraction.TRIGGER_BLOCK) return;
        if (level instanceof ServerLevel && state.canDropFromExplosion(level, pos, explosion)) {
            if (state.is(MiteFurnaces.SANDSTONE_FURNACE.get())) drops.accept(named(level, pos, new ItemStack(Items.SAND)), pos.immutable());
            else if (state.is(Blocks.FURNACE)) drops.accept(named(level, pos, new ItemStack(Items.GRAVEL)), pos.immutable());
            else if (state.is(MiteFurnaces.OBSIDIAN_FURNACE.get())) {
                int count = 0;
                for (int i = 0; i < 6; i++) if (level.random.nextFloat() < .5F) count++;
                if (count > 0) drops.accept(new ItemStack(MiteShards.OBSIDIAN_SHARD.get(), count), pos.immutable());
            }
            // Clay, hardened clay and netherrack model blocks produce no material after an explosion.
        }
        state.onBlockExploded(level, pos, explosion);
    }
    private static ItemStack named(Level level, BlockPos pos, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace && furnace.hasCustomName())
            stack.set(DataComponents.CUSTOM_NAME, furnace.getCustomName());
        return stack;
    }
}
