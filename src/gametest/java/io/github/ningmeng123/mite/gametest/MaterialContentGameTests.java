package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.rules.MaterialContentRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source-literal limits and native dropped-item hazards for already registered material leaves. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MaterialContentGameTests {
    private MaterialContentGameTests() {}
    @GameTest(template = "empty")
    public static void AllEighteenMaterialsRetainSourceLimitsAndHazardFlags(GameTestHelper helper) {
        helper.assertValueEqual(MaterialContentRules.profiles().size(), 18, "Exact remaining material scope");
        for (var entry : MaterialContentRules.profiles().entrySet()) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(entry.getKey()));
            helper.assertTrue(!stack.isEmpty(), "Existing source material identity is registered");
            int expected = entry.getKey().getPath().equals("sinew") ? 16 : entry.getKey().getPath().endsWith("_ingot") ? 8 : 64;
            helper.assertValueEqual(stack.getMaxStackSize(), expected, "Actual stack limit keeps metal ingots at eight over source item families");
            boolean leather = entry.getKey().getPath().equals("sinew");
            boolean adamantium = entry.getKey().getPath().startsWith("adamantium_");
            helper.assertTrue(stack.canBeHurtBy(helper.getLevel().damageSources().inFire()) == leather,
                    "Only leather sinew is harmed by ordinary fire among these source materials");
            helper.assertTrue(stack.canBeHurtBy(helper.getLevel().damageSources().lava()) != adamantium,
                    "Only adamantium material is immune to lava; fire and lava are distinct");
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void NativeItemEntityHurtPreservesProtectedMaterialsAndDestroysVulnerableOnes(GameTestHelper helper) {
        for (String id : new String[]{"mite:copper_nugget", "mite:diamond_shard", "mite:adamantium_ingot", "mite:sinew"}) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            ItemEntity fire = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(item));
            fire.hurt(helper.getLevel().damageSources().inFire(), 10F);
            helper.assertTrue(fire.isRemoved() == id.endsWith("sinew"), "Real ordinary-fire destruction follows source susceptibility");
            ItemEntity lava = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(item));
            lava.hurt(helper.getLevel().damageSources().lava(), 10F);
            helper.assertTrue(lava.isRemoved() != id.contains("adamantium"), "Real lava destruction is independent of ordinary fire");
        }
        ItemStack unrelated = new ItemStack(Items.PAPER);
        helper.assertTrue(unrelated.canBeHurtBy(helper.getLevel().damageSources().inFire()), "Unrelated modern item retains native hazards");
        helper.succeed();
    }
}
