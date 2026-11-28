package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.MossyDungeonEyeRecipe;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the loaded crafting recipe across every permitted layout. @author NingMeng123 */
@GameTestHolder("mite_mossy_dungeon_eye")
@PrefixGameTestTemplate(false)
public final class MossyDungeonEyeRecipeGameTests {
    private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("mite", "mossy_dungeon_eye");

    @GameTest(template = "empty")
    public static void allCornerOrdersCraftExactlyOneMossyDungeonEye(GameTestHelper helper) {
        var corners = new ArrayList<>(List.of(Items.ROTTEN_FLESH, Items.GUNPOWDER, Items.STRING, Items.BONE));
        assertPermutationsCraft(helper, corners, 0);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingRequiresTheCrossAndAllFourDifferentDrops(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = (MossyDungeonEyeRecipe) level.getRecipeManager().byKey(RECIPE).orElseThrow().value();
        var corners = List.of(Items.ROTTEN_FLESH, Items.GUNPOWDER, Items.STRING, Items.BONE);
        var duplicate = grid(corners);
        duplicate.set(8, new ItemStack(Items.ROTTEN_FLESH, 2));
        helper.assertFalse(recipe.matches(CraftingInput.of(3, 3, duplicate), level), "A duplicate drop cannot replace a missing one");

        var misplaced = grid(corners);
        Collections.swap(misplaced, 0, 1);
        helper.assertFalse(recipe.matches(CraftingInput.of(3, 3, misplaced), level), "Mithril nuggets must occupy the four edges");
        var offCenter = grid(corners);
        Collections.swap(offCenter, 4, 5);
        helper.assertFalse(recipe.matches(CraftingInput.of(3, 3, offCenter), level), "The ender pearl must occupy the center");

        var incomplete = grid(corners);
        incomplete.set(8, ItemStack.EMPTY);
        helper.assertFalse(recipe.matches(CraftingInput.of(3, 3, incomplete), level), "All nine slots must contain ingredients");
        helper.succeed();
    }

    private static void assertPermutationsCraft(GameTestHelper helper, List<Item> corners, int next) {
        if (next == corners.size()) {
            var level = helper.getLevel();
            var input = CraftingInput.of(3, 3, grid(corners));
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            helper.assertValueEqual(recipe.id(), RECIPE, "Every arrangement of the four drops is a valid recipe");
            var output = recipe.value().assemble(input, level.registryAccess());
            helper.assertTrue(output.is(MiteItems.MOSSY_DUNGEON_EYE.get()) && output.getCount() == 1,
                    "The recipe crafts one eye");
            return;
        }
        for (int corner = next; corner < corners.size(); corner++) {
            Collections.swap(corners, next, corner);
            assertPermutationsCraft(helper, corners, next + 1);
            Collections.swap(corners, next, corner);
        }
    }

    private static List<ItemStack> grid(List<Item> corners) {
        Item mithril = MiteMaterials.MITHRIL_NUGGET.get();
        return new ArrayList<>(List.of(
                new ItemStack(corners.get(0)), new ItemStack(mithril), new ItemStack(corners.get(1)),
                new ItemStack(mithril), new ItemStack(Items.ENDER_PEARL), new ItemStack(mithril),
                new ItemStack(corners.get(2)), new ItemStack(mithril), new ItemStack(corners.get(3))));
    }
}
