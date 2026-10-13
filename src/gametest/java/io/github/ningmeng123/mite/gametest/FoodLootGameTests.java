package io.github.ningmeng123.mite.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real loot table resolves the untouched-cake distinction. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodLootGameTests {
    @GameTest(template="empty")
    public static void untouchedCakeDropsOnceButEatenCakeCannotBeRecovered(GameTestHelper helper) {
        for(int bites=0;bites<=6;bites++) {
            var state=Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES,bites);
            var drops=state.getDrops(new LootParams.Builder(helper.getLevel())
                    .withParameter(LootContextParams.ORIGIN,helper.absolutePos(new net.minecraft.core.BlockPos(1,1,1)).getCenter())
                    .withParameter(LootContextParams.TOOL,new ItemStack(Items.IRON_AXE)));
            if(bites==0) {
                helper.assertValueEqual(drops.size(),1,"Untouched cake has exactly one loot stack");
                helper.assertTrue(drops.getFirst().is(Items.CAKE),"Untouched cake returns its original native item");
                helper.assertValueEqual(drops.getFirst().getCount(),1,"Only one whole cake is returned");
            } else helper.assertTrue(drops.isEmpty(),"Any partly eaten cake produces no cake item");
        }
        helper.succeed();
    }
}
