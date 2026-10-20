package io.github.ningmeng123.mite.gametest;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Set;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native progression remains attainable after its retired equipment is removed. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DiamondEquipmentAdvancementGameTests {
    private DiamondEquipmentAdvancementGameTests() {}

    @GameTest(template = "empty")
    public static void nativeEquipmentAdvancementsUseAncientMetalAndPreserveSavedCriteria(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var shiny = server.getAdvancements().get(id("story/shiny_gear")).value();
        helper.assertValueEqual(shiny.parent().orElseThrow(), id("story/mine_diamond"), "Native parent and advancement identity remain valid");
        helper.assertValueEqual(shiny.criteria().keySet(), Set.of("diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"),
                "Old completed criterion names remain compatible");
        for (String family : List.of("helmet", "chestplate", "leggings", "boots")) {
            var instance = shiny.criteria().get("diamond_" + family).triggerInstance();
            helper.assertTrue(instance instanceof InventoryChangeTrigger.TriggerInstance, "Native inventory trigger remains unchanged");
            var trigger = (InventoryChangeTrigger.TriggerInstance) instance;
            helper.assertTrue(trigger.items().get(0).test(stack("mite:ancient_metal_" + family)), "Ancient-metal armor satisfies its native criterion");
            helper.assertTrue(!trigger.items().get(0).test(stack("minecraft:diamond_" + family)), "Retired armor no longer satisfies the adapted criterion");
        }
        helper.assertTrue(shiny.display().orElseThrow().getIcon().is(stack("mite:ancient_metal_chestplate").getItem()), "Native shiny-gear icon uses ancient metal");
        helper.assertValueEqual(shiny.display().orElseThrow().getDescription().getString(), "获得一件远古金属盔甲", "Description states the obtainable equipment requirement");
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (String path : List.of("story/shiny_gear", "adventure/adventuring_time", "adventure/kill_all_mobs",
                "adventure/lighten_up", "husbandry/wax_off", "husbandry/obtain_netherite_hoe",
                "nether/explore_nether", "nether/netherite_armor")) {
            var holder = server.getAdvancements().get(id(path));
            helper.assertTrue(holder != null, "Adapted native advancement is still loaded: " + path);
            var json = Advancement.CODEC.encodeStart(ops, holder.value()).getOrThrow();
            helper.assertTrue(!json.toString().contains("minecraft:diamond_"), "No display or item predicate references removed equipment: " + path);
            helper.assertTrue(!json.toString().contains("minecraft:netherite_"), "No display or item predicate references retired netherite: " + path);
            helper.assertTrue(holder.value().requirements().test(name -> holder.value().criteria().containsKey(name)),
                    "Every preserved requirement still names a loaded criterion: " + path);
            if (path.equals("adventure/lighten_up") || path.equals("husbandry/wax_off")) {
                helper.assertTrue(json.toString().contains("mite:ancient_metal_axe"), "Ancient-metal axes can complete native scraping actions");
                helper.assertTrue(json.toString().contains("mite:mithril_axe"), "Mithril axes replace retired netherite scraping choices");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeNetheriteAdvancementIdsKeepProgressAndRewardsWithMithrilRequirements(GameTestHelper helper) {
        var advancements = helper.getLevel().getServer().getAdvancements();
        var hoe = advancements.get(id("husbandry/obtain_netherite_hoe")).value();
        helper.assertValueEqual(hoe.criteria().keySet(), Set.of("netherite_hoe"), "Saved hoe criterion identity is retained");
        var hoeTrigger = (InventoryChangeTrigger.TriggerInstance)hoe.criteria().get("netherite_hoe").triggerInstance();
        helper.assertTrue(hoeTrigger.items().getFirst().test(stack("mite:mithril_hoe")), "Mithril hoe completes the native challenge");
        helper.assertFalse(hoeTrigger.items().getFirst().test(stack("minecraft:netherite_hoe")), "Retired hoe no longer meets the challenge");
        helper.assertValueEqual(hoe.rewards().experience(), 100, "Native hoe challenge keeps its experience reward");
        helper.assertTrue(hoe.display().orElseThrow().getIcon().is(stack("mite:mithril_hoe").getItem()), "Hoe challenge icon is obtainable");

        var armor = advancements.get(id("nether/netherite_armor")).value();
        helper.assertValueEqual(armor.criteria().keySet(), Set.of("netherite_armor"), "Saved full-armor criterion identity is retained");
        var armorTrigger = (InventoryChangeTrigger.TriggerInstance)armor.criteria().get("netherite_armor").triggerInstance();
        for (String family : List.of("helmet", "chestplate", "leggings", "boots")) {
            helper.assertTrue(armorTrigger.items().stream().anyMatch(predicate -> predicate.test(stack("mite:mithril_" + family))),
                    "Matching mithril armor is required: " + family);
            helper.assertFalse(armorTrigger.items().stream().anyMatch(predicate -> predicate.test(stack("minecraft:netherite_" + family))),
                    "Retired netherite armor is not required: " + family);
        }
        helper.assertValueEqual(armor.rewards().experience(), 100, "Native armor challenge keeps its experience reward");
        helper.assertTrue(armor.display().orElseThrow().getIcon().is(stack("mite:mithril_chestplate").getItem()), "Full-armor challenge icon is obtainable");
        helper.assertTrue(advancements.get(id("nether/explore_nether")).value().display().orElseThrow().getIcon()
                .is(stack("mite:mithril_boots").getItem()), "Nether exploration icon also uses obtainable armor");
        helper.succeed();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.withDefaultNamespace(path); }
    private static ItemStack stack(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }
}
