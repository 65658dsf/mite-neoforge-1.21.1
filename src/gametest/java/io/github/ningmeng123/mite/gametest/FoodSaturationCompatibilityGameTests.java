package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Displaying source nutrition must never produce a second food-energy buffer. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodSaturationCompatibilityGameTests {
    private FoodSaturationCompatibilityGameTests() {}

    @GameTest(template = "empty")
    public static void breadAndBakedPotatoConsumeSatiationAndNutritionTogether(GameTestHelper helper) {
        for (Item item : new Item[]{Items.BREAD, Items.BAKED_POTATO}) {
            Player player = player(helper);
            ItemStack serving = new ItemStack(item, 2);
            int saturation = item == Items.BREAD ? 8 : 6;
            helper.assertValueEqual(serving.get(DataComponents.FOOD).nutrition(), 2,
                    "Food preview restores two hunger points: " + item);
            helper.assertValueEqual(serving.get(DataComponents.FOOD).saturation(), (float) saturation,
                    "Food preview carries the requested saturation-layer value: " + item);
            ItemStack remaining = serving.finishUsingItem(helper.getLevel(), player);
            helper.assertValueEqual(remaining.getCount(), 1, "The actual player consumes only one serving: " + item);
            assertReserves(helper, player, 2, saturation);
            for (int spent = 1; spent <= saturation; spent++) {
                player.getFoodData().addExhaustion(4F);
                player.getFoodData().tick(player);
                assertReserves(helper, player, Math.max(0, 2 - spent), saturation - spent);
            }
            helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 0F,
                    "The food preview leaves the separate seed buffer at zero: " + item);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allMilkContainersKeepNutritionFourWhenSatiationIsZero(GameTestHelper helper) {
        Item[] milk = {Items.MILK_BUCKET, MiteFoods.get("copper_milk_bucket"), MiteFoods.get("silver_milk_bucket"),
                MiteFoods.get("gold_milk_bucket"), MiteFoods.get("mithril_milk_bucket"),
                MiteFoods.get("adamantium_milk_bucket"), MiteFoods.get("ancient_metal_milk_bucket")};
        Item[] empty = {Items.BUCKET, MiteFoods.get("copper_bucket"), MiteFoods.get("silver_bucket"),
                MiteFoods.get("gold_bucket"), MiteFoods.get("mithril_bucket"),
                MiteFoods.get("adamantium_bucket"), MiteFoods.get("ancient_metal_bucket")};
        for (int index = 0; index < milk.length; index++) {
            Player player = player(helper);
            ItemStack serving = new ItemStack(milk[index]);
            helper.assertValueEqual(serving.get(DataComponents.FOOD).saturation(), 4F,
                    "Every source milk bucket previews absolute nutrition four");
            ItemStack returned = serving.finishUsingItem(helper.getLevel(), player);
            assertReserves(helper, player, 0, 4);
            helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 0F,
                    "Nutrition four survives independently of satiation zero without a third reserve");
            helper.assertTrue(returned.is(empty[index]) && returned.getCount() == 1,
                    "Each accepted milk serving returns exactly its own empty vessel");
            player.getFoodData().addExhaustion(4F);
            player.getFoodData().tick(player);
            assertReserves(helper, player, 0, 3);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wheatSeedExceptionAddsOnlyOneBufferWhileLaterCheeseNutritionIsAppliedOnce(GameTestHelper helper) {
        Player player = player(helper);
        player.getFoodData().setFoodLevel(4);
        ((DualFoodData) player.getFoodData()).mite$setNutrition(3);
        NutritionState.set(player, new NutritionState(1, 0, 0, 0, 0, -1));
        new ItemStack(Items.WHEAT_SEEDS).finishUsingItem(helper.getLevel(), player);
        assertReserves(helper, player, 4, 3);
        helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 1F,
                "The previously requested seed exception supplies one separate buffer, not a copy of nutrition");
        helper.assertValueEqual(NutritionState.get(player).protein(), 0,
                "The seed still supplies no protein or other source micronutrients");
        new ItemStack(MiteFoods.CHEESE.get()).finishUsingItem(helper.getLevel(), player);
        assertReserves(helper, player, 7, 6);
        helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 1F,
                "Cheese's display value three is not added to the existing seed buffer");
        helper.assertValueEqual(NutritionState.get(player).protein(), 24000,
                "Cheese nutrition is applied exactly once to protein as well");
        helper.succeed();
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.experienceLevel = 35;
        player.getFoodData().setFoodLevel(0);
        ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
        player.getFoodData().setSaturation(0F);
        player.getInventory().clearContent();
        return player;
    }

    private static void assertReserves(GameTestHelper helper, Player player, int satiation, int nutrition) {
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), satiation, "Source satiation");
        helper.assertValueEqual(((DualFoodData) player.getFoodData()).mite$getNutrition(), nutrition,
                "Source nutrition, independent of satiation and display metadata");
    }
}
