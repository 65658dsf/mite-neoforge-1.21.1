package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.MaterialUpgradeRequirements;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import io.github.ningmeng123.mite.integration.jei.SmithingJeiRecipes;
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
    public static void materialUpgradePagesShowTheFullNativeCostAndTimedBaseline(GameTestHelper helper) {
        var index = SmithingJeiRecipes.index(helper.getLevel());
        helper.assertTrue(!index.recipes().isEmpty(), "Loaded native smithing upgrades produce display pages");
        helper.assertValueEqual(index.originals().size(), index.recipes().size(), "Only replaced native pages are hidden");
        boolean helmet = false;
        for (var page : index.recipes()) {
            int required = MaterialUpgradeRequirements.ingots(page.output());
            helper.assertTrue(page.additions().stream().allMatch(stack -> stack.getCount() == required),
                    "Every displayed alternative requires the full target-material cost");
            helper.assertValueEqual(page.baseTicks(), TimedCraftingRules.period(
                    MaterialUpgradeRequirements.difficulty(page.output()), 0, 0F, false),
                    "Displayed timing uses the server's zero-level neutral baseline");
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
                helmet = true;
            }
            helper.assertTrue(MaterialUpgradeRequirements.usesTemplate(template), "The replacement category never hides other templates");
            helper.assertTrue(!page.output().isEmpty() && page.output().getCount() == 1, "Each timed upgrade produces one item");
        }
        helper.assertTrue(helmet, "The full-cost mithril helmet example is part of the loaded catalog");
        helper.succeed();
    }
}
