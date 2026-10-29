package io.github.ningmeng123.mite.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual recipe loading and inherited ordinary/cherry/mangrove tree growth. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class TreeAndBoneBalanceGameTests {
    @GameTest(template = "empty")
    public static void oneBoneMakesExactlyOneBoneMealInBothCraftingGrids(GameTestHelper helper) {
        for (int width : new int[]{2, 3}) {
            List<ItemStack> slots = new ArrayList<>();
            for (int index = 0; index < width * width; index++) slots.add(ItemStack.EMPTY);
            slots.set(slots.size() - 1, new ItemStack(Items.BONE));
            CraftingInput input = CraftingInput.of(width, width, slots);
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            helper.assertValueEqual(recipe.id(), ResourceLocation.withDefaultNamespace("bone_meal"), "The original recipe identity is overridden");
            ItemStack result = recipe.value().assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(Items.BONE_MEAL) && result.getCount() == 1, "One bone yields only one bone meal in width " + width);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryCherryAndGroundedMangroveShareTheCropPacedGrowthGate(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        long blockedSeed = seedFor(false);
        long acceptedSeed = seedFor(true);
        var old = helper.getLevel().getBlockState(pos);
        try {
            for (var block : new net.minecraft.world.level.block.Block[]{Blocks.OAK_SAPLING, Blocks.SPRUCE_SAPLING,
                    Blocks.BIRCH_SAPLING, Blocks.JUNGLE_SAPLING, Blocks.ACACIA_SAPLING, Blocks.DARK_OAK_SAPLING,
                    Blocks.CHERRY_SAPLING, Blocks.MANGROVE_PROPAGULE}) {
                var state = block.defaultBlockState();
                helper.getLevel().setBlock(pos, state, 2);
                SaplingBlock sapling = (SaplingBlock) block;
                sapling.advanceTree(helper.getLevel(), pos, state, RandomSource.create(blockedSeed));
                helper.assertValueEqual(helper.getLevel().getBlockState(pos).getValue(SaplingBlock.STAGE), 0,
                        "The shared 1/11 growth gate blocks an ordinary growth attempt for " + block);
                sapling.advanceTree(helper.getLevel(), pos, state, RandomSource.create(acceptedSeed));
                helper.assertValueEqual(helper.getLevel().getBlockState(pos).getValue(SaplingBlock.STAGE), 1,
                        "A successful gate advances one stage for " + block);
            }
        } finally {
            helper.getLevel().setBlock(pos, old, 2);
        }
        helper.succeed();
    }

    private static long seedFor(boolean accepted) {
        for (long seed = 0; seed < 10000; seed++)
            if ((new java.util.Random(seed).nextInt(11) == 0) == accepted) return seed;
        throw new IllegalStateException("Missing deterministic growth seed");
    }
}
