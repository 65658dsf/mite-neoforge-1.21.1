package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.MaterialUpgradeRequirements;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import io.github.ningmeng123.mite.integration.jei.SmithingJeiRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mite_upgrade")
@PrefixGameTestTemplate(false)
public final class SmithingJeiGameTests {
    private SmithingJeiGameTests() {}

    @GameTest(template = "empty")
    public static void materialUpgradePagesShowTheFullNativeCostAndCurrentTiming(GameTestHelper helper) {
        var index = SmithingJeiRecipes.index(helper.getLevel());
        helper.assertTrue(!index.recipes().isEmpty(), "Loaded native smithing upgrades produce display pages");
        helper.assertValueEqual(index.originals().size(), index.recipes().size(), "Only replaced native pages are hidden");
        boolean helmet = false;
        int ancientMetalPages = 0;
        for (var page : index.recipes()) {
            int required = MaterialUpgradeRequirements.ingots(page.output());
            helper.assertTrue(page.additions().stream().allMatch(stack -> stack.getCount() == required),
                    "Every displayed alternative requires the full target-material cost");
            float difficulty = MaterialUpgradeRequirements.difficulty(page.output());
            var table = page.requiredTable();
            helper.assertValueEqual(page.durationTicks(0, false), table.duration(TimedCraftingRules.period(difficulty, 0, 0F, false)),
                    "Displayed timing matches the server at level zero and default quality");
            var template = page.templates().getFirst().copy();
            var base = page.bases().getFirst().copy();
            var addition = page.additions().getFirst().copy();
            helper.assertTrue(page.source().value().matches(new SmithingRecipeInput(template, base, addition), helper.getLevel()),
                    "The full-cost JEI example matches its real native recipe");
            if (required > 1) {
                addition.shrink(1);
                helper.assertFalse(page.source().value().matches(new SmithingRecipeInput(template, base, addition), helper.getLevel()),
                        "A displayed recipe cannot accept one less ingot than its declared cost");
            }
            if (page.source().id().equals(ResourceLocation.parse("mite:equipment_upgrade/ancient_metal_helmet_to_mithril"))) {
                helper.assertValueEqual(required, 5, "The mithril helmet page shows five mithril ingots");
                helper.assertValueEqual(BuiltInRegistries.BLOCK.getKey(table.block()), ResourceLocation.parse("mite:mithril_smithing_table"),
                        "The mithril helmet page requires the mithril smithing table");
                helper.assertValueEqual(table.timeReductionDivisor(), 6, "Mithril smithing reduces time by one sixth");
                helper.assertValueEqual(page.durationTicks(20, false), table.duration(TimedCraftingRules.period(difficulty, 20, 0F, false)),
                        "The same page applies the current positive level to its displayed duration");
                helper.assertValueEqual(page.durationTicks(-20, false), table.duration(TimedCraftingRules.period(difficulty, -20, 0F, false)),
                        "The same page applies the current negative level to its displayed duration");
                helper.assertValueEqual(page.durationTicks(20, true), table.duration(TimedCraftingRules.period(difficulty, 20, 0F, true)),
                        "The same page applies the player's clumsiness to its displayed duration");
                helmet = true;
            }
            if (page.source().id().getPath().endsWith("_to_ancient_metal")) {
                helper.assertTrue(page.additions().stream().allMatch(stack -> stack.getCount() == 2),
                        "Every ancient metal upgrade page independently displays exactly two ingots");
                ancientMetalPages++;
            }
            helper.assertTrue(MaterialUpgradeRequirements.usesTemplate(template), "The replacement category never hides other templates");
            helper.assertTrue(!page.output().isEmpty() && page.output().getCount() == 1, "Each timed upgrade produces one item");
        }
        helper.assertTrue(helmet, "The full-cost mithril helmet example is part of the loaded catalog");
        helper.assertTrue(ancientMetalPages >= 20, "The loaded catalog displays the fixed cost for all ancient metal equipment families");
        helper.succeed();
    }
}
