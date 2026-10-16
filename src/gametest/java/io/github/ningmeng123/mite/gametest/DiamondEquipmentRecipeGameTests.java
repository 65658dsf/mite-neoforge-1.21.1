package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded resources exclude retired equipment without cutting off modern smithing. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DiamondEquipmentRecipeGameTests {
    private static final List<String> FAMILIES = List.of(
            "sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots");

    private DiamondEquipmentRecipeGameTests() {}

    @GameTest(template = "empty")
    public static void loadedRecipesCannotProduceDiamondEquipment(GameTestHelper helper) {
        for (String family : FAMILIES) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id("minecraft:diamond_" + family)).isEmpty(),
                    "Retired native crafting recipe is disabled: " + family);
        }
        for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
            ItemStack output = holder.value().getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(!retired(output.getItem()), "No loaded recipe produces retired equipment: " + holder.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allNineNetheriteUpgradesAcceptAncientMetalAndKeepBaseComponents(GameTestHelper helper) {
        for (String family : FAMILIES) {
            SmithingTransformRecipe recipe = recipe(helper, family);
            ItemStack base = new ItemStack(item("mite:ancient_metal_" + family));
            Component customName = Component.literal("AncientMetal upgrade " + family);
            base.set(DataComponents.CUSTOM_NAME, customName);
            base.setDamageValue(17);
            SmithingRecipeInput input = input(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), base,
                    new ItemStack(Items.NETHERITE_INGOT));
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "Matching ancient-metal upgrade exists: " + family);
            ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(item("minecraft:netherite_" + family)) && result.getCount() == 1,
                    "Upgrade yields exactly one corresponding netherite item: " + family);
            helper.assertValueEqual(result.get(DataComponents.CUSTOM_NAME), customName, "Native smithing preserves names");
            helper.assertValueEqual(result.getDamageValue(), 17, "Native smithing preserves existing damage");
            helper.assertTrue(base.is(item("mite:ancient_metal_" + family)), "Recipe preview does not mutate the base stack");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradesStillRequireTheTemplateAndIngotAndRejectDiamondBases(GameTestHelper helper) {
        for (String family : FAMILIES) {
            SmithingTransformRecipe recipe = recipe(helper, family);
            ItemStack template = new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
            ItemStack base = new ItemStack(item("mite:ancient_metal_" + family));
            ItemStack ingot = new ItemStack(Items.NETHERITE_INGOT);
            helper.assertTrue(!recipe.matches(input(ItemStack.EMPTY, base, ingot), helper.getLevel()), "Template remains required");
            helper.assertTrue(!recipe.matches(input(template, base, ItemStack.EMPTY), helper.getLevel()), "Ingot remains required");
            helper.assertTrue(!recipe.matches(input(template, base, new ItemStack(Items.NETHERITE_SCRAP)), helper.getLevel()),
                    "Scrap cannot replace the native ingot");
            helper.assertTrue(!recipe.matches(input(template, new ItemStack(item("minecraft:diamond_" + family)), ingot), helper.getLevel()),
                    "Retired diamond bases cannot upgrade: " + family);
            helper.assertTrue(!recipe.matches(input(template, new ItemStack(item("mite:adamantium_" + family)), ingot), helper.getLevel()),
                    "The replacement ingredient remains exactly ancient metal: " + family);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void retiredEquipmentIsExcludedFromSourceAndNativeEquipmentTags(GameTestHelper helper) {
        var tags = List.of(ItemTags.SWORDS, ItemTags.AXES, ItemTags.PICKAXES, ItemTags.SHOVELS, ItemTags.HOES,
                ItemTags.HEAD_ARMOR, ItemTags.CHEST_ARMOR, ItemTags.LEG_ARMOR, ItemTags.FOOT_ARMOR,
                ItemTags.TRIMMABLE_ARMOR, ItemTags.HEAD_ARMOR_ENCHANTABLE, ItemTags.CHEST_ARMOR_ENCHANTABLE,
                ItemTags.LEG_ARMOR_ENCHANTABLE, ItemTags.FOOT_ARMOR_ENCHANTABLE, ItemTags.ARMOR_ENCHANTABLE,
                ItemTags.SWORD_ENCHANTABLE, ItemTags.FIRE_ASPECT_ENCHANTABLE, ItemTags.SHARP_WEAPON_ENCHANTABLE,
                ItemTags.WEAPON_ENCHANTABLE, ItemTags.MINING_ENCHANTABLE, ItemTags.MINING_LOOT_ENCHANTABLE,
                ItemTags.DURABILITY_ENCHANTABLE, ItemTags.EQUIPPABLE_ENCHANTABLE, ItemTags.VANISHING_ENCHANTABLE);
        for (String family : List.of("sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots", "horse_armor")) {
            ItemStack diamond = new ItemStack(item("minecraft:diamond_" + family));
            for (var tag : tags) helper.assertTrue(!diamond.is(tag), "Retired diamond " + family + " absent from " + tag.location());
            for (var definition : R196EnchantmentCatalog.definitions()) {
                var tag = TagKey.create(Registries.ITEM, id("mite:enchantable/" + definition.id()));
                helper.assertTrue(!diamond.is(tag), "Retired diamond " + family + " absent from source support " + definition.id());
            }
        }
        helper.assertTrue(new ItemStack(Items.NETHERITE_PICKAXE).is(ItemTags.PICKAXES), "Native netherite tool classification survives");
        helper.assertTrue(new ItemStack(Items.NETHERITE_CHESTPLATE).is(ItemTags.TRIMMABLE_ARMOR), "Native netherite trim support survives");
        helper.assertTrue(new ItemStack(item("mite:ancient_metal_pickaxe")).is(ItemTags.PICKAXES), "Ancient-metal tool classification survives");
        helper.assertTrue(new ItemStack(item("mite:ancient_metal_chestplate")).is(ItemTags.TRIMMABLE_ARMOR), "Ancient-metal armor trim support survives");
        helper.succeed();
    }

    private static SmithingTransformRecipe recipe(GameTestHelper helper, String family) {
        var holder = helper.getLevel().getRecipeManager().byKey(id("minecraft:netherite_" + family + "_smithing"))
                .orElseThrow(() -> new IllegalStateException("Missing ancient_metal-to-netherite upgrade: " + family));
        helper.assertTrue(holder.value() instanceof SmithingTransformRecipe, "Native smithing serializer is retained");
        return (SmithingTransformRecipe) holder.value();
    }

    private static SmithingRecipeInput input(ItemStack template, ItemStack base, ItemStack addition) {
        return new SmithingRecipeInput(template, base, addition);
    }

    private static boolean retired(Item item) {
        String key = BuiltInRegistries.ITEM.getKey(item).toString();
        return key.equals("minecraft:diamond_horse_armor") || FAMILIES.stream().anyMatch(family -> key.equals("minecraft:diamond_" + family));
    }

    private static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    private static Item item(String id) { return BuiltInRegistries.ITEM.get(id(id)); }
}
