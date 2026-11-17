package io.github.ningmeng123.mite.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native loaded loot retains shears and reduces the original 2/3 stick chance to 40%. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DeadBushDropsGameTests {
    @GameTest(template = "empty")
    public static void deadBushDropsAtMostOneStickWithFortyPercentChance(GameTestHelper helper) {
        var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(Blocks.DEAD_BUSH.getLootTable());
        ItemStack fortune = new ItemStack(Items.IRON_AXE);
        fortune.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FORTUNE), 3);
        for (ItemStack tool : new ItemStack[]{ItemStack.EMPTY, fortune}) {
            var params = params(helper, tool);
            int successes = 0;
            for (int sample = 0; sample < 10000; sample++) {
                var drops = table.getRandomItems(params, 0x4445414442555348L + sample * 1_000_003L);
                helper.assertTrue(drops.size() <= 1 && drops.stream().allMatch(stack -> stack.is(Items.STICK) && stack.getCount() == 1),
                        "Ordinary and Fortune breaks produce either zero sticks or exactly one stick");
                if (!drops.isEmpty()) successes++;
            }
            helper.assertTrue(successes >= 3800 && successes <= 4200,
                    "Fixed-seed native loot samples retain a 40% drop probability, actual successes=" + successes);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shearsStillCollectTheBushInsteadOfSticks(GameTestHelper helper) {
        var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(Blocks.DEAD_BUSH.getLootTable());
        var shears = helper.getLevel().registryAccess().registryOrThrow(Registries.ITEM).getTagOrEmpty(
                TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mite", "shears")));
        for (var item : shears) {
            var params = params(helper, new ItemStack(item));
            for (int seed = 1; seed <= 64; seed++) {
                var drops = table.getRandomItems(params, seed);
                helper.assertTrue(drops.size() == 1 && drops.getFirst().is(Items.DEAD_BUSH) && drops.getFirst().getCount() == 1,
                        "Every registered shears material still collects a single dead bush");
            }
        }
        helper.succeed();
    }

    private static LootParams params(GameTestHelper helper, ItemStack tool) {
        return new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, helper.absolutePos(new BlockPos(2, 2, 2)).getCenter())
                .withParameter(LootContextParams.BLOCK_STATE, Blocks.DEAD_BUSH.defaultBlockState())
                .withParameter(LootContextParams.TOOL, tool)
                .create(LootContextParamSets.BLOCK);
    }
}
