package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteTools;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Endgame alloy manufacturing, without changing the frozen R196 recipe evidence. @author NingMeng123 */
public final class VibraniumCrafting {
    private static final float INGOT_DIFFICULTY = 51200F;
    private static final java.util.Map<String, Float> UPGRADES = upgrades();
    private VibraniumCrafting() {}

    private static java.util.Map<String, Float> upgrades() {
        try (var reader = new java.io.InputStreamReader(java.util.Objects.requireNonNull(VibraniumCrafting.class.getResourceAsStream("/mite/vibranium-upgrades.json")), java.nio.charset.StandardCharsets.UTF_8)) {
            java.util.Map<String, Float> values = new java.util.HashMap<>();
            com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("items").entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsJsonObject().get("lowest_difficulty").getAsFloat()));
            return java.util.Map.copyOf(values);
        } catch (Exception exception) { throw new IllegalStateException("Cannot load vibranium upgrade recipes", exception); }
    }

    /** Negative means the existing equipment/workstation rules decide. */
    public static float required(ItemStack result) {
        return switch (BuiltInRegistries.ITEM.getKey(result.getItem()).toString()) {
            // The first alloy and its bench must be obtainable from the preceding tier.
            case "mite:vibranium_ingot", "mite:vibranium_crafting_table" -> 256F;
            case "mite:vibranium_block", "mite:vibranium_anvil" -> 512F;
            default -> -1F;
        };
    }

    public static float lowestDifficulty(ItemStack stack) {
        Float upgrade = UPGRADES.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (upgrade != null) return upgrade;
        var tool = MiteTools.definition(stack);
        if (tool != null && tool.material().equals("vibranium")) {
            int sticks = switch (tool.family()) {
                case SHEARS -> 0;
                case SWORD, DAGGER, KNIFE -> 1;
                case SCYTHE -> 3;
                default -> 2;
            };
            return tool.family().components() * INGOT_DIFFICULTY + sticks * 25F;
        }
        var armor = MiteArmorContent.definition(stack.getItem());
        if (armor != null && armor.material().equals("vibranium")) return armor.components() * INGOT_DIFFICULTY;
        return -1F;
    }

    public static TimedCraftingRecipes.Rule rule(ResourceLocation id, ItemStack output) {
        if (!id.getNamespace().equals("mite")) return null;
        String path = id.getPath();
        float difficulty = lowestDifficulty(output);
        if (difficulty >= 0F) return new TimedCraftingRecipes.Rule(difficulty, "vibranium", -1);
        return switch (path) {
            case "obsidian_stick" -> new TimedCraftingRecipes.Rule(520F, null, -1);
            case "vibranium_ingot" -> new TimedCraftingRecipes.Rule(4F * 25600F + 4F * 6400F / 9F + 500F, "adamantium", -1);
            case "vibranium_ingot_from_nuggets" -> new TimedCraftingRecipes.Rule(INGOT_DIFFICULTY, "adamantium", -1);
            case "vibranium_nuggets_from_ingot" -> new TimedCraftingRecipes.Rule(INGOT_DIFFICULTY, null, -1);
            case "vibranium_crafting_table" -> new TimedCraftingRecipes.Rule(INGOT_DIFFICULTY + 205F, "adamantium", -1);
            case "vibranium_block" -> new TimedCraftingRecipes.Rule(9F * INGOT_DIFFICULTY, "vibranium", -1);
            case "vibranium_ingots_from_block" -> new TimedCraftingRecipes.Rule(9F * INGOT_DIFFICULTY, "adamantium", -1);
            case "vibranium_anvil" -> new TimedCraftingRecipes.Rule(31F * INGOT_DIFFICULTY, "vibranium", -1);
            default -> null;
        };
    }
}
