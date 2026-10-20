package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.client.GearProgressionTooltips;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.R196ReinforcedBowItem;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered equipment and normal tooltips share the approved category experience thresholds. @author NingMeng123 */
@GameTestHolder("mite_gear")
@PrefixGameTestTemplate(false)
public final class GearExperienceThresholdGameTests {
    private static final int[] LEVELS = {0, 1, 10, 24};
    private static final UUID IDENTITY = new UUID(0L, 1L);

    // Approved spreadsheet anchors at L0/1/10/24, kept independent of the production arithmetic.
    private static final int[] TOOL = {100, 150, 600, 1300};
    private static final int[] WEAPON = {150, 250, 1150, 2550};
    private static final int[] HELMET = {40, 60, 240, 520};
    private static final int[] CHESTPLATE = {80, 120, 480, 1040};
    private static final int[] LEGGINGS = {60, 90, 360, 780};
    private static final int[] BOOTS = {20, 30, 120, 260};

    private GearExperienceThresholdGameTests() {}

    @GameTest(template = "empty")
    public static void everyRegisteredLegacyAndVibraniumToolHasExactRequirementsAndTooltip(GameTestHelper helper) {
        int checked = 0;
        for (var definition : MiteTools.definitions()) {
            ItemStack stack = registered(helper, definition.id());
            helper.assertValueEqual(GearProgression.material(stack), definition.material(),
                    "The actual tool retains its registered material: " + definition.id());
            int[] requirements = switch (definition.family()) {
                case SWORD, DAGGER, KNIFE, CLUB, CUDGEL, BATTLE_AXE, WAR_HAMMER -> WEAPON;
                default -> TOOL;
            };
            for (int index = 0; index < LEVELS.length; index++)
                assertRequirementAndTooltip(helper, stack, LEVELS[index], requirements[index], definition.id());
            checked++;
        }
        helper.assertValueEqual(checked, 120, "The complete matrix includes all 107 legacy tools and 13 vibranium tools");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyRegisteredLegacyAndVibraniumArmorHasExactRequirementsAndTooltip(GameTestHelper helper) {
        int checked = 0;
        for (var definition : MiteArmorContent.definitions()) {
            if (definition.horse()) continue;
            ItemStack stack = registered(helper, definition.id());
            helper.assertValueEqual(GearProgression.material(stack), definition.material(),
                    "The actual armor retains its registered material: " + definition.id());
            helper.assertTrue(stack.getItem() instanceof ArmorItem armor && armor.getType() == definition.type(),
                    "The registered armor uses its declared helmet/chest/legs/boots slot: " + definition.id());
            int[] requirements = armorRequirements(definition.type());
            for (int index = 0; index < LEVELS.length; index++)
                assertRequirementAndTooltip(helper, stack, LEVELS[index], requirements[index], definition.id());
            checked++;
        }
        // Native leather is outside the added-metal catalog and shares its slot's material-independent curve.
        String[] leather = {"minecraft:leather_helmet", "minecraft:leather_chestplate",
                "minecraft:leather_leggings", "minecraft:leather_boots"};
        for (int slot = 0; slot < leather.length; slot++) {
            ItemStack stack = registered(helper, leather[slot]);
            int[] requirements = armorRequirements(((ArmorItem) stack.getItem()).getType());
            for (int index = 0; index < LEVELS.length; index++)
                assertRequirementAndTooltip(helper, stack, LEVELS[index], requirements[index], leather[slot]);
            checked++;
        }
        helper.assertValueEqual(checked, 68, "The complete matrix includes 60 legacy metal pieces, four vibranium pieces and four native leather pieces");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rangedWeaponsModernWeaponsAndAllShieldsHaveExactRequirementsAndTooltip(GameTestHelper helper) {
        for (Item item : new Item[]{Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE})
            assertMatrix(helper, new ItemStack(item), WEAPON);
        int reinforcedBows = 0;
        for (Item item : BuiltInRegistries.ITEM) if (item instanceof R196ReinforcedBowItem) {
            assertMatrix(helper, new ItemStack(item), WEAPON);
            reinforcedBows++;
        }
        helper.assertValueEqual(reinforcedBows, 3, "All three registered reinforced bows use the weapon curve");
        int shields = 0;
        for (var definition : MiteShields.definitions()) {
            ItemStack stack = registered(helper, definition.id());
            helper.assertValueEqual(GearProgression.material(stack), definition.material(),
                    "The shield keeps its registered material: " + definition.id());
            assertMatrix(helper, stack, TOOL);
            shields++;
        }
        helper.assertValueEqual(shields, 9, "Every source-metal and vibranium shield uses the tool curve");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyLoadedSimplySwordsWeaponUsesTheWeaponCurveIncludingAxesAndScythes(GameTestHelper helper) {
        int checked = 0, retired = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (SimplySwordsCompat.definition(stack) == null) continue;
            if (RemovedDiamondEquipment.isRemoved(stack)) {
                helper.assertValueEqual(GearProgression.maxLevel(stack), 0,
                        "Retired weapons cannot participate in available equipment growth: " + BuiltInRegistries.ITEM.getKey(item));
                retired++;
                continue;
            }
            assertMatrix(helper, stack, WEAPON);
            checked++;
        }
        helper.assertValueEqual(retired, SimplySwordsCompat.enabled() ? 15 : 0,
                "All fifteen retired native netherite families are excluded from the available weapon matrix");
        helper.assertTrue(SimplySwordsCompat.enabled() ? checked > 0 : checked == 0,
                "The optional matrix checks every live compatibility definition and stays empty when the mod is absent");
        helper.succeed();
    }

    private static int[] armorRequirements(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> HELMET;
            case CHESTPLATE -> CHESTPLATE;
            case LEGGINGS -> LEGGINGS;
            case BOOTS -> BOOTS;
            default -> throw new IllegalArgumentException("Not a wearable growth slot: " + type);
        };
    }

    private static void assertMatrix(GameTestHelper helper, ItemStack stack, int[] requirements) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (int index = 0; index < LEVELS.length; index++)
            assertRequirementAndTooltip(helper, stack, LEVELS[index], requirements[index], id);
    }

