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

/** Actual server food ticks verify the requested saturation-before-satiation order. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodConsumptionOrderGameTests {
    private FoodConsumptionOrderGameTests() {}

    @GameTest(template = "empty")
    public static void spendingTheFinalNutritionUnitDoesNotAlsoSpendSatiation(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 5, 1, 0F);
        player.getFoodData().addExhaustion(8F);
        player.getFoodData().tick(player);
        assertReserves(helper, player, 5, 0, 0F,
                "The final nutrition unit alone pays for the first queued deduction");
        player.getFoodData().tick(player);
        assertReserves(helper, player, 4, 0, 0F,
                "The second queued deduction can spend satiation after nutrition is empty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualWheatSeedBuffersBeforeNutritionAndThenSatiation(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 4, 1, 0F);
        NutritionState.set(player, new NutritionState(1, 20, 20, 20, 0, -1));
        new ItemStack(Items.WHEAT_SEEDS).finishUsingItem(helper.getLevel(), player);
        assertReserves(helper, player, 4, 1, 1F, "The actual seed supplies only its separate native buffer");
        player.getFoodData().addExhaustion(12F);
        player.getFoodData().tick(player);
        assertReserves(helper, player, 4, 1, 0F, "The first deduction spends the seed buffer only");
        player.getFoodData().tick(player);
        assertReserves(helper, player, 4, 0, 0F, "The second deduction spends nutrition only");
        player.getFoodData().tick(player);
        assertReserves(helper, player, 3, 0, 0F, "The third deduction finally spends satiation");
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
        assertReserves(helper, player, 5, 1, 0F,
                "A later deduction consumes nutrition rather than the satiation bar");
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
        int[] satiation = {5, 5, 5, 5, 4};
        int[] nutrition = {2, 2, 1, 0, 0};
        float[] nativeBuffer = {1F, 0F, 0F, 0F, 0F};
        for (int tick = 0; tick < 5; tick++) {
            restored.getFoodData().tick(restored);
            assertReserves(helper, restored, satiation[tick], nutrition[tick], nativeBuffer[tick],
                    "The restored queue spends native buffer, nutrition, then satiation on update " + (tick + 1));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void historicalNutritionHungerCannotChangeTheCurrentConsumptionPriority(GameTestHelper helper) {
        for (float historicalHunger : new float[]{0F, 3.999F, 4F, 40F}) {
            Player player = player(helper, GameType.SURVIVAL);
            setReserves(player, 5, 2, 0F);
            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            saved.putFloat("mite_nutrition_hunger", historicalHunger);
            saved.putFloat("foodExhaustionLevel", 4F);
            player.getFoodData().readAdditionalSaveData(saved);
            player.getFoodData().tick(player);
            assertReserves(helper, player, 5, 1, 0F,
                    "The legacy hunger value " + historicalHunger + " neither bypasses nutrition nor spends two units");
            CompoundTag after = player.saveWithoutId(new CompoundTag());
            helper.assertValueEqual(after.getFloat("mite_nutrition_hunger"), 0F,
                    "Spending nutrition clears historical nutrition hunger without another deduction");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void satiationAlonePreventsStarvationButCannotProvideNutritionHealing(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        setReserves(player, 5, 0, 0F);
        player.setHealth(4F);
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        GameRules.BooleanValue natural = helper.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
        boolean previousNatural = natural.get();
        try {
            server.getWorldData().setDifficulty(Difficulty.NORMAL);
            natural.set(true, server);
            for (int tick = 0; tick < 1_000; tick++) player.getFoodData().tick(player);
            helper.assertValueEqual(player.getHealth(), 4F,
                    "Retained satiation causes neither starvation nor nutrition-driven regeneration over one-thousand updates");
            assertReserves(helper, player, 5, 0, 0F, "Natural exhaustion below four has not reached the satiation reserve");
            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            helper.assertValueEqual(saved.getFloat("mite_starve_progress"), 0F,
                    "Starvation does not accumulate while satiation still supplies energy");
            helper.assertValueEqual(saved.getFloat("mite_heal_progress"), 0F,
                    "Absent nutrition cannot accumulate slow healing progress from satiation alone");
        } finally {
            natural.set(previousNatural, server);
            server.getWorldData().setDifficulty(previousDifficulty);
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
