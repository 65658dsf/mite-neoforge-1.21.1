package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual server food ticks verify source satiation priority and nutrition-driven starvation. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodConsumptionOrderGameTests {
    private FoodConsumptionOrderGameTests() {}

    @GameTest(template = "empty")
    public static void ordinaryExhaustionSpendsSatiationBeforeNutrition(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 5, 1, 0F);
        player.getFoodData().addExhaustion(8F);
        player.getFoodData().tick(player);
        assertReserves(helper, player, 4, 1, 0F,
                "The first queued deduction spends one satiation and preserves nutrition");
        player.getFoodData().tick(player);
        assertReserves(helper, player, 3, 1, 0F,
                "The second queued deduction spends only one additional satiation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualWheatSeedBuffersBeforeSatiation(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 4, 1, 0F);
        NutritionState.set(player, new NutritionState(1, 20, 20, 20, 0, -1));
        new ItemStack(Items.WHEAT_SEEDS).finishUsingItem(helper.getLevel(), player);
        assertReserves(helper, player, 4, 1, 1F, "The actual seed supplies only its separate native buffer");
        player.getFoodData().addExhaustion(12F);
        player.getFoodData().tick(player);
        assertReserves(helper, player, 4, 1, 0F, "The first deduction spends the seed buffer only");
        player.getFoodData().tick(player);
        assertReserves(helper, player, 3, 1, 0F, "The second deduction spends satiation only");
        player.getFoodData().tick(player);
        assertReserves(helper, player, 2, 1, 0F, "The third deduction continues spending satiation");
        NutritionState state = NutritionState.get(player);
        helper.assertValueEqual(state.protein(), 17, "Three updates consume exactly three protein points");
        helper.assertValueEqual(state.fat(), 17, "Three updates consume exactly three essential-fat points");
        helper.assertValueEqual(state.plants(), 17, "Reserve deductions add no second micronutrient consumption");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFractionalNativeBufferStillPaysForOnlyOneDeduction(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 5, 2, .25F);
        player.getFoodData().addExhaustion(8F);
        player.getFoodData().tick(player);
        assertReserves(helper, player, 5, 2, 0F,
                "The positive fractional buffer is depleted without also touching a food reserve");
        player.getFoodData().tick(player);
        assertReserves(helper, player, 4, 2, 0F,
                "A later deduction consumes the satiation reserve");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void queuedExhaustionKeepsItsConsumptionOrderThroughFullPlayerSaveAndReload(GameTestHelper helper) {
        Player original = player(helper, GameType.SURVIVAL);
        setReserves(original, 5, 2, 2F);
        original.getFoodData().addExhaustion(20F);
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        saved.putFloat("mite_nutrition_hunger", 40F);
        Player restored = player(helper, GameType.SURVIVAL);
        restored.load(saved);
        assertReserves(helper, restored, 5, 2, 2F, "Saving preserves all three distinct resources");
        int[] satiation = {5, 5, 5, 4, 3};
        int[] nutrition = {2, 2, 1, 1, 1};
        float[] nativeBuffer = {1F, 0F, 0F, 0F, 0F};
        for (int tick = 0; tick < 5; tick++) {
            restored.getFoodData().tick(restored);
            assertReserves(helper, restored, satiation[tick], nutrition[tick], nativeBuffer[tick],
                    "The restored queue preserves the buffer, accumulated nutrition hunger and satiation priority on update " + (tick + 1));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedNutritionHungerSelectsExactlyOneReserveAtTheSourceBoundary(GameTestHelper helper) {
        float[] savedHunger = {0F, 3.998F, 3.999F, 4F, 40F};
        for (int index = 0; index < savedHunger.length; index++) {
            Player player = player(helper, GameType.SURVIVAL);
            setReserves(player, 5, 2, 0F);
            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            saved.putFloat("mite_nutrition_hunger", savedHunger[index]);
            saved.putFloat("foodExhaustionLevel", 4F);
            player.getFoodData().readAdditionalSaveData(saved);
            player.getFoodData().tick(player);
            boolean consumesNutrition = index >= 2;
            assertReserves(helper, player, consumesNutrition ? 5 : 4, consumesNutrition ? 1 : 2, 0F,
                    "Saved nutrition hunger " + savedHunger[index] + " selects exactly one source reserve");
            CompoundTag after = player.saveWithoutId(new CompoundTag());
            helper.assertValueEqual(after.getFloat("mite_nutrition_hunger"),
                    consumesNutrition ? 0F : savedHunger[index] + .0005F,
                    "Only a nutrition deduction clears the separate nutrition-hunger accumulator");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nutritionHungerAccumulatesThroughOrdinaryTicksAndPlayerReload(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 5, 2, 0F);
        for (int tick = 0; tick < 100; tick++) player.getFoodData().tick(player);
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        float accumulated = saved.getFloat("mite_nutrition_hunger");
        helper.assertTrue(Math.abs(accumulated - .05F) < .000001F,
                "One hundred normal server food ticks accrue one twentieth nutrition-only hunger");
        Player restored = player(helper, GameType.SURVIVAL);
        restored.load(saved);
        restored.getFoodData().tick(restored);
        CompoundTag after = restored.saveWithoutId(new CompoundTag());
        helper.assertValueEqual(after.getFloat("mite_nutrition_hunger"), accumulated + .0005F,
                "A full player reload resumes nutrition-only hunger from its saved float");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedSavedNutritionHungerKeepsFiniteNonnegativeProgress(GameTestHelper helper) {
        for (float malformed : new float[]{-1F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            Player player = player(helper, GameType.SURVIVAL);
            setReserves(player, 5, 2, 0F);
            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            saved.putFloat("mite_nutrition_hunger", malformed);
            saved.putFloat("foodExhaustionLevel", 4F);
            player.getFoodData().readAdditionalSaveData(saved);
            player.getFoodData().tick(player);
            assertReserves(helper, player, 4, 2, 0F, "Malformed nutrition-only hunger safely resumes satiation priority");
            helper.assertValueEqual(player.saveWithoutId(new CompoundTag()).getFloat("mite_nutrition_hunger"), .0005F,
                    "The loaded nutrition-only accumulator resumes from a finite zero");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyNutritionFallsBackToSatiationWithoutEverGoingNegative(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 1, 0, 0F);
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        saved.putFloat("mite_nutrition_hunger", 4F);
        saved.putFloat("foodExhaustionLevel", 8F);
        player.getFoodData().readAdditionalSaveData(saved);
        player.getFoodData().tick(player);
        assertReserves(helper, player, 0, 0, 0F, "Due nutrition hunger falls back to the final satiation unit when nutrition is empty");
        player.getFoodData().tick(player);
        assertReserves(helper, player, 0, 0, 0F, "Queued exhaustion leaves both empty reserves at zero");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyNutritionStartsStarvationDespiteRetainedSatiationAndSeedBuffer(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 5, 0, 2F);
        player.setHealth(4F);
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        GameRules.BooleanValue natural = helper.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
        boolean previousNatural = natural.get();
        try {
            server.getWorldData().setDifficulty(Difficulty.NORMAL);
            natural.set(true, server);
            for (int tick = 0; tick < 501; tick++) player.getFoodData().tick(player);
            helper.assertValueEqual(player.getHealth(), 3F,
                    "Zero nutrition causes one source starvation damage after five-hundred and one updates");
            assertReserves(helper, player, 5, 0, 2F, "Starvation begins with satiation and the native seed buffer still available");
            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            helper.assertTrue(saved.getFloat("mite_starve_progress") > 0F,
                    "The source starvation interval retains its fractional remainder");
            helper.assertValueEqual(saved.getFloat("mite_heal_progress"), 0F,
                    "Absent nutrition cannot accumulate slow healing progress from satiation alone");
        } finally {
            natural.set(previousNatural, server);
            server.getWorldData().setDifficulty(previousDifficulty);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eatingNutritionClearsStarvationProgressAndRestartsSlowHealing(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 5, 0, 0F);
        player.setHealth(4F);
        for (int tick = 0; tick < 100; tick++) player.getFoodData().tick(player);
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        helper.assertTrue(saved.getFloat("mite_starve_progress") > 0F, "Empty nutrition accumulates starvation progress");
        new ItemStack(Items.BREAD).finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(((DualFoodData) player.getFoodData()).mite$getNutrition() > 0, "Real bread consumption restores nutrition");
        MinecraftServer server = helper.getLevel().getServer();
        GameRules.BooleanValue natural = helper.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
        boolean previousNatural = natural.get();
        try {
            natural.set(true, server);
            player.getFoodData().tick(player);
            CompoundTag recovered = player.saveWithoutId(new CompoundTag());
            helper.assertValueEqual(recovered.getFloat("mite_starve_progress"), 0F, "Restored nutrition clears starvation progress immediately");
            helper.assertTrue(recovered.getFloat("mite_heal_progress") > 0F, "Restored nutrition starts source slow healing");
            helper.assertValueEqual(player.getHealth(), 4F, "A single food tick accumulates recovery without an instant heal");
        } finally {
            natural.set(previousNatural, server);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothEmptyReservesRetainTheSourceStarvationBoundaryAndDifficultyLimits(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        Difficulty[] difficulties = {Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
        float[] initialHealth = {14F, 6F, 1F};
        float[][] expectedHealth = {{13F, 6F, 1F}, {13F, 6F, 1F}, {13F, 5F, 1F}, {13F, 5F, 0F}};
        try {
            for (int difficulty = 0; difficulty < difficulties.length; difficulty++) {
                // Direct data is an isolated fixture for inactive source branches, not a production difficulty change.
                server.getWorldData().setDifficulty(difficulties[difficulty]);
                for (int boundary = 0; boundary < initialHealth.length; boundary++) {
                    Player player = player(helper, GameType.SURVIVAL);
                    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(14D);
                    player.setHealth(initialHealth[boundary]);
                    setReserves(player, 0, 0, 0F);
                    for (int tick = 0; tick < 500; tick++) player.getFoodData().tick(player);
                    helper.assertValueEqual(player.getHealth(), initialHealth[boundary],
                            "Five-hundred source float additions remain below the starvation damage boundary");
                    player.getFoodData().tick(player);
                    helper.assertValueEqual(player.getHealth(), expectedHealth[difficulty][boundary],
                            "Both empty reserves retain the source starvation health floor in " + difficulties[difficulty]);
                }
            }
        } finally {
            server.getWorldData().setDifficulty(previousDifficulty);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nutritionAloneRemainsUsableFoodEnergy(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 0, 2, 0F);
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        try {
            server.getWorldData().setDifficulty(Difficulty.HARD);
            for (int tick = 0; tick < 501; tick++) player.getFoodData().tick(player);
            helper.assertValueEqual(player.getHealth(), 6F, "An empty satiation bar cannot starve a player with nutrition");
            assertReserves(helper, player, 0, 2, 0F, "Nutrition remains independently available without satiation");
        } finally {
            server.getWorldData().setDifficulty(previousDifficulty);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeAndSpectatorActivityCannotNaturallyDrainAnyFoodResource(GameTestHelper helper) {
        NutritionState initial = new NutritionState(1, 123, 456, 789, 2, 0);
        for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
            Player player = player(helper, mode);
            setReserves(player, 5, 2, 2F);
            NutritionState.set(player, initial);
            for (int tick = 0; tick < 2_100; tick++) {
                player.getFoodData().addExhaustion(8F);
                player.getFoodData().tick(player);
            }
            assertReserves(helper, player, 5, 2, 2F, mode + " activity preserves every food-energy reserve");
            helper.assertValueEqual(NutritionState.get(player), initial,
                    mode + " also preserves micronutrients and sugar-resistance state");
            helper.assertValueEqual(player.saveWithoutId(new CompoundTag()).getFloat("mite_nutrition_hunger"), 0F,
                    mode + " does not accumulate nutrition-only hunger");
        }
        helper.succeed();
    }

    private static Player player(GameTestHelper helper, GameType mode) {
        Player player = helper.makeMockPlayer(mode);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(6D);
        player.setHealth(6F);
        return player;
    }

    private static void setReserves(Player player, int satiation, int nutrition, float nativeBuffer) {
        FoodData food = player.getFoodData();
        food.setFoodLevel(satiation);
        ((DualFoodData) food).mite$setNutrition(nutrition);
        food.setSaturation(nativeBuffer);
        food.setExhaustion(0F);
    }

    private static void assertReserves(GameTestHelper helper, Player player, int satiation, int nutrition,
                                       float nativeBuffer, String context) {
        FoodData food = player.getFoodData();
        helper.assertValueEqual(food.getFoodLevel(), satiation, context + " (satiation)");
        helper.assertValueEqual(((DualFoodData) food).mite$getNutrition(), nutrition, context + " (nutrition)");
        helper.assertValueEqual(food.getSaturationLevel(), nativeBuffer, context + " (native seed buffer)");
    }
}
