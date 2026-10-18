package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Single-step material routes, preserving the original equipment family. @author NingMeng123 */
public final class MaterialUpgradeRecipes {
    private static final Map<String, String> NEXT_MATERIAL = Map.of(
            "copper", "iron", "silver", "iron", "rusted_iron", "iron", "gold", "iron",
            "iron", "ancient_metal", "ancient_metal", "mithril", "mithril", "adamantium", "adamantium", "vibranium");

    public record Upgrade(String id, String fromMaterial, String toMaterial, Item base, Item addition, Item result) {}
    public record OptionalUpgrade(String id, String fromMaterial, String toMaterial,
            ResourceLocation base, ResourceLocation addition, ResourceLocation result) {}
    private record Equipment(String family, String material, Item item) {}

    private MaterialUpgradeRecipes() {}

    public static String nextMaterial(String material) { return NEXT_MATERIAL.get(material); }

    /** Only registered items enter this runtime catalog; optional IDs never resolve to air. */
    public static List<Upgrade> upgrades() {
        List<Equipment> equipment = new ArrayList<>();
        for (var definition : MiteTools.definitions())
            equipment.add(new Equipment("tool:" + definition.family(), definition.material(), required(definition.id())));
        for (var definition : MiteArmorContent.definitions())
            equipment.add(new Equipment("armor:" + definition.type() + ":" + definition.chainmail() + ":" + definition.horse(),
                    definition.material(), required(definition.id())));
        for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS))
            equipment.add(new Equipment("armor:" + type + ":false:false", "iron", required("minecraft:iron_" + type.getName())));
        equipment.add(new Equipment("armor:BODY:false:true", "iron", Items.IRON_HORSE_ARMOR));
        equipment.add(new Equipment("armor:BODY:false:true", "gold", Items.GOLDEN_HORSE_ARMOR));
        for (var definition : MiteShields.definitions())
            equipment.add(new Equipment("shield", definition.material(), required(definition.id())));
        equipment.add(new Equipment("reinforced_bow", "ancient_metal", MiteWeaponContent.ANCIENT_METAL_BOW.get()));
        equipment.add(new Equipment("reinforced_bow", "mithril", MiteWeaponContent.MITHRIL_BOW.get()));
        equipment.add(new Equipment("reinforced_bow", "vibranium", MiteWeaponContent.VIBRANIUM_BOW.get()));
        for (Material material : MiteRangedItems.rods()) {
            equipment.add(new Equipment("fishing_rod", material.id, MiteRangedItems.rod(material)));
            equipment.add(new Equipment("carrot_rod", material.id, MiteRangedItems.carrotRod(material)));
        }

        Map<String, Equipment> byFamilyAndMaterial = new LinkedHashMap<>();
        for (Equipment entry : equipment) byFamilyAndMaterial.put(entry.family() + ":" + entry.material(), entry);
        List<Upgrade> result = new ArrayList<>();
        for (Equipment entry : byFamilyAndMaterial.values()) {
            String next = nextMaterial(entry.material());
            Equipment target = next == null ? null : byFamilyAndMaterial.get(entry.family() + ":" + next);
            if (target == null) continue;
            result.add(new Upgrade(recipeId(BuiltInRegistries.ITEM.getKey(entry.item()), next), entry.material(), next,
                    entry.item(), ingot(next), target.item()));
        }
        if (SimplySwordsCompat.enabled()) for (OptionalUpgrade entry : optionalUpgrades())
            result.add(new Upgrade(entry.id(), entry.fromMaterial(), entry.toMaterial(),
                    required(entry.base().toString()), required(entry.addition().toString()), required(entry.result().toString())));
        return List.copyOf(result);
    }

    /** Stable optional-mod routes are generated with a mod-loaded condition even during vanilla data generation. */
    public static List<OptionalUpgrade> optionalUpgrades() {
        List<OptionalUpgrade> result = new ArrayList<>();
        for (String family : SimplySwordsCompat.families()) {
            for (String material : List.of("copper", "silver", "rusted_iron", "gold", "iron", "ancient_metal", "mithril", "adamantium")) {
                String next = nextMaterial(material);
                ResourceLocation base = simplySwordsId(material, family);
                result.add(new OptionalUpgrade(recipeId(base, next), material, next, base, ingotId(next), simplySwordsId(next, family)));
            }
        }
        return List.copyOf(result);
    }

    public static void addRecipes(RecipeOutput output) {
        Item template = MiteUpgradeTemplates.UPGRADE_TEMPLATE.get();
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, template)
                .requires(Items.PAPER).requires(Items.STONE).requires(Items.IRON_INGOT).requires(Items.DIAMOND)
                .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                .save(output, ResourceLocation.parse("mite:equipment_upgrade_smithing_template"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, template, 2)
                .pattern("ITI").pattern("ISI").pattern("III")
                .define('I', Items.IRON_INGOT).define('T', template).define('S', Items.STONE)
                .unlockedBy("has_upgrade_template", InventoryChangeTrigger.TriggerInstance.hasItems(template))
                .save(output, ResourceLocation.parse("mite:equipment_upgrade_smithing_template_duplication"));
        for (Upgrade entry : upgrades()) {
            // Optional routes are emitted by the assets provider so runData never depends on the optional mod.
            if (entry.id().contains("/simplyswords_") || BuiltInRegistries.ITEM.getKey(entry.base()).getNamespace().equals(SimplySwordsCompat.MOD_ID)) continue;
            SmithingTransformRecipeBuilder.smithing(Ingredient.of(template), Ingredient.of(entry.base()),
                            Ingredient.of(entry.addition()), RecipeCategory.COMBAT, entry.result())
                    .unlocks("has_upgrade_template", InventoryChangeTrigger.TriggerInstance.hasItems(template))
                    .save(output, ResourceLocation.parse(entry.id()));
        }
    }

    private static ResourceLocation simplySwordsId(String material, String family) {
        return material.equals("iron") || material.equals("gold")
                ? ResourceLocation.fromNamespaceAndPath(SimplySwordsCompat.MOD_ID, material + "_" + family)
                : ResourceLocation.fromNamespaceAndPath("mite", SimplySwordsCompat.path(material, family));
    }

    private static String recipeId(ResourceLocation base, String material) {
        String path = base.getNamespace().equals(SimplySwordsCompat.MOD_ID) ? "simplyswords_" + base.getPath() : base.getPath();
        return "mite:equipment_upgrade/" + path + "_to_" + material;
    }

    private static ResourceLocation ingotId(String material) {
        return ResourceLocation.fromNamespaceAndPath(List.of("copper", "iron", "gold").contains(material) ? "minecraft" : "mite", material + "_ingot");
    }

    private static Item ingot(String material) { return required(ingotId(material).toString()); }

    private static Item required(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).filter(item -> item != Items.AIR)
                .orElseThrow(() -> new IllegalStateException("Missing material-upgrade item: " + id));
    }
}
