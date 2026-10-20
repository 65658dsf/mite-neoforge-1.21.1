package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded resources exclude retired diamond and netherite equipment. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DiamondEquipmentRecipeGameTests {
    private static final List<String> FAMILIES = List.of(
            "sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots");

    private DiamondEquipmentRecipeGameTests() {}

    @GameTest(template = "empty")
    public static void loadedRecipesCannotProduceRetiredEquipment(GameTestHelper helper) {
        for (String family : FAMILIES) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id("minecraft:diamond_" + family)).isEmpty(),
                    "Retired native crafting recipe is disabled: " + family);
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id("minecraft:netherite_" + family + "_smithing")).isEmpty(),
                    "Retired netherite smithing recipe is disabled: " + family);
        }
        for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
            ItemStack output = holder.value().getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(!RemovedDiamondEquipment.isRemoved(output), "No loaded recipe produces retired equipment: " + holder.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void retiredEquipmentDoesNotRetireItsRawMaterialsOrAncientMetalBases(GameTestHelper helper) {
        for (Item material : List.of(Items.DIAMOND, Items.DIAMOND_BLOCK, Items.NETHERITE_INGOT,
                Items.NETHERITE_SCRAP, Items.NETHERITE_BLOCK, Items.ANCIENT_DEBRIS, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) {
            helper.assertFalse(RemovedDiamondEquipment.isRemoved(new ItemStack(material)), "Raw material or template survives: " + material);
        }
        for (String family : FAMILIES)
            helper.assertFalse(RemovedDiamondEquipment.isRemoved(new ItemStack(item("mite:ancient_metal_" + family))),
                    "Ancient-metal equipment remains usable: " + family);
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
        for (String material : List.of("diamond", "netherite")) {
            for (String family : FAMILIES) assertExcluded(helper, new ItemStack(item("minecraft:" + material + "_" + family)), tags);
        }
        assertExcluded(helper, new ItemStack(Items.DIAMOND_HORSE_ARMOR), tags);
        if (SimplySwordsCompat.enabled()) for (String family : SimplySwordsCompat.families())
            assertExcluded(helper, new ItemStack(SimplySwordsCompat.weapon("netherite", family)), tags);
        helper.assertTrue(new ItemStack(item("mite:ancient_metal_pickaxe")).is(ItemTags.PICKAXES), "Ancient-metal tool classification survives");
        helper.assertTrue(new ItemStack(item("mite:ancient_metal_chestplate")).is(ItemTags.TRIMMABLE_ARMOR), "Ancient-metal armor trim support survives");
        helper.succeed();
    }

    private static void assertExcluded(GameTestHelper helper, ItemStack stack, List<TagKey<Item>> tags) {
        String label = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (var tag : tags) helper.assertTrue(!stack.is(tag), "Retired " + label + " absent from " + tag.location());
        for (var definition : R196EnchantmentCatalog.definitions()) {
            var tag = TagKey.create(Registries.ITEM, id("mite:enchantable/" + definition.id()));
            helper.assertTrue(!stack.is(tag), "Retired " + label + " absent from source support " + definition.id());
        }
    }

    private static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    private static Item item(String id) { return BuiltInRegistries.ITEM.get(id(id)); }
}
