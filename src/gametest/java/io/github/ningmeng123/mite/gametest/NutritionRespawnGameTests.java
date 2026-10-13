package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real restoreFrom paths preserve nutrients while retaining R196 FoodStats lifetime. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class NutritionRespawnGameTests {
    private static final NutritionState STORED = new NutritionState(1, 12_345, 23_456, 34_567, 100_000, 1);

    private NutritionRespawnGameTests() {}

    @GameTest(template = "empty")
    public static void deathCloneKeepsMicronutrientsWithAndWithoutKeepInventory(GameTestHelper helper) {
        GameRules.BooleanValue keep = helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        boolean previousKeep = keep.get();
        try {
            for (boolean keepInventory : new boolean[]{false, true}) {
                keep.set(keepInventory, helper.getLevel().getServer());
                ServerPlayer original = newPlayer(helper);
                PlayerExperience.setTotal(original, 6_650);
                original.getFoodData().setFoodLevel(0);
                dual(original.getFoodData()).mite$setNutrition(1);
                NutritionState.set(original, STORED);
                FoodData previousFood = original.getFoodData();
                ServerPlayer replacement = newPlayer(helper);
                replacement.restoreFrom(original, false);
                helper.assertValueEqual(NutritionState.get(replacement), STORED,
                        "Death cloning preserves all nutrients and resistance with keepInventory=" + keepInventory);
                helper.assertValueEqual(NutritionState.get(original), STORED,
                        "Cloning does not clear the original source nutrient state");
                helper.assertFalse(replacement.getFoodData() == previousFood,
                        "R196 death always constructs new FoodStats, including keepInventory");
                assertEnergy(helper, replacement, 6, 6,
                        "Death food starts at the zero-level constructor capacity instead of copying the depleted reserves");
                helper.assertValueEqual(replacement.experienceLevel, keepInventory ? 35 : 0,
                        "The retained experience changes capacity but does not retroactively refill new FoodStats");
            }
        } finally {
            keep.set(previousKeep, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void keepAllReturnReusesBothReservesAndRebindsTheFoodOwner(GameTestHelper helper) {
        GameRules.BooleanValue keep = helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        boolean previousKeep = keep.get();
        try {
            keep.set(false, helper.getLevel().getServer());
            ServerPlayer original = newPlayer(helper);
            PlayerExperience.setTotal(original, 6_650);
            original.getFoodData().setFoodLevel(2);
            dual(original.getFoodData()).mite$setNutrition(1);
            NutritionState.set(original, STORED);
            FoodData previousFood = original.getFoodData();
            ServerPlayer replacement = newPlayer(helper);
            replacement.restoreFrom(original, true);
            helper.assertValueEqual(NutritionState.get(replacement), STORED,
                    "The keepAll return retains every micronutrient and resistance field");
            helper.assertTrue(replacement.getFoodData() == previousFood,
                    "The keepAll path reuses the previous FoodData object");
            assertEnergy(helper, replacement, 2, 1, "Returning from the End preserves both depleted resources");

            // Deliberately give the old object a different level, then exercise both getter hooks.
            original.experienceLevel = 0;
            original.getFoodData();
            replacement.getFoodData().setFoodLevel(20);
            dual(replacement.getFoodData()).mite$setNutrition(20);
            assertEnergy(helper, replacement, 20, 20,
                    "The reused FoodData setter uses its replacement level-35 owner instead of the original level-zero owner");
            replacement.experienceLevel = 0;
            dual(replacement.getFoodData()).mite$clampToLimit();
            assertEnergy(helper, replacement, 6, 6, "The replacement owner also controls subsequent level loss");
            helper.assertValueEqual(NutritionState.get(replacement), STORED,
                    "Food owner rebinding and capacity clamps do not reset stored micronutrients");
        } finally {
            keep.set(previousKeep, helper.getLevel().getServer());
        }
        helper.succeed();
    }

    private static ServerPlayer newPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "mite-food-clone"), false);
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                cookie.gameProfile(), cookie.clientInformation());
    }

    private static DualFoodData dual(FoodData food) { return (DualFoodData) food; }

    private static void assertEnergy(GameTestHelper helper, ServerPlayer player, int satiation, int nutrition, String context) {
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), satiation, context + " (satiation)");
        helper.assertValueEqual(dual(player.getFoodData()).mite$getNutrition(), nutrition, context + " (nutrition)");
    }
}
