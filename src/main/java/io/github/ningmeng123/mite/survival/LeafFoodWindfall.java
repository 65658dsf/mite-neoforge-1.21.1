package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.loot.NaturalFoodLootModifier;
import io.github.ningmeng123.mite.registry.MiteFoods;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** R196 natural tree windfalls: 1/100 times 1/500 per random tick. @author NingMeng123 */
public final class LeafFoodWindfall {
    private LeafFoodWindfall() {}
    public static boolean tryDrop(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!NaturalFoodLootModifier.originalLeaf(state.getBlock()) || state.getValue(LeavesBlock.PERSISTENT)
                || !level.getBlockState(pos).is(state.getBlock()) || random.nextInt(100) != 0 || random.nextInt(500) != 0) return false;
        var biome = level.getBiome(pos);
        if ((biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) && random.nextBoolean()) return false;
        Item result = Items.STICK;
        if (state.is(Blocks.OAK_LEAVES) && random.nextInt(3) > 0)
            result = biome.is(BiomeTags.IS_JUNGLE) ? MiteFoods.get("orange") : Items.APPLE;
        else if (state.is(Blocks.JUNGLE_LEAVES) && random.nextInt(3) > 0) result = MiteFoods.get("banana");
        Block.popResource(level, pos, new ItemStack(result));
        return true;
    }
}
