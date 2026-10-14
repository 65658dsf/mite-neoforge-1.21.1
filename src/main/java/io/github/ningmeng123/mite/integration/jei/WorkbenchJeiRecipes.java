package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.BulkMaterialRecipe;
import io.github.ningmeng123.mite.crafting.FoodManufacturingRecipe;
import io.github.ningmeng123.mite.crafting.FoodShapedRecipe;
import io.github.ningmeng123.mite.crafting.FoodShapelessRecipe;
import io.github.ningmeng123.mite.crafting.WorkbenchDismantleRecipe;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/** Loaded recipe snapshot filtered by the same capacity and grid gates as server manufacturing. @author NingMeng123 */
public final class WorkbenchJeiRecipes {
    public static final String INVENTORY = "minecraft:inventory";
    public record Profile(String id, float capacity, int gridSize) {}
    public record Index(List<WorkbenchJeiRecipe> recipes, List<RecipeHolder<CraftingRecipe>> originals) {
        public Index { recipes = List.copyOf(recipes); originals = List.copyOf(originals); }
    }
    private WorkbenchJeiRecipes() {}

    public static Map<String, Profile> profiles() {
        Map<String, Profile> result = new LinkedHashMap<>();
        result.put(INVENTORY, new Profile(INVENTORY, 0, 2));
        MiteWorkbenches.TABLES.forEach((material, block) -> {
            String id = block.getId().toString();
            result.put(id, new Profile(id, WorkbenchRules.materialCapacity(material), 3));
        });
        return Collections.unmodifiableMap(result);
    }

    public static List<WorkbenchJeiRecipe> build(Level level) { return index(level).recipes(); }

    public static boolean supports(CraftingRecipe recipe) {
        // Unknown subclasses can change matching/assembly and have their own JEI extensions.
        return recipe.getClass() == ShapedRecipe.class || recipe.getClass() == ShapelessRecipe.class
                || recipe instanceof FoodShapedRecipe || recipe instanceof FoodShapelessRecipe
                || recipe instanceof BulkMaterialRecipe || recipe instanceof WorkbenchDismantleRecipe
                || recipe instanceof io.github.ningmeng123.mite.crafting.RangedRodConversionRecipe
                || recipe instanceof io.github.ningmeng123.mite.crafting.UtilityShapelessRecipe
                || recipe instanceof io.github.ningmeng123.mite.crafting.StackBatchShapedRecipe
                || recipe instanceof io.github.ningmeng123.mite.crafting.StackBatchShapelessRecipe;
    }

    public static Index index(Level level) {
        List<WorkbenchJeiRecipe> pages = new ArrayList<>();
        List<RecipeHolder<CraftingRecipe>> originals = new ArrayList<>();
        List<ItemStack> milk = BuiltInRegistries.ITEM.stream().map(ItemStack::new)
                .filter(FoodManufacturingRecipe::isSourceMilkBucket).toList();
        Map<String, Profile> profiles = profiles();
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .sorted(Comparator.comparing(recipe -> recipe.id().toString())).toList()) {
            CraftingRecipe recipe = holder.value();
            // Dynamic repair/dye/map/firework recipes remain in JEI's existing extension system.
            if (!supports(recipe)) continue;
            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty()) continue;
            boolean shapeless = recipe instanceof ShapelessRecipe;
            int count = recipe.getIngredients().size();
            if (count == 0) continue;
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : Math.min(3, count);
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : (count + width - 1) / width;
            List<List<ItemStack>> slots = new ArrayList<>();
            boolean mixedMilk = recipe instanceof FoodManufacturingRecipe food && food.manufacturing().anyMilkBuckets();
            for (var ingredient : recipe.getIngredients())
                slots.add(mixedMilk ? milk : Arrays.stream(ingredient.getItems())
                        .filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList());
            if (recipe instanceof io.github.ningmeng123.mite.crafting.UtilityShapelessRecipe utility && utility.waterPotionOnly()) {
                for (int i = 0; i < slots.size(); i++) slots.set(i, slots.get(i).stream().map(stack -> {
                    if (!stack.is(net.minecraft.world.item.Items.POTION)) return stack;
                    ItemStack water = stack.copy();
                    water.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                            new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.WATER));
                    return water;
                }).toList());
            }
            while (slots.size() < width * height) slots.add(List.of());
            // An unresolved nonempty tag is not a usable recipe page, but its native view is preserved.
            boolean unresolved = false;
            for (int i = 0; i < count; i++)
                if (!recipe.getIngredients().get(i).isEmpty()
                        && (recipe.getIngredients().get(i).hasNoItems() || slots.get(i).isEmpty())) unresolved = true;
            if (unresolved) continue;
            CraftingInput example = CraftingInput.of(width, height,
                    slots.stream().map(slot -> slot.isEmpty() ? ItemStack.EMPTY : slot.getFirst().copy()).toList());
            if (!recipe.matches(example, level)) continue;
            var rule = CraftingQuality.rule(holder, output, example);
            float required = Math.max(WorkbenchRules.required(output), rule == null ? 0F : rule.requiredCapacity());
            boolean covered = false;
            for (Profile profile : profiles.values()) {
                if (profile.capacity() < required || !recipe.canCraftInDimensions(profile.gridSize(), profile.gridSize())
                        || profile.gridSize() == 2 && output.is(ItemTags.PLANKS)) continue;
                int displayWidth = shapeless ? Math.min(profile.gridSize(), count) : width;
                int displayHeight = shapeless ? (count + displayWidth - 1) / displayWidth : height;
                List<List<ItemStack>> displaySlots = new ArrayList<>(slots.subList(0, count));
                while (displaySlots.size() < displayWidth * displayHeight) displaySlots.add(List.of());
                WorkbenchJeiRecipe page = new WorkbenchJeiRecipe(profile.id(), holder, displayWidth, displayHeight,
                        displaySlots, output, rule, required, shapeless);
                // Seven separate source milk recipes accept identical mixed buckets. Show each batch only once.
                if (!mixedMilk || pages.stream().noneMatch(old -> sameMilkBatch(old, page))) pages.add(page);
                covered = true;
            }
            if (covered) originals.add(holder);
        }
        return new Index(pages, originals);
    }

    private static boolean sameMilkBatch(WorkbenchJeiRecipe first, WorkbenchJeiRecipe second) {
        return first.workstationId().equals(second.workstationId())
                && first.source().value() instanceof FoodManufacturingRecipe food && food.manufacturing().anyMilkBuckets()
                && first.width() == second.width() && first.height() == second.height()
                && first.source().value().getIngredients().size() == second.source().value().getIngredients().size()
                && first.requiredCapacity() == second.requiredCapacity()
                && first.rule().difficulty() == second.rule().difficulty()
                && ItemStack.matches(first.output(), second.output());
    }
}
