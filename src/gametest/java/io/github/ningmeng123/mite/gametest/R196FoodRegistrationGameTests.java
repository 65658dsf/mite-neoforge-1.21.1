package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.item.FoodProfile;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/** Literal R196 registration expectations, independent from the loaded production catalog. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class R196FoodRegistrationGameTests {
    private record Expected(String path, int legacyId, int stackLimit, @Nullable FoodProfile profile,
                            @Nullable String remainder) {
        ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("mite", path); }
    }

    // Copied independently from R196 runtime items and yc/ItemBowl/MaterialSoup declarations.
    // Metal order is explicit here rather than derived from MiteFoods.METALS or production JSON.
    private static final List<Expected> EXPECTED = List.of(
            nonFood("flour", 1184, 16),
            food("dough", 1185, 16, 6, 2, "", 0, false, false, null),
            food("cheese", 1183, 16, 3, 3, "P", 0, false, false, null),
            food("chocolate", 1186, 16, 3, 3, "", 1000, false, false, null),
            food("onion", 1187, 16, 1, 1, "V", 0, false, false, null),
            food("orange", 1218, 16, 2, 1, "V", 1000, false, false, null),
            food("banana", 1219, 16, 2, 1, "V", 1000, false, false, null),
            food("blueberries", 1225, 16, 1, 1, "V", 1000, false, false, null),
            food("raw_large_fish", 1229, 16, 5, 5, "PF", 0, false, false, null),
            food("cooked_large_fish", 1230, 16, 10, 10, "PF", 0, false, false, null),
            food("raw_worm", 1280, 16, 0, 1, "P", 0, false, false, null),
            food("cooked_worm", 1281, 16, 1, 1, "P", 0, false, false, null),
            nonFood("manure", 1191, 16),
            food("milk_bowl", 1166, 4, 0, 1, "P", 0, true, true, "minecraft:bowl"),
            food("water_bowl", 1167, 4, 0, 0, "", 0, true, false, "minecraft:bowl"),
            food("beef_stew", 1188, 4, 16, 16, "PV", 0, false, false, "minecraft:bowl"),
            food("chicken_soup", 1189, 4, 10, 10, "PV", 0, true, false, "minecraft:bowl"),
            food("vegetable_soup", 1190, 4, 6, 6, "V", 0, true, false, "minecraft:bowl"),
            food("ice_cream", 1206, 4, 5, 4, "P", 1000, false, false, "minecraft:bowl"),
            food("salad", 1209, 4, 1, 1, "V", 0, false, false, "minecraft:bowl"),
            food("cream_of_mushroom_soup", 1215, 4, 3, 5, "P", 0, true, false, "minecraft:bowl"),
            food("cream_of_vegetable_soup", 1216, 4, 7, 7, "PV", 0, true, false, "minecraft:bowl"),
            food("pumpkin_soup", 1217, 4, 1, 2, "V", 0, true, false, "minecraft:bowl"),
            food("mashed_potato", 1223, 4, 12, 8, "P", 0, false, false, "minecraft:bowl"),
            food("sorbet", 1224, 4, 4, 2, "V", 2000, false, false, "minecraft:bowl"),
            food("porridge", 1226, 4, 4, 2, "V", 2000, false, false, "minecraft:bowl"),
            food("cereal", 1227, 4, 4, 2, "P", 1000, false, false, "minecraft:bowl"),
            nonFood("copper_bucket", 1142, 8),
            nonFood("copper_water_bucket", 1148, 1),
            food("copper_milk_bucket", 1160, 1, 0, 4, "P", 0, true, true, "mite:copper_bucket"),
            nonFood("silver_bucket", 1143, 8),
            nonFood("silver_water_bucket", 1149, 1),
            food("silver_milk_bucket", 1161, 1, 0, 4, "P", 0, true, true, "mite:silver_bucket"),
            nonFood("gold_bucket", 1144, 8),
            nonFood("gold_water_bucket", 1150, 1),
            food("gold_milk_bucket", 1162, 1, 0, 4, "P", 0, true, true, "mite:gold_bucket"),
            nonFood("mithril_bucket", 1145, 8),
            nonFood("mithril_water_bucket", 1151, 1),
            food("mithril_milk_bucket", 1163, 1, 0, 4, "P", 0, true, true, "mite:mithril_bucket"),
            nonFood("adamantium_bucket", 1146, 8),
            nonFood("adamantium_water_bucket", 1152, 1),
            food("adamantium_milk_bucket", 1164, 1, 0, 4, "P", 0, true, true, "mite:adamantium_bucket"),
            nonFood("ancient_metal_bucket", 1147, 8),
            nonFood("ancient_metal_water_bucket", 1153, 1),
            food("ancient_metal_milk_bucket", 1165, 1, 0, 4, "P", 0, true, true, "mite:ancient_metal_bucket"),
            // Source lava vessel peers enter the furnace/fuel batch, rather than gaining FOOD.
            nonFood("copper_lava_bucket", 1154, 1),
            nonFood("silver_lava_bucket", 1155, 1),
            nonFood("gold_lava_bucket", 1156, 1),
            nonFood("mithril_lava_bucket", 1157, 1),
            nonFood("adamantium_lava_bucket", 1158, 1),
            nonFood("ancient_metal_lava_bucket", 1159, 1)
    );

    private R196FoodRegistrationGameTests() {}

    @GameTest(template = "empty")
    public static void allFiftyOneSourceItemsAreRegisteredWithIndependentStackAndEdibilityExpectations(GameTestHelper helper) {
        helper.assertValueEqual(EXPECTED.size(), 51, "The literal source fixture contains51 new food/material/vessel IDs, including six lava peers");
        Set<String> names = new HashSet<>();
        for (Expected expected : EXPECTED) {
            helper.assertTrue(names.add(expected.path()), "The fixture cannot hide duplicate source registrations");
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(expected.id()), "Actual registration exists: " + expected.id());
            Item item = BuiltInRegistries.ITEM.get(expected.id());
            helper.assertTrue(MiteFoods.get(expected.path()) == item, "Runtime helper resolves the exact registry item: " + expected.id());
            ItemStack stack = new ItemStack(item);
            helper.assertValueEqual(stack.getMaxStackSize(), expected.stackLimit(), "Independent source stack maximum: " + expected.id());
            helper.assertValueEqual(stack.has(DataComponents.FOOD), expected.profile() != null, "Only actual ingestible items get FOOD: " + expected.id());
            if (expected.profile() == null)
                helper.assertTrue(MiteFoodCatalog.profile(stack) == null, "Flour/manure/empty and water buckets are not silently edible: " + expected.id());
        }
        helper.assertValueEqual(MiteFoods.entries().keySet(), names, "Registered new item set exactly matches all51 independent expected IDs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allThirtyOneFoodProfilesHaveExactSourceValuesThirtyTwoTicksAndNoInventedSideEffects(GameTestHelper helper) {
        Player player = player(helper);
        int foods = 0;
        for (Expected expected : EXPECTED) {
            if (expected.profile() == null) continue;
            foods++;
            ItemStack stack = new ItemStack(MiteFoods.get(expected.path()));
            FoodProfile profile = MiteFoodCatalog.profile(stack);
            helper.assertValueEqual(profile, expected.profile(), "Independent full S/N, micronutrient, sugar and use-category fixture: " + expected.id());
            helper.assertValueEqual(MiteFoodCatalog.sourceProfiles().get(stack.getItem()), expected.profile(), "New original content belongs to source mappings: " + expected.id());
            helper.assertFalse(MiteFoodCatalog.modernProfiles().containsKey(stack.getItem()), "Source food cannot be relabelled as a guessed modern adaptation: " + expected.id());
            FoodProperties properties = stack.get(DataComponents.FOOD);
            helper.assertValueEqual(properties.nutrition(), expected.profile().satiation(), "Native hunger metadata uses S: " + expected.id());
            helper.assertValueEqual(properties.saturation(), (float) expected.profile().nutrition(),
                    "AppleSkin saturation metadata displays the source N absolute value: " + expected.id());
            helper.assertTrue(properties.canAlwaysEat(), "Native start delegates actual fullness qualification to the shared server rules: " + expected.id());
            helper.assertValueEqual(properties.eatDurationTicks(), 32, "FOOD use duration is32 ticks: " + expected.id());
            helper.assertValueEqual(stack.getUseDuration(player), 32, "Actual item-stack use duration is32 ticks: " + expected.id());
            helper.assertValueEqual(stack.getUseAnimation(), expected.profile().drink() ? UseAnim.DRINK : UseAnim.EAT,
                    "Original soups are drinks and original stews/porridge/cereal remain foods: " + expected.id());
            helper.assertTrue(properties.effects().isEmpty(), "No poison or other effect is invented for newly mapped source foods: " + expected.id());
            if (expected.remainder() == null) helper.assertTrue(properties.usingConvertsTo().isEmpty(), "Ordinary food has no invented container: " + expected.id());
            else helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(properties.usingConvertsTo().orElseThrow().getItem()).toString(),
                    expected.remainder(), "Food metadata names the correct wooden bowl or matching metal bucket: " + expected.id());
        }
        helper.assertValueEqual(foods, 31, "Added profiles include eleven foods, fourteen bowls and six milk buckets");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceResourceMatchesTheLiteralOldIdsAndFoodFieldsWithoutDependingOnExternalArchives(GameTestHelper helper) {
        Map<String, JsonObject> foodRows = new HashMap<>();
        Map<String, JsonObject> nonFoodRows = new HashMap<>();
        var stream = R196FoodRegistrationGameTests.class.getResourceAsStream("/mite/foods-018.json");
        helper.assertTrue(stream != null, "The published production food data is available on the classpath");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("foods")) {
                JsonObject row = element.getAsJsonObject();
                helper.assertTrue(foodRows.put(row.get("id").getAsString(), row) == null, "Food data has no duplicate IDs");
            }
            for (JsonElement element : root.getAsJsonArray("non_food_items")) {
                JsonObject row = element.getAsJsonObject();
                helper.assertTrue(nonFoodRows.put(row.get("id").getAsString(), row) == null, "Material/vessel data has no duplicate IDs");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read the production food resource", exception);
        }
        helper.assertValueEqual(foodRows.size(), 80, "Current production catalog has80 ingestion profiles");
        helper.assertValueEqual(nonFoodRows.size(), 20, "Source materials and noningestible fluid vessels include six lava peers among20 explicit metadata entries");
        for (Expected expected : EXPECTED) {
            JsonObject row = (expected.profile() != null ? foodRows : nonFoodRows).get(expected.id().toString());
            helper.assertTrue(row != null, "Source resource records the actual ID: " + expected.id());
            helper.assertValueEqual(row.get("source_id").getAsInt(), expected.legacyId(), "Old numeric identity comes from the independent literal fixture: " + expected.id());
            helper.assertValueEqual(row.get("stack_limit").getAsInt(), expected.stackLimit(), "Resource stack limit agrees with independent R196 expectation: " + expected.id());
            if (expected.profile() != null) {
                helper.assertValueEqual(row.get("source_kind").getAsString(), "r196", "Added food retains its real original source classification: " + expected.id());
                helper.assertValueEqual(row.get("satiation").getAsInt(), expected.profile().satiation(), "Source satiation literal: " + expected.id());
                helper.assertValueEqual(row.get("nutrition").getAsInt(), expected.profile().nutrition(), "Source nutrition literal: " + expected.id());
                helper.assertValueEqual(row.get("protein").getAsBoolean(), expected.profile().protein(), "Source protein flag: " + expected.id());
                helper.assertValueEqual(row.get("fat").getAsBoolean(), expected.profile().fat(), "Source essential-fat flag: " + expected.id());
                helper.assertValueEqual(row.get("plants").getAsBoolean(), expected.profile().plants(), "Source phytonutrient flag: " + expected.id());
                helper.assertValueEqual(row.get("sugar").getAsInt(), expected.profile().sugar(), "Source sugar amount: " + expected.id());
                helper.assertValueEqual(row.get("drink").getAsBoolean(), expected.profile().drink(), "Source ingest action: " + expected.id());
                helper.assertValueEqual(row.get("always_edible").getAsBoolean(), expected.profile().alwaysEdible(), "Source fullness exception: " + expected.id());
                helper.assertValueEqual(row.get("use_ticks").getAsInt(), 32, "Source handheld use ticks: " + expected.id());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyNewFoodReallyConsumesOneServingAddsItsSourceResourcesAndReturnsOnlyItsOwnContainer(GameTestHelper helper) {
        int completed = 0;
        for (Expected expected : EXPECTED) {
            if (expected.profile() == null) continue;
            Player player = player(helper);
            int count = expected.stackLimit() == 1 ? 1 : 2;
            ItemStack source = new ItemStack(MiteFoods.get(expected.path()), count);
            ItemStack result = source.finishUsingItem(helper.getLevel(), player);
            helper.assertValueEqual(player.getFoodData().getFoodLevel(), expected.profile().satiation(), "Actual completion supplies exact source S: " + expected.id());
            helper.assertValueEqual(((DualFoodData) player.getFoodData()).mite$getNutrition(), expected.profile().nutrition(), "Actual completion supplies exact source N once: " + expected.id());
            helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 0F,
                    "Display compatibility metadata must not add to the seed-only native buffer during source S/N ingestion: " + expected.id());
            NutritionState state = NutritionState.get(player);
            helper.assertValueEqual(state.protein(), expected.profile().protein() ? expected.profile().nutrition() * 8000 : 0, "Actual source protein intake: " + expected.id());
            helper.assertValueEqual(state.fat(), expected.profile().fat() ? expected.profile().nutrition() * 8000 : 0, "Actual source essential-fat intake: " + expected.id());
            helper.assertValueEqual(state.plants(), expected.profile().plants() ? expected.profile().nutrition() * 8000 : 0, "Actual source phytonutrient intake: " + expected.id());
            int resistance = expected.profile().sugar() == 2000 ? 9600 : expected.profile().sugar() == 1000 ? 4800 : 0;
            helper.assertValueEqual(state.resistance(), resistance, "Actual source sugar intake is applied once: " + expected.id());
            helper.assertTrue(player.getActiveEffects().isEmpty(), "Ordinary source consumption adds no invented side effects at zero resistance: " + expected.id());
            if (count == 2) {
                helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(result.getItem()), expected.id(), "Nonfinal serving keeps the remaining food item: " + expected.id());
                helper.assertValueEqual(result.getCount(), 1, "Actual completion consumes exactly one of two servings: " + expected.id());
                if (expected.remainder() != null) {
                    Item container = BuiltInRegistries.ITEM.get(ResourceLocation.parse(expected.remainder()));
                    helper.assertValueEqual(player.getInventory().countItem(container), 1, "The sole empty bowl is added once: " + expected.id());
                }
            } else {
                helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), expected.remainder(), "Final milk serving returns its own empty metal bucket: " + expected.id());
                helper.assertValueEqual(result.getCount(), 1, "Final milk serving returns exactly one replacement bucket: " + expected.id());
                helper.assertTrue(source.isEmpty(), "The single milk serving is consumed: " + expected.id());
                Item container = BuiltInRegistries.ITEM.get(ResourceLocation.parse(expected.remainder()));
                helper.assertValueEqual(player.getInventory().countItem(container), 0, "A returned replacement bucket is not duplicated in the inventory: " + expected.id());
            }
            completed++;
        }
        helper.assertValueEqual(completed, 31, "Every added ingestion profile is exercised through its actual item completion");
        helper.succeed();
    }

    private static Expected food(String path, int legacy, int stack, int satiation, int nutrition, String nutrients,
                                 int sugar, boolean drink, boolean always, @Nullable String remainder) {
        return new Expected(path, legacy, stack, new FoodProfile(satiation, nutrition, nutrients.contains("P"),
                nutrients.contains("F"), nutrients.contains("V"), sugar, drink, always), remainder);
    }

    private static Expected nonFood(String path, int legacy, int stack) {
        return new Expected(path, legacy, stack, null, null);
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.experienceLevel = 35;
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        player.setPos(center.x, center.y, center.z);
        player.getFoodData().setFoodLevel(0);
        ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
        player.getFoodData().setSaturation(0F);
        NutritionState.set(player, new NutritionState(1, 0, 0, 0, 0, -1));
        return player;
    }
}