    private static ItemStack registered(GameTestHelper helper, String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(key), "The equipment exists in the actual item registry: " + id);
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private static void assertRequirementAndTooltip(GameTestHelper helper, ItemStack stack, int level, int expectedPoints, String id) {
        String context = id + " at current level " + level;
        helper.assertValueEqual(GearProgression.maxLevel(stack), 25, "Standalone equipment retains the requested growth cap: " + context);
        helper.assertValueEqual(GearProgression.nextLevelExperienceMillis(stack, level), expectedPoints * 1000L,
                "The actual equipment uses its approved category threshold without a material multiplier: " + context);
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(IDENTITY, level, 0L, Map.of(), 1));
        ItemStack before = stack.copy();
        var lines = new ArrayList<Component>();
        GearProgressionTooltips.append(new ItemTooltipEvent(stack, null, lines, TooltipFlag.NORMAL, Item.TooltipContext.of(helper.getLevel())));
        var experienceLines = lines.stream().map(Component::getContents)
                .filter(contents -> contents instanceof TranslatableContents translated
                        && translated.getKey().equals("tooltip.mite.gear_experience"))
                .map(contents -> (TranslatableContents)contents).toList();
        helper.assertValueEqual(experienceLines.size(), 1, "Normal hovering exposes one experience requirement: " + context);
        Object[] arguments = experienceLines.getFirst().getArgs();
        helper.assertTrue(arguments.length == 2 && "0".equals(arguments[0])
                        && Integer.toString(expectedPoints).equals(arguments[1]),
                "The real tooltip displays 0 / " + expectedPoints + " experience: " + context);
        helper.assertTrue(ItemStack.matches(stack, before), "Inspecting the threshold cannot mutate the equipment: " + context);
    }
}
