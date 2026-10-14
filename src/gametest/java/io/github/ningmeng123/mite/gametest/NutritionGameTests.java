package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import io.github.ningmeng123.mite.survival.NutritionState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual consumption and FoodData ticks with independent R196 expectations. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class NutritionGameTests {
    private NutritionGameTests() {}

    @GameTest(template = "empty")
    public static void foodAndNutritionStartAtSixAndShareCapacityWithoutRefilling(GameTestHelper helper) {
        Player player = player(helper);
        FoodData food = player.getFoodData();
        DualFoodData dual = dual(food);
        assertEnergy(helper, player, 6, 6, "New level-zero player");
        helper.assertValueEqual(NutritionState.get(player), NutritionState.INITIAL, "New micronutrients start full");
        player.experienceLevel = 5;
        food.tick(player);
        assertEnergy(helper, player, 6, 6, "Level five raises capacity without refilling either resource");
        food.setFoodLevel(8);
        dual.mite$setNutrition(7);
        player.experienceLevel = 0;
        food.tick(player);
        assertEnergy(helper, player, 6, 6, "Level loss clamps both resources to six");
        food.setFoodLevel(0);
        helper.assertValueEqual(dual.mite$getNutrition(), 6, "Emptying satiation preserves nutrition");
        helper.assertTrue(FoodConsumption.hasEnergy(player), "Nutrition alone still supplies usable food energy");
        dual.mite$setNutrition(0);
        helper.assertFalse(FoodConsumption.hasEnergy(player), "Only both empty resources remove food energy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realBreadAndBeefConsumptionHaveDistinctR196Values(GameTestHelper helper) {
        Player player = player(helper);
        player.experienceLevel = 35;
        setEnergy(player, 0, 0);
        ItemStack bread = new ItemStack(Items.BREAD, 2);
        ItemStack remaining = bread.finishUsingItem(helper.getLevel(), player);
        helper.assertValueEqual(remaining.getCount(), 1, "Completing bread consumes exactly one item");
        assertEnergy(helper, player, 8, 2, "Bread contributes eight satiation and two nutrition");
        helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 0F, "Native saturation is not used as R196 nutrition");
        ItemStack beef = new ItemStack(Items.COOKED_BEEF, 2);
        remaining = beef.finishUsingItem(helper.getLevel(), player);
        helper.assertValueEqual(remaining.getCount(), 1, "Completing beef consumes exactly one item");
        assertEnergy(helper, player, 18, 12, "Beef contributes ten to each independent resource");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void seedsAndMeatRestoreOnlyTheirSpecifiedMicronutrients(GameTestHelper helper) {
        Player player = player(helper);
        player.experienceLevel = 35;
        setEnergy(player, 4, 3);
        player.getFoodData().setSaturation(0F);
        NutritionState.set(player, new NutritionState(1, 0, 0, 0, 0, -1));
        new ItemStack(Items.WHEAT_SEEDS).finishUsingItem(helper.getLevel(), player);
        assertEnergy(helper, player, 4, 3, "The requested wheat-seed adaptation supplies neither satiation nor nutrition");
        helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 1F,
                "One wheat seed adds exactly one native saturation point");
        NutritionState state = NutritionState.get(player);
        helper.assertValueEqual(state.protein(), 0, "Seeds supply no protein");
        helper.assertValueEqual(state.fat(), 0, "The requested seed adaptation supplies only native saturation and no essential fats");
        helper.assertValueEqual(state.plants(), 0, "Seeds supply no plant nutrients");
        new ItemStack(Items.COOKED_BEEF).finishUsingItem(helper.getLevel(), player);
        assertEnergy(helper, player, 14, 13, "Cooked beef still supplies its source ten satiation and ten nutrition");
        helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 1F,
                "Eating another food preserves the existing native saturation buffer");
        state = NutritionState.get(player);
        helper.assertValueEqual(state.protein(), 80_000, "Ten meat nutrition supplies eighty-thousand protein");
        helper.assertValueEqual(state.fat(), 0, "Cooked beef supplies no essential fats");
        helper.assertValueEqual(state.plants(), 0, "Cooked beef supplies no plant nutrients");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void seedSaturationAccumulatesOnlyToExistingSatiationAndCreatesNoFoodEnergy(GameTestHelper helper) {
        Player player = player(helper);
        setEnergy(player, 3, 2);
        player.getFoodData().setSaturation(0F);
        NutritionState unchanged = new NutritionState(1, 0, 0, 0, 0, -1);
        NutritionState.set(player, unchanged);
        ItemStack seeds = new ItemStack(Items.WHEAT_SEEDS, 5);
        float[] expectedSaturation = {1F, 2F, 3F, 3F, 3F};
        for (int eaten = 1; eaten <= 5; eaten++) {
            seeds = seeds.finishUsingItem(helper.getLevel(), player);
            assertEnergy(helper, player, 3, 2, "Seed consumption never increases either food-energy reserve");
            helper.assertValueEqual(player.getFoodData().getSaturationLevel(), expectedSaturation[eaten - 1],
                    "Each seed adds one saturation until the current satiation value three is reached");
            helper.assertValueEqual(seeds.getCount(), 5 - eaten, "Each successful seed completion consumes exactly one seed");
        }
        setEnergy(player, 0, 0);
        new ItemStack(Items.WHEAT_SEEDS).finishUsingItem(helper.getLevel(), player);
        assertEnergy(helper, player, 0, 0, "Seeds do not manufacture food energy when both reserves are empty");
        helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 0F,
                "Zero satiation also caps native seed saturation at zero");
        helper.assertFalse(FoodConsumption.hasEnergy(player), "Eating seeds with empty reserves does not restore food-energy eligibility");
        helper.assertValueEqual(NutritionState.get(player), unchanged,
                "Repeated seed consumption and the empty-reserve case restore no micronutrients or sugar resistance");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneSeedSaturationBuffersOneExhaustionDeductionBeforeNutritionResumes(GameTestHelper helper) {
        Player player = player(helper);
        setEnergy(player, 4, 3);
        player.getFoodData().setSaturation(0F);
        new ItemStack(Items.WHEAT_SEEDS).finishUsingItem(helper.getLevel(), player);
        player.getFoodData().addExhaustion(8F);
        player.getFoodData().tick(player);
        helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 0F,
                "The first queued hunger deduction consumes the one-point saturation buffer");
        assertEnergy(helper, player, 4, 3, "Consuming saturation protects both independent food reserves for that deduction");
        player.getFoodData().tick(player);
        assertEnergy(helper, player, 4, 2, "Once seed saturation is depleted the next deduction consumes nutrition before satiation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void highNativeSaturationDoesNotActivateModernFastRegeneration(GameTestHelper helper) {
        Player player = player(helper);
        setEnergy(player, 6, 6);
        player.getFoodData().setSaturation(6F);
        player.setHealth(4F);
        MinecraftServer server = helper.getLevel().getServer();
        GameRules.BooleanValue natural = helper.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
        boolean previous = natural.get();
        try {
            natural.set(true, server);
            for (int tick = 0; tick < 80; tick++) player.getFoodData().tick(player);
            helper.assertValueEqual(player.getHealth(), 4F,
                    "High native saturation does not bypass the source slow nutrition-driven healing progress");
            helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 6F,
                    "The retained saturation buffer is not spent by modern fast regeneration");
            assertEnergy(helper, player, 6, 6, "The short regeneration probe changes neither food reserve");
        } finally {
            natural.set(previous, server);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sugarResistanceTriggersNauseaAndKeepsTheRecoveryHysteresis(GameTestHelper helper) {
        Player player = player(helper);
        setEnergy(player, 0, 0);
        NutritionState.set(player, new NutritionState(1, 160_000, 160_000, 160_000, 47_999, -1));
        new ItemStack(Items.SUGAR).finishUsingItem(helper.getLevel(), player);
        helper.assertValueEqual(NutritionState.get(player).resistance(), 52_799, "One thousand sugar adds four-thousand eight-hundred resistance");
        helper.assertValueEqual(NutritionState.get(player).resistanceLevel(), 0, "Crossing forty-eight-thousand enters mild resistance");
        MobEffectInstance nausea = player.getEffect(MobEffects.CONFUSION);
        helper.assertTrue(nausea != null, "Positive sugar response with resistance causes nausea");
        helper.assertValueEqual(nausea.getDuration(), 400, "R196 sugar nausea lasts four-hundred ticks");
        helper.assertValueEqual(nausea.getAmplifier(), 0, "Mild resistance uses nausea amplifier zero");

        NutritionState.set(player, new NutritionState(1, 160_000, 160_000, 160_000, 144_000, 2));
        player.getFoodData().tick(player);
        helper.assertValueEqual(NutritionState.get(player).resistanceLevel(), 2, "One tick below severe threshold does not immediately remove severe state");
        NutritionState.set(player, new NutritionState(1, 160_000, 160_000, 160_000, 96_000, 2));
        player.getFoodData().tick(player);
        helper.assertValueEqual(NutritionState.get(player).resistanceLevel(), 1, "Below ninety-six-thousand recovers only to moderate");
        NutritionState.set(player, new NutritionState(1, 160_000, 160_000, 160_000, 48_000, 1));
        player.getFoodData().tick(player);
        helper.assertValueEqual(NutritionState.get(player).resistanceLevel(), 0, "Below forty-eight-thousand retains mild resistance");
        NutritionState.set(player, new NutritionState(1, 160_000, 160_000, 160_000, 1, 0));
        player.getFoodData().tick(player);
        helper.assertValueEqual(NutritionState.get(player).resistanceLevel(), -1, "Only numeric zero clears mild resistance");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void moderateResistanceReducesFoodSatiationBeforeAddingItsSugarResponse(GameTestHelper helper) {
        Player player = player(helper);
        setEnergy(player, 0, 0);
        NutritionState.set(player, new NutritionState(1, 160_000, 160_000, 160_000, 96_000, 1));
        new ItemStack(Items.COOKIE).finishUsingItem(helper.getLevel(), player);
        assertEnergy(helper, player, 2, 1, "Cookie's three satiation loses one for its two-hundred fifty sugar");
        helper.assertValueEqual(NutritionState.get(player).resistance(), 97_200, "Cookie adds one-thousand two-hundred resistance");
        helper.assertValueEqual(player.getEffect(MobEffects.CONFUSION).getAmplifier(), 1, "Moderate resistance uses nausea amplifier one");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nauseaPreventsStartingFoodAndCancelingUseConsumesNothing(GameTestHelper helper) {
        Player player = player(helper);
        setEnergy(player, 0, 0);
        ItemStack bread = new ItemStack(Items.BREAD, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, bread);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400, 0));
        helper.assertFalse(FoodConsumption.canIngest(player, bread), "Nausea blocks ordinary ingestion");
        bread.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertFalse(player.isUsingItem(), "The real use-start event rejects nauseated eating");
        helper.assertValueEqual(bread.getCount(), 2, "Denied eating does not consume the stack");
        assertEnergy(helper, player, 0, 0, "Denied eating supplies no resources");
        player.removeEffect(MobEffects.CONFUSION);
        bread.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem(), "The same stack becomes usable after nausea ends");
        player.stopUsingItem();
        helper.assertFalse(player.isUsingItem(), "Stopping use cancels eating before completion");
        helper.assertValueEqual(bread.getCount(), 2, "Canceled eating leaves both bread items");
        assertEnergy(helper, player, 0, 0, "Canceled eating supplies no resources");
        helper.assertValueEqual(NutritionState.get(player), NutritionState.INITIAL, "Canceled eating changes no micronutrients or sugar state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldFoodSaveMigratesNutritionAndNewSavePreservesAllProgress(GameTestHelper helper) {
        Player player = player(helper);
        CompoundTag legacy = foodTag(5, 2F, 0.875F);
        player.getFoodData().readAdditionalSaveData(legacy);
        assertEnergy(helper, player, 5, 5, "Legacy saves initialize missing nutrition from the existing food level");

        CompoundTag modern = foodTag(5, 2F, 0.875F);
        modern.putInt("mite_food_version", 1);
        modern.putInt("mite_nutrition", 4);
        modern.putFloat("mite_heal_progress", 0.375F);
        modern.putFloat("mite_starve_progress", 0.75F);
        modern.putFloat("mite_nutrition_hunger", 1.25F);
        player.getFoodData().readAdditionalSaveData(modern);
        NutritionState expected = new NutritionState(1, 123, 456, 789, 95_999, 1);
        NutritionState.set(player, expected);
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        helper.assertValueEqual(saved.getInt("mite_food_version"), 1, "New food save declares its version");
        helper.assertValueEqual(saved.getFloat("mite_heal_progress"), 0.375F, "Healing progress remains a float");
        helper.assertValueEqual(saved.getFloat("mite_starve_progress"), 0.75F, "Starvation progress remains a float");
        helper.assertValueEqual(saved.getFloat("mite_nutrition_hunger"), 1.25F, "Nutrition-only hunger survives saving");
        helper.assertValueEqual(saved.getFloat("foodExhaustionLevel"), 0.875F, "Shared hunger survives saving");
        Player restored = player(helper);
        restored.load(saved);
        assertEnergy(helper, restored, 5, 4, "New saves retain distinct satiation and nutrition");
        helper.assertValueEqual(NutritionState.get(restored), expected, "All nutrient and resistance attachment fields survive full entity load");
        CompoundTag savedAgain = restored.saveWithoutId(new CompoundTag());
        for (String key : new String[]{"mite_heal_progress", "mite_starve_progress", "mite_nutrition_hunger", "foodExhaustionLevel"}) {
            helper.assertValueEqual(Float.floatToRawIntBits(savedAgain.getFloat(key)), Float.floatToRawIntBits(saved.getFloat(key)),
                    "Save-load retains exact float bits for " + key);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void accumulatedHungerUsesOneDeductionPerTickAndKeepsTheIndependentReserve(GameTestHelper helper) {
        Player player = player(helper);
        setEnergy(player, 6, 6);
        player.getFoodData().addExhaustion(8F);
        player.getFoodData().tick(player);
        assertEnergy(helper, player, 6, 5, "Eight hunger deducts only one nutrition on its first tick and preserves satiation");
        player.getFoodData().tick(player);
        assertEnergy(helper, player, 6, 4, "The queued second nutrition deduction occurs on the next tick");
        setEnergy(player, 0, 6);
        player.getFoodData().addExhaustion(4F);
        player.getFoodData().tick(player);
        assertEnergy(helper, player, 0, 5, "Without satiation a deduction consumes independent nutrition");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void retainedSatiationPreventsStarvationAfterNutritionIsEmpty(GameTestHelper helper) {
        Player player = player(helper);
        setEnergy(player, 6, 0);
        player.getFoodData().setSaturation(6F);
        player.setHealth(6F);
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty previous = helper.getLevel().getDifficulty();
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            for (int i = 0; i < 500; i++) player.getFoodData().tick(player);
            helper.assertValueEqual(player.getHealth(), 6F, "Retained satiation prevents starvation after five-hundred updates");
            player.getFoodData().tick(player);
            helper.assertValueEqual(player.getHealth(), 6F, "The five-hundred first update still cannot damage a player with satiation");
            assertEnergy(helper, player, 6, 0, "Empty nutrition cannot discard the retained satiation reserve");
            helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 6F,
                    "The existing native saturation buffer is preserved when no exhaustion unit is due");
        } finally {
            server.setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nutritionSixRegeneratesAfter1924UpdatesAndFullHealthClearsProgress(GameTestHelper helper) {
        Player player = player(helper);
        setEnergy(player, 6, 6);
        player.setHealth(4F);
        MinecraftServer server = helper.getLevel().getServer();
        GameRules.BooleanValue natural = helper.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
        boolean old = natural.get();
        try {
            natural.set(true, server);
            for (int i = 0; i < 1_923; i++) player.getFoodData().tick(player);
            helper.assertValueEqual(player.getHealth(), 4F, "Nutrition six does not use modern fast regeneration");
            player.getFoodData().tick(player);
            helper.assertValueEqual(player.getHealth(), 5F, "The one-thousand nine-hundred twenty-fourth update heals one health");
            assertEnergy(helper, player, 6, 6, "Regeneration's extra hunger is queued after this tick's deduction stage");
            player.getFoodData().tick(player);
            assertEnergy(helper, player, 6, 5, "Regeneration's shared hunger consumes nutrition before satiation");
            player.setHealth(6F);
            player.getFoodData().tick(player);
            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            helper.assertValueEqual(saved.getFloat("mite_heal_progress"), 0F, "Full health clears healing progress");
            player.setHealth(4F);
            player.getFoodData().tick(player);
            natural.set(false, server);
            player.getFoodData().tick(player);
            saved = player.saveWithoutId(new CompoundTag());
            helper.assertValueEqual(saved.getFloat("mite_heal_progress"), 0F, "Disabled natural regeneration also clears progress");
        } finally {
            natural.set(old, server);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalMicronutrientsDecayOncePerUpdateButCreativeDoesNot(GameTestHelper helper) {
        Player survival = player(helper);
        NutritionState.set(survival, new NutritionState(1, 2, 1, 0, 2, 0));
        survival.getFoodData().tick(survival);
        NutritionState state = NutritionState.get(survival);
        helper.assertValueEqual(state.protein(), 1, "One survival update consumes one protein");
        helper.assertValueEqual(state.fat(), 0, "One survival update consumes one essential-fat point");
        helper.assertValueEqual(state.plants(), 0, "Depleted plant nutrients do not become negative");
        helper.assertValueEqual(state.resistance(), 1, "One survival update removes one resistance point");
        helper.assertValueEqual(state.resistanceLevel(), 0, "Residual mild resistance remains active");
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        NutritionState unchanged = new NutritionState(1, 2, 1, 0, 2, 0);
        NutritionState.set(creative, unchanged);
        creative.getFoodData().addExhaustion(8F);
        creative.getFoodData().tick(creative);
        helper.assertValueEqual(NutritionState.get(creative), unchanged, "Creative updates preserve every micronutrient and resistance field");
        assertEnergy(helper, creative, 6, 6, "Creative hunger requests do not drain either food resource");
        helper.succeed();
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(6D);
        player.setHealth(6F);
        return player;
    }

    private static DualFoodData dual(FoodData food) { return (DualFoodData) food; }

    private static void setEnergy(Player player, int satiation, int nutrition) {
        player.getFoodData().setFoodLevel(satiation);
        dual(player.getFoodData()).mite$setNutrition(nutrition);
    }

    private static void assertEnergy(GameTestHelper helper, Player player, int satiation, int nutrition, String context) {
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), satiation, context + " (satiation)");
        helper.assertValueEqual(dual(player.getFoodData()).mite$getNutrition(), nutrition, context + " (nutrition)");
    }

    private static CompoundTag foodTag(int satiation, float nativeSaturation, float hunger) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("foodLevel", satiation);
        tag.putInt("foodTickTimer", 0);
        tag.putFloat("foodSaturationLevel", nativeSaturation);
        tag.putFloat("foodExhaustionLevel", hunger);
        return tag;
    }
}
