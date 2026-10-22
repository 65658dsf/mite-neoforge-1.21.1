package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.crafting.VibraniumUpgradeRecipe;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Copper equipment replaces one handle component without discounting the full iron head. @author NingMeng123 */
public final class CopperEquipmentUpgradeRecipes {
    private CopperEquipmentUpgradeRecipes() {}

    static Map<Item, ShapedRecipe> ironShapes(RecipeOutput output) {
        Map<Item, ShapedRecipe> recipes = new LinkedHashMap<>();
        ToolRecipes.addRecipes(new RecipeOutput() {
            @Override public Advancement.Builder advancement() { return output.advancement(); }
            @Override public void accept(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement, ICondition... conditions) {
                if (recipe instanceof ShapedRecipe shaped) recipes.put(shaped.getResultItem(null).getItem(), shaped);
            }
        });
        Map<Item, List<String>> armor = Map.of(Items.IRON_HELMET, List.of("III", "I I"),
                Items.IRON_CHESTPLATE, List.of("I I", "III", "III"),
                Items.IRON_LEGGINGS, List.of("III", "I I", "I I"), Items.IRON_BOOTS, List.of("I I", "I I"));
        armor.forEach((item, pattern) -> recipes.put(item, new ShapedRecipe("", CraftingBookCategory.EQUIPMENT,
                ShapedRecipePattern.of(Map.of('I', Ingredient.of(Items.IRON_INGOT)), pattern), new ItemStack(item))));
        return recipes;
    }

    static void add(RecipeOutput output, MaterialUpgradeRecipes.Upgrade route, Map<Item, ShapedRecipe> originals) {
        ShapedRecipe original = Objects.requireNonNull(originals.get(route.result()), "Missing original iron equipment recipe: " + route.id());
        VibraniumUpgradeRecipe recipe = create(original, route.base(), route.result());
        ResourceLocation id = ResourceLocation.parse(route.id());
        var advancement = output.advancement().addCriterion("has_copper_equipment", InventoryChangeTrigger.TriggerInstance.hasItems(route.base()))
                .rewards(AdvancementRewards.Builder.recipe(id));
        output.accept(id, recipe, advancement.build(id.withPrefix("recipes/combat/")));
    }

    public static VibraniumUpgradeRecipe create(ShapedRecipe original, Item base, Item result) {
        List<Ingredient> ingredients = new ArrayList<>(original.getIngredients());
        int sourceSlot = -1;
        for (int slot = 0; slot < ingredients.size(); slot++) {
            if (ingredients.get(slot).test(new ItemStack(Items.STICK))) {
                sourceSlot = slot;
                break;
            }
        }
        if (sourceSlot < 0) for (int slot = 0; slot < ingredients.size(); slot++) {
            if (ingredients.get(slot).isEmpty()) {
                sourceSlot = slot;
                break;
            }
        }
        if (sourceSlot < 0) throw new IllegalStateException("No handle or empty slot for copper equipment");
        ingredients.set(sourceSlot, Ingredient.of(base));
        Map<Character, Ingredient> keys = new LinkedHashMap<>();
        List<String> pattern = new ArrayList<>();
        for (int row = 0; row < original.getHeight(); row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < original.getWidth(); column++) {
                int slot = row * original.getWidth() + column;
                Ingredient ingredient = ingredients.get(slot);
                char symbol = ingredient.isEmpty() ? ' ' : (char) ('A' + slot);
                line.append(symbol);
                if (!ingredient.isEmpty()) keys.put(symbol, ingredient);
            }
            pattern.add(line.toString());
        }
        return new VibraniumUpgradeRecipe(new ShapedRecipe(original.getGroup(), original.category(),
                ShapedRecipePattern.of(keys, pattern), new ItemStack(result)));
    }

    static JsonObject optionalRecipe(MaterialUpgradeRecipes.OptionalUpgrade route) {
        String path = "/data/mite/recipe/" + route.base().getPath() + ".json";
        try (var reader = new InputStreamReader(Objects.requireNonNull(CopperEquipmentUpgradeRecipes.class.getResourceAsStream(path)),
                StandardCharsets.UTF_8)) {
            JsonObject recipe = JsonParser.parseReader(reader).getAsJsonObject();
            recipe.addProperty("type", "mite:vibranium_upgrade");
            JsonObject keys = recipe.getAsJsonObject("key");
            Character stick = null;
            for (var entry : keys.entrySet()) {
                JsonObject ingredient = entry.getValue().getAsJsonObject();
                if (ingredient.has("item")) {
                    String item = ingredient.get("item").getAsString();
                    if (item.equals("minecraft:copper_ingot")) ingredient.addProperty("item", "minecraft:iron_ingot");
                    else if (item.equals("mite:copper_nugget")) ingredient.addProperty("item", "minecraft:iron_nugget");
                    else if (item.equals("minecraft:stick")) stick = entry.getKey().charAt(0);
                } else if (ingredient.has("tag") && ingredient.get("tag").getAsString().equals("c:wood_sticks"))
                    stick = entry.getKey().charAt(0);
            }
            if (stick == null) throw new IllegalStateException("Missing weapon handle: " + route.id());
            JsonArray pattern = recipe.getAsJsonArray("pattern");
            boolean replaced = false;
            boolean remainingStick = false;
            for (int row = 0; row < pattern.size(); row++) {
                String line = pattern.get(row).getAsString();
                if (!replaced && line.indexOf(stick) >= 0) {
                    int column = line.indexOf(stick);
                    line = line.substring(0, column) + 'U' + line.substring(column + 1);
                    pattern.set(row, new com.google.gson.JsonPrimitive(line));
                    replaced = true;
                }
                remainingStick |= line.indexOf(stick) >= 0;
            }
            if (!replaced) throw new IllegalStateException("Missing weapon handle position: " + route.id());
            if (!remainingStick) keys.remove(stick.toString());
            JsonObject source = new JsonObject();
            source.addProperty("item", route.base().toString());
            keys.add("U", source);
            recipe.getAsJsonObject("result").addProperty("id", route.result().toString());
            return recipe;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot generate copper weapon upgrade: " + route.id(), exception);
        }
    }
}
