package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.item.FoodProfile;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent R196 table fixtures and modified native components. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodCatalogGameTests {
    private FoodCatalogGameTests() {}

    @GameTest(template = "empty")
    public static void sourceFoodsCarryIndependentResourcesAndOpenTheServerQualifiedUsePath(GameTestHelper helper) {
        helper.assertValueEqual(MiteFoodCatalog.sourceProfiles().size(), 64, "33 existing IDs and31 newly registered source foods preserve verified release values");
        helper.assertValueEqual(MiteFoodCatalog.modernProfiles().size(), 16, "15 modern foods and the explicit user wheat-seed adaptation have separate profiles");
        helper.assertValueEqual(MiteFoodCatalog.sourceProfiles().size() + MiteFoodCatalog.modernProfiles().size(), 80,
                "The complete current ingestion catalog has80 profiles including source zero-resource water bowl");
        verifyComponents(helper, MiteFoodCatalog.sourceProfiles());
        verifyComponents(helper, MiteFoodCatalog.modernProfiles());
        helper.assertValueEqual(profile(Items.BREAD), new FoodProfile(8, 2, false, false, false, 0, false, false),
                "R196 bread is S8/N2, not native hunger5 and saturation6");
        helper.assertValueEqual(profile(Items.BEEF), new FoodProfile(5, 5, true, false, false, 0, false, false), "Raw beef source profile");
        helper.assertValueEqual(profile(Items.COOKED_BEEF), new FoodProfile(10, 10, true, false, false, 0, false, false), "Cooked beef source profile");
        helper.assertValueEqual(profile(Items.COD), new FoodProfile(3, 3, true, true, false, 0, false, false), "Modern cod explicitly represents R196 small fish");
        helper.assertValueEqual(profile(Items.MUTTON), new FoodProfile(3, 3, true, false, false, 0, false, false), "R196 lambchop maps existing mutton");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void formerlyInedibleFoodsAndDifferentStackLimitsMatchTheRelease(GameTestHelper helper) {
        for (Item item : new Item[]{Items.BROWN_MUSHROOM, Items.RED_MUSHROOM, Items.SUGAR, Items.WHEAT_SEEDS,
                Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.NETHER_WART, Items.EGG})
            helper.assertTrue(new ItemStack(item).has(DataComponents.FOOD), "Existing original-release food gets native use component: " + item);
        helper.assertValueEqual(new ItemStack(Items.BROWN_MUSHROOM).getMaxStackSize(), 32, "Mushrooms stack32");
        helper.assertValueEqual(new ItemStack(Items.WHEAT_SEEDS).getMaxStackSize(), 64, "Wheat seeds stack64");
        helper.assertValueEqual(new ItemStack(Items.NETHER_WART).getMaxStackSize(), 64, "Nether wart retains release stack64");
        helper.assertValueEqual(new ItemStack(Items.APPLE).getMaxStackSize(), 16, "Ordinary foods stack16");
        helper.assertValueEqual(new ItemStack(Items.PUMPKIN_PIE).getMaxStackSize(), 8, "Pumpkin pie stacks8");
        helper.assertValueEqual(profile(Items.WHEAT_SEEDS), new FoodProfile(0, 0, false, false, false, 0, false, false),
                "User-adapted wheat seeds supply only native saturation, with no S/N or micronutrients");
        helper.assertValueEqual(food(Items.WHEAT_SEEDS).nutrition(), 0, "Wheat seed FOOD adds no native hunger value");
        helper.assertValueEqual(food(Items.WHEAT_SEEDS).saturation(), 1F, "Wheat seed FOOD carries exactly one absolute native saturation point despite nutrition zero");
        helper.assertFalse(MiteFoodCatalog.sourceProfiles().containsKey(Items.WHEAT_SEEDS), "User override must not be classified as an unchanged source value");
        helper.assertTrue(MiteFoodCatalog.modernProfiles().containsKey(Items.WHEAT_SEEDS), "The seed override is an explicit adaptation");
        helper.assertValueEqual(profile(Items.EGG), new FoodProfile(1, 3, true, false, false, 0, false, false), "Eating egg profile precedes separate projectile interaction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poisonReplacesNativeHungerAndRedMushroomHasExactlyTwoEffects(GameTestHelper helper) {
        checkEffect(helper, Items.CHICKEN, MobEffects.POISON, 400, 0, .3F);
        checkEffect(helper, Items.ROTTEN_FLESH, MobEffects.POISON, 400, 0, .8F);
        checkEffect(helper, Items.SPIDER_EYE, MobEffects.POISON, 100, 0, 1F);
        checkEffect(helper, Items.POISONOUS_POTATO, MobEffects.POISON, 100, 0, .6F);
        helper.assertValueEqual(food(Items.CHICKEN).effects().size(), 1, "Chicken poison does not also retain modern hunger");
        helper.assertValueEqual(food(Items.ROTTEN_FLESH).effects().size(), 1, "Rotten flesh poison does not also retain modern hunger");
        helper.assertValueEqual(food(Items.RED_MUSHROOM).effects().size(), 2, "Red mushroom adds only its original two effects");
        checkEffect(helper, Items.RED_MUSHROOM, MobEffects.POISON, 200, 0, 1F);
        checkEffect(helper, Items.RED_MUSHROOM, MobEffects.CONFUSION, 1200, 4, 1F);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void goldenAppleSubtypesHaveSourceEffectsWithoutNativeAbsorption(GameTestHelper helper) {
        helper.assertValueEqual(profile(Items.GOLDEN_APPLE), profile(Items.ENCHANTED_GOLDEN_APPLE), "Both apple subtypes share original S/N and micronutrients");
        helper.assertTrue(profile(Items.GOLDEN_APPLE).alwaysEdible(), "Golden apple remains inherently edible at full resources");
        helper.assertValueEqual(food(Items.GOLDEN_APPLE).effects().size(), 1, "Ordinary apple has exactly one original effect");
        checkEffect(helper, Items.GOLDEN_APPLE, MobEffects.REGENERATION, 1200, 0, 1F);
        helper.assertValueEqual(food(Items.ENCHANTED_GOLDEN_APPLE).effects().size(), 3, "Enchanted apple has exactly three original effects");
        checkEffect(helper, Items.ENCHANTED_GOLDEN_APPLE, MobEffects.REGENERATION, 1200, 1, 1F);
        checkEffect(helper, Items.ENCHANTED_GOLDEN_APPLE, MobEffects.DAMAGE_RESISTANCE, 1200, 0, 1F);
        checkEffect(helper, Items.ENCHANTED_GOLDEN_APPLE, MobEffects.FIRE_RESISTANCE, 1200, 0, 1F);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void containersAndCakeRemainDistinctFromOrdinaryHandheldFood(GameTestHelper helper) {
        helper.assertValueEqual(profile(Items.MILK_BUCKET), new FoodProfile(0, 4, true, false, false, 0, true, true), "Milk supplies nutrition only and is a drink");
        helper.assertValueEqual(new ItemStack(Items.MILK_BUCKET).getMaxStackSize(), 1, "Milk bucket cannot stack");
        helper.assertTrue(food(Items.MILK_BUCKET).usingConvertsTo().orElseThrow().is(Items.BUCKET), "Milk metadata retains iron bucket remainder");
        helper.assertValueEqual(profile(Items.MUSHROOM_STEW), new FoodProfile(2, 4, false, false, false, 0, false, false), "Mushroom stew remains EAT, not soup DRINK");
        helper.assertValueEqual(new ItemStack(Items.MUSHROOM_STEW).getMaxStackSize(), 4, "Mushroom stew stacks4");
        helper.assertTrue(food(Items.MUSHROOM_STEW).usingConvertsTo().orElseThrow().is(Items.BOWL), "Stew consumes one unit and produces one bowl");
        helper.assertValueEqual(new ItemStack(Items.BOWL).getMaxStackSize(), 16, "Empty wooden bowls stack16");
        helper.assertValueEqual(new ItemStack(Items.BUCKET).getMaxStackSize(), 8, "Native empty iron buckets match R196 stack8");
        helper.assertValueEqual(new ItemStack(Items.WATER_BUCKET).getMaxStackSize(), 1, "Native filled iron water buckets remain stack1");
        helper.assertValueEqual(new ItemStack(Items.CAKE).getMaxStackSize(), 8, "Whole cake item stacks8");
        helper.assertFalse(new ItemStack(Items.CAKE).has(DataComponents.FOOD), "Cake item cannot be eaten from the hand");
        helper.assertTrue(profile(Items.CAKE) == null, "Cake's placed-block interaction has no handheld food profile");
        helper.assertValueEqual(MiteFoodCatalog.CAKE_SLICE, new FoodProfile(2, 2, true, false, false, 166, false, false), "Independent source cake slice fixture");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernFoodsAndExternalComponentsShareTheDualFoodCompatibilityPolicy(GameTestHelper helper) {
        helper.assertValueEqual(profile(Items.SALMON), new FoodProfile(2, 2, true, true, false, 0, false, false), "Modern salmon mapping is explicit and distinct from R196 small fish");
        helper.assertValueEqual(profile(Items.HONEY_BOTTLE), new FoodProfile(6, 6, false, false, false, 2000, true, true), "Modern honey has explicit sugar and drinking policy");
        helper.assertTrue(profile(Items.CHORUS_FRUIT).alwaysEdible(), "Modern chorus teleport remains available at full resources");
        helper.assertTrue(profile(Items.BEETROOT_SOUP).drink(), "Modern soup uses drink category");
        helper.assertValueEqual(food(Items.PUFFERFISH).effects().size(), 3, "Modern pufferfish preserves all three native side effects");
        ItemStack external = new ItemStack(Items.STONE);
        external.set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(7).saturationModifier(2F).alwaysEdible().build());
        helper.assertValueEqual(MiteFoodCatalog.profile(external), new FoodProfile(7, 7, false, false, false, 0, false, true),
                "External food nutrition supplies S/N; saturation multiplier cannot invent micronutrients");
        ItemStack externalDrink = new ItemStack(Items.POTION);
        externalDrink.set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(3).build());
        helper.assertValueEqual(MiteFoodCatalog.profile(externalDrink), new FoodProfile(3, 3, false, false, false, 0, true, false),
                "External FOOD drink participates in the same S/N compatibility policy");
        helper.assertTrue(profile(Items.POTION) == null, "Normal potions are not food");
        helper.assertFalse(new ItemStack(Items.OMINOUS_BOTTLE).has(DataComponents.FOOD), "Unused native ominous-bottle FOOD is removed");
        helper.assertTrue(profile(Items.OMINOUS_BOTTLE) == null, "Ominous bottle keeps its independent effect behavior without fake nutrition");
        helper.succeed();
    }

    private static void verifyComponents(GameTestHelper helper, Map<Item, FoodProfile> profiles) {
        profiles.forEach((item, profile) -> {
            FoodProperties food = food(item);
            helper.assertTrue(food != null, "Mapped food has native component: " + item);
            helper.assertTrue(food.canAlwaysEat(), "Native use delegates fullness decisions to authoritative dual resource rules: " + item);
            helper.assertValueEqual(food.nutrition(), profile.satiation(), "Native HUD metadata represents S, not modern saturation: " + item);
            helper.assertValueEqual(food.saturation(), item == Items.WHEAT_SEEDS ? 1F : 0F,
                    "Only the explicit wheat-seed override supplies native saturation: " + item);
            helper.assertValueEqual(food.eatDurationTicks(), 32, "Catalog use duration is32 ticks: " + item);
        });
    }

    private static void checkEffect(GameTestHelper helper, Item item, Holder<MobEffect> type, int duration, int amplifier, float probability) {
        FoodProperties.PossibleEffect effect = food(item).effects().stream()
                .filter(candidate -> candidate.effect().getEffect().equals(type)).findFirst().orElseThrow();
        helper.assertValueEqual(effect.effect().getDuration(), duration, "Source effect duration: " + item + " " + type);
        helper.assertValueEqual(effect.effect().getAmplifier(), amplifier, "Source effect amplifier: " + item + " " + type);
        helper.assertValueEqual(effect.probability(), probability, "Source effect chance: " + item + " " + type);
    }

    private static FoodProfile profile(Item item) { return MiteFoodCatalog.profile(new ItemStack(item)); }
    private static FoodProperties food(Item item) { return new ItemStack(item).get(DataComponents.FOOD); }
}
