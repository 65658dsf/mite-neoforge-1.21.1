package io.github.ningmeng123.mite.furnace;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.item.R196RepairMaterial;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;

/** Material-driven equipment recycling and wooden-body fuel classification. @author NingMeng123 */
public final class EquipmentRecycling {
    private static final List<String> METALS = List.of("copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium");

    private EquipmentRecycling() {}

    public static List<String> metals() { return METALS; }

    /** Returns the recoverable metal, independently of durability, quality and enchantments. */
    public static String material(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var simply = SimplySwordsCompat.definition(stack);
        if (simply != null) return canonical(simply.material());
        // Unknown/runic/unique Simply Swords tiers must not inherit an incidental iron repair fallback.
        if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(SimplySwordsCompat.MOD_ID)) return null;
        var armor = MiteArmorContent.definition(stack.getItem());
        if (armor != null) return canonical(armor.material());
        var carrotRod = MiteRangedItems.carrotRodMaterial(stack);
        if (carrotRod != null) return canonical(carrotRod.id);
        var source = MaterialAnvilRules.material(stack);
        if (source != null) return canonical(source.name());
        if (stack.getItem() instanceof TieredItem tool) {
            if (tool.getTier() == Tiers.WOOD || tool.getTier() == Tiers.STONE
                    || tool.getTier() == Tiers.DIAMOND || tool.getTier() == Tiers.NETHERITE) return null;
            return repairMetal(tool.getTier().getRepairIngredient());
        }
        if (stack.getItem() instanceof ArmorItem modernArmor) {
            if (modernArmor.getMaterial().equals(ArmorMaterials.DIAMOND)
                    || modernArmor.getMaterial().equals(ArmorMaterials.NETHERITE)) return null;
            return repairMetal(modernArmor.getMaterial().value().repairIngredient().get());
        }
        return null;
    }

    public static Item nugget(String metal) {
        if (metal == null) return null;
        return switch (metal) {
            case "copper" -> MiteMaterials.COPPER_NUGGET.get();
            case "silver" -> MiteMaterials.SILVER_NUGGET.get();
            case "gold" -> Items.GOLD_NUGGET;
            case "iron", "rusted_iron" -> Items.IRON_NUGGET;
            case "ancient_metal" -> MiteMaterials.ANCIENT_METAL_NUGGET.get();
            case "mithril" -> MiteMaterials.MITHRIL_NUGGET.get();
            case "adamantium" -> MiteMaterials.ADAMANTIUM_NUGGET.get();
            default -> null;
        };
    }

    /** Reinforcements and metal hooks can be recovered, while bows and rods retain their wooden-body fuel behavior. */
    public static boolean isNonWoodEquipment(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        if (item instanceof BowItem || item instanceof FishingRodItem || MiteRangedItems.carrotRodMaterial(stack) != null) return false;
        var simply = SimplySwordsCompat.definition(stack);
        if (simply != null) return !simply.material().equals("wood");
        var tool = MiteTools.definition(stack);
        if (tool != null) return !tool.material().equals("wood");
        if (MiteArmorContent.definition(item) != null || MiteShields.definition(stack) != null
                || item instanceof ArmorItem || item instanceof ShearsItem) return true;
        if (item instanceof TieredItem tiered)
            return tiered.getTier() != Tiers.WOOD && !woodenRepair(tiered.getTier().getRepairIngredient());
        return item instanceof R196RepairMaterial repair && !"wood".equals(repair.repairMaterial());
    }

    private static String canonical(String material) {
        if (material == null) return null;
        return "rusted_iron".equals(material) ? "iron" : METALS.contains(material) ? material : null;
    }

    private static String repairMetal(Ingredient ingredient) {
        String resolved = null;
        for (ItemStack candidate : liveRepairItems(ingredient)) {
            String candidateMetal = null;
            for (String metal : METALS) {
                if (!repairResource(candidate, metal)) continue;
                if (candidateMetal != null) return null;
                candidateMetal = metal;
            }
            // Every accepted repair resource must agree; mixed or unknown materials fail closed.
            if (candidateMetal == null || resolved != null && !resolved.equals(candidateMetal)) return null;
            resolved = candidateMetal;
        }
        return resolved;
    }

    private static boolean repairResource(ItemStack candidate, String metal) {
        return candidate.is(nugget(metal)) || candidate.is(ingot(metal))
                || candidate.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/" + metal)))
                || candidate.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "nuggets/" + metal)));
    }

    private static Item ingot(String metal) {
        return switch (metal) {
            case "copper" -> Items.COPPER_INGOT;
            case "silver" -> MiteMaterials.SILVER_INGOT.get();
            case "gold" -> Items.GOLD_INGOT;
            case "iron" -> Items.IRON_INGOT;
            case "ancient_metal" -> MiteMaterials.ANCIENT_METAL_INGOT.get();
            case "mithril" -> MiteMaterials.MITHRIL_INGOT.get();
            case "adamantium" -> MiteMaterials.ADAMANTIUM_INGOT.get();
            default -> throw new IllegalArgumentException("Unknown equipment metal: " + metal);
        };
    }

    private static boolean woodenRepair(Ingredient ingredient) {
        ItemStack[] candidates = liveRepairItems(ingredient);
        if (candidates.length == 0) return false;
        for (ItemStack candidate : candidates)
            if (!candidate.is(ItemTags.PLANKS) && !candidate.is(ItemTags.LOGS)) return false;
        return true;
    }

    /** Persistent tier/material ingredients must resolve tag membership again after a data reload. */
    private static ItemStack[] liveRepairItems(Ingredient ingredient) {
        var custom = ingredient.getCustomIngredient();
        return (custom == null
                ? Arrays.stream(ingredient.getValues()).flatMap(value -> value.getItems().stream())
                : custom.getItems()).toArray(ItemStack[]::new);
    }
}
