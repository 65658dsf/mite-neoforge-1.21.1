package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.survival.LeveledFoodData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.Foods;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Exercises transformed Minecraft food logic inside the GameTest server.
 *
 * @author NingMeng123
 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodCapacityGameTests {
    @GameTest(template = "empty")
    public static void eatingFollowsCapacityWithoutRefillingOnLevelUp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        FoodData food = player.getFoodData();
        player.experienceLevel = 0;
        food.setFoodLevel(6);
        food.setSaturation(4.0F);

        helper.assertFalse(food.needsFood(), "Level 0 food 6 must count as full");
        helper.assertFalse(player.canEat(false), "Ordinary food must be refused at the level 0 cap");
        helper.assertTrue(player.canEat(true), "Always-edible food must retain its exception");

        player.experienceLevel = 5;
        food.tick(player);
        assertFood(helper, food, 6, 4.0F, "Level-up must not refill food or saturation");
        helper.assertTrue(food.needsFood(), "Level 5 food 6 must be below the new cap 8");
        helper.assertTrue(player.canEat(false), "Ordinary food must be allowed below cap 8");

        food.eat(Foods.COOKED_BEEF);
        assertFood(helper, food, 8, 8.0F, "FoodProperties eating must cap both food and saturation");
        helper.assertFalse(food.needsFood(), "Level 5 food 8 must count as full");
        helper.assertFalse(player.canEat(false), "Ordinary food must be refused at cap 8");

        food.setFoodLevel(1);
        food.setSaturation(0.0F);
        food.eat(20, 1.0F);
        assertFood(helper, food, 8, 8.0F, "Integer eating used by cake/effects must share the cap");

        player.experienceLevel = 0;
        food.tick(player);
        assertFood(helper, food, 6, 6.0F, "Level-down must clamp existing food before metabolism");
        helper.assertFalse(food.needsFood(), "Clamped level 0 food must still count as full");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loadingAndRebindingPreserveTheRightOwner(GameTestHelper helper) {
        Player loadedPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        FoodData loadedFood = loadedPlayer.getFoodData();
        CompoundTag savedFood = new CompoundTag();
        savedFood.putInt("foodLevel", 20);
        savedFood.putInt("foodTickTimer", 0);
        savedFood.putFloat("foodSaturationLevel", 20.0F);
        savedFood.putFloat("foodExhaustionLevel", 0.0F);

        // Food can be restored while the player still has its initial level zero.
        loadedFood.readAdditionalSaveData(savedFood);
        helper.assertTrue(loadedPlayer.getFoodData() == loadedFood, "The getter must preserve the loaded instance");
        assertFood(helper, loadedFood, 20, 20.0F, "Binding during load must not prematurely clamp to level zero");
        loadedPlayer.experienceLevel = 35;
        loadedFood.tick(loadedPlayer);
        assertFood(helper, loadedFood, 20, 20.0F, "The first tick must use the restored experience level");
        helper.assertFalse(loadedFood.needsFood(), "Restored level 35 food 20 must count as full");

        Player replacement = helper.makeMockPlayer(GameType.SURVIVAL);
        replacement.experienceLevel = 0;
        LeveledFoodData reused = bridge(helper, loadedFood);
        reused.mite$bindPlayer(replacement);
        reused.mite$clampToLimit();
        assertFood(helper, loadedFood, 6, 6.0F, "A reused FoodData must adopt its replacement owner");
        replacement.experienceLevel = 5;
        helper.assertTrue(loadedFood.needsFood(), "A rebound owner must supply subsequent level changes");
        loadedFood.setFoodLevel(20);
        assertFood(helper, loadedFood, 8, 6.0F, "The old level 35 owner must no longer control the setter");

        // Exercise the production getFoodData RETURN hook as well as the bridge.
        loadedPlayer.getFoodData().setFoodLevel(20);
        helper.assertValueEqual(loadedFood.getFoodLevel(), 20, "Getter rebinding to the original level 35 owner");
        loadedFood.tick(replacement);
        assertFood(helper, loadedFood, 8, 6.0F, "tick(player) must refresh ownership independently of the getter");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void settersAndPeacefulRegenerationRespectCapacity(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        FoodData food = player.getFoodData();
        player.experienceLevel = 0;
        food.setFoodLevel(20);
        food.setSaturation(20.0F);
        assertFood(helper, food, 6, 6.0F, "Direct setters must enforce the level 0 cap");
        food.setFoodLevel(3);
        assertFood(helper, food, 3, 3.0F, "Lowering food must also clamp saturation to food");
        food.setFoodLevel(-1);
        food.setSaturation(-1.0F);
        assertFood(helper, food, 0, 0.0F, "Server setters must reject negative food and saturation");

        MinecraftServer server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        GameRules.BooleanValue naturalRegeneration = helper.getLevel().getGameRules()
                .getRule(GameRules.RULE_NATURAL_REGENERATION);
        boolean previousRegeneration = naturalRegeneration.get();
        try {
            server.setDifficulty(Difficulty.PEACEFUL, true);
            naturalRegeneration.set(true, server);
            helper.assertValueEqual(helper.getLevel().getDifficulty(), Difficulty.PEACEFUL, "Peaceful test precondition");
            player.tickCount = 20;
            food.setFoodLevel(6);
            food.setSaturation(6.0F);
            player.aiStep();
            assertFood(helper, food, 6, 6.0F, "The actual peaceful saturation increment must not exceed capacity");

            food.setFoodLevel(5);
            food.setSaturation(0.0F);
            player.aiStep();
            assertFood(helper, food, 6, 1.0F, "Peaceful food recovery must stop at the custom cap");
        } finally {
            naturalRegeneration.set(previousRegeneration, server);
            server.setDifficulty(previousDifficulty, true);
        }
        helper.succeed();
    }

    private static LeveledFoodData bridge(GameTestHelper helper, FoodData food) {
        helper.assertTrue(food instanceof LeveledFoodData, "FoodData mixin must be loaded in GameTest");
        return (LeveledFoodData) food;
    }

    private static void assertFood(GameTestHelper helper, FoodData food, int expectedFood,
                                   float expectedSaturation, String context) {
        helper.assertValueEqual(food.getFoodLevel(), expectedFood, context + " (food)");
        helper.assertValueEqual(food.getSaturationLevel(), expectedSaturation, context + " (saturation)");
    }
}
