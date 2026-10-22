package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.MaterialUpgradeRequirements;
import io.github.ningmeng123.mite.crafting.VibraniumUpgradeRecipe;
import io.github.ningmeng123.mite.data.MaterialUpgradeRecipes;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipes;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded material costs and copper workbench routes have no one-ingot smithing shortcut. @author NingMeng123 */
@GameTestHolder("mite_upgrade")
@PrefixGameTestTemplate(false)
public final class CopperEquipmentUpgradeGameTests {
    private CopperEquipmentUpgradeGameTests() {}

    @GameTest(template = "empty")
    public static void completeEquipmentCostsUseActualMetalRatherThanDurabilityAliases(GameTestHelper helper) {
        Map<String, Integer> costs = Map.ofEntries(
                Map.entry("mite:mithril_pickaxe", 3), Map.entry("mite:mithril_hatchet", 1),
                Map.entry("mite:mithril_war_hammer", 5), Map.entry("mite:mithril_helmet", 5),
                Map.entry("mite:mithril_chestplate", 8), Map.entry("mite:mithril_leggings", 7), Map.entry("mite:mithril_boots", 4),
                Map.entry("mite:mithril_chain_helmet", 3), Map.entry("mite:mithril_chain_chestplate", 4),
                Map.entry("mite:mithril_chain_leggings", 4), Map.entry("mite:mithril_chain_boots", 2),
                Map.entry("mite:mithril_shield", 1), Map.entry("mite:mithril_bow", 1),
                Map.entry("mite:fishing_rod_mithril_hook", 1), Map.entry("mite:carrot_on_a_stick_mithril_hook", 1),
                Map.entry("minecraft:iron_horse_armor", 1), Map.entry("mite:mithril_horse_armor", 1));
        for (var entry : costs.entrySet()) {
            ItemStack result = item(entry.getKey());
            helper.assertValueEqual(MaterialUpgradeRequirements.ingots(result), entry.getValue(), "Complete metal cost: " + entry.getKey());
            helper.assertTrue(MaterialUpgradeRequirements.difficulty(result) > 0F, "Every supported target has a positive manufacturing duration");
        }
        helper.assertTrue(MaterialUpgradeRequirements.usesTemplate(new ItemStack(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()))
                        && !MaterialUpgradeRequirements.usesTemplate(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)),
                "Only the shared material template enables full-cost material upgrading");
        if (SimplySwordsCompat.enabled()) for (var entry : Map.ofEntries(
                Map.entry("longsword", 2), Map.entry("sai", 1), Map.entry("spear", 1), Map.entry("warglaive", 3),
                Map.entry("cutlass", 3), Map.entry("claymore", 3), Map.entry("greataxe", 4), Map.entry("greathammer", 4),
                Map.entry("chakram", 4), Map.entry("scythe", 3), Map.entry("halberd", 4)).entrySet())
            helper.assertValueEqual(MaterialUpgradeRequirements.ingots(new ItemStack(SimplySwordsCompat.weapon("mithril", entry.getKey()))),
                    entry.getValue(), "Simply Swords uses its complete real ingots and nuggets: " + entry.getKey());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyCopperToolAndPlateArmorRouteLoadsOnWorkbenchAndAppearsInItsJeiIndex(GameTestHelper helper) {
        var routes = MaterialUpgradeRecipes.upgrades().stream().filter(MaterialUpgradeRecipes::isWorkbenchUpgrade).toList();
        helper.assertValueEqual(routes.size(), SimplySwordsCompat.enabled() ? 31 : 16, "Twelve tools, four plate pieces and optional fifteen weapons use workbench upgrading");
        var index = WorkbenchJeiRecipes.index(helper.getLevel());
        for (var route : routes) {
            RecipeHolder<CraftingRecipe> holder = recipe(helper, route.id());
            var shaped = (VibraniumUpgradeRecipe) holder.value();
            CraftingInput input = input(shaped);
            helper.assertTrue(shaped.matches(input, helper.getLevel()), "The loaded full recipe accepts exactly one copper source: " + route.id());
            helper.assertTrue(shaped.assemble(input, helper.getLevel().registryAccess()).is(route.result()), "The workbench produces the same-family iron equipment");
            long sources = input.items().stream().filter(stack -> stack.is(route.base())).count();
            helper.assertValueEqual(sources, 1L, "Each upgrade consumes exactly one source equipment item");
            helper.assertTrue(input.items().stream().noneMatch(stack -> stack.is(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get())), "Copper workbench routes do not require or consume a template");
            long expectedMetal = MaterialUpgradeRequirements.ingots(new ItemStack(route.result()));
            var simply = SimplySwordsCompat.definition(new ItemStack(route.result()));
            if (simply == null) helper.assertValueEqual(input.items().stream().filter(stack -> stack.is(Items.IRON_INGOT)).count(),
                    expectedMetal, "Every normal tool or plate upgrade pays the complete iron recipe cost");
            helper.assertTrue(MaterialUpgradeRecipes.smithingUpgrades().stream().noneMatch(other -> other.id().equals(route.id())), "The route cannot retain a one-ingot smithing shortcut");
            helper.assertTrue(index.recipes().stream().anyMatch(page -> page.source().id().equals(holder.id())), "A supported workbench JEI page exists for each copper upgrade");
            helper.assertValueEqual(index.originals().stream().filter(original -> original.id().equals(holder.id())).count(), 1L,
                    "The source appears once in the workbench JEI index rather than duplicating its recipe group");
            var rule = CraftingQuality.rule(holder, shaped.getResultItem(helper.getLevel().registryAccess()), input);
            helper.assertTrue(rule != null && rule.difficulty() > 0F, "Every copper workbench upgrade uses the target manufacturing duration");
        }
        RecipeHolder<CraftingRecipe> pickaxe = recipe(helper, "mite:equipment_upgrade/copper_pickaxe_to_iron");
        CraftingInput pickInput = input((VibraniumUpgradeRecipe) pickaxe.value());
        helper.assertTrue(pickInput.width() == 3 && pickInput.height() == 3
                        && pickInput.getItem(0).is(Items.IRON_INGOT) && pickInput.getItem(1).is(Items.IRON_INGOT)
                        && pickInput.getItem(2).is(Items.IRON_INGOT) && pickInput.getItem(4).is(MiteTools.get("copper_pickaxe"))
                        && pickInput.getItem(7).is(Items.STICK), "The copper pickaxe occupies the upper handle slot of the complete iron pickaxe recipe");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copperWorkbenchUpgradePreservesPersonalComponentsAndProportionalDamage(GameTestHelper helper) {
        RecipeHolder<CraftingRecipe> holder = recipe(helper, "mite:equipment_upgrade/copper_pickaxe_to_iron");
        var recipe = (VibraniumUpgradeRecipe) holder.value();
        CraftingInput input = input(recipe);
        ItemStack source = VibraniumUpgradeRecipe.source(input);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123"));
        EquipmentDurability.setQuality(source, Quality.EXCELLENT);
        source.setDamageValue(source.getMaxDamage() / 3);
        source.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 2);
        GearProgress growth = new GearProgress(UUID.randomUUID(), 3, 12_345L, Map.of(ResourceLocation.parse("missing:legacy_rank"), 17), 1);
        source.set(GearProgression.PROGRESS.get(), growth);
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(Items.IRON_PICKAXE) && result.getHoverName().getString().equals("NingMeng123")
                        && EquipmentDurability.quality(result) == Quality.EXCELLENT
                        && GearProgression.state(result).equals(growth)
                        && result.get(DataComponents.ENCHANTMENTS).equals(source.get(DataComponents.ENCHANTMENTS)),
                "The new workbench route retains name, quality, ordinary enchantments and exact historical growth");
        int expectedDamage = (int) Math.round((double) source.getDamageValue() * result.getMaxDamage() / source.getMaxDamage());
        helper.assertValueEqual(result.getDamageValue(), expectedDamage, "The upgraded tool retains proportional wear instead of repairing for free");
        helper.succeed();
    }

    private static ItemStack item(String id) {
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).filter(value -> value != Items.AIR).orElseThrow();
        return new ItemStack(item);
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<CraftingRecipe> recipe(GameTestHelper helper, String id) {
        var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow();
        helper.assertTrue(holder.value() instanceof VibraniumUpgradeRecipe && holder.value().getType() == RecipeType.CRAFTING,
                "The old route ID is replaced by a component-preserving workbench recipe: " + id);
        return (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) holder;
    }

    private static CraftingInput input(VibraniumUpgradeRecipe recipe) {
        List<ItemStack> items = new ArrayList<>();
        for (var ingredient : recipe.getIngredients()) items.add(ingredient.isEmpty() ? ItemStack.EMPTY : ingredient.getItems()[0].copy());
        return CraftingInput.of(recipe.getWidth(), recipe.getHeight(), items);
    }
}
