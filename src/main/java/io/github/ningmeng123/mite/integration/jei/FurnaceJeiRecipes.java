package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.mite.rules.R196FurnaceRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** No JEI classes: builds viewer batches using the exact same read-only plan as server cooking. @author NingMeng123 */
public final class FurnaceJeiRecipes {
    private FurnaceJeiRecipes() {}
    public static RecipeType<? extends AbstractCookingRecipe> recipeType(String furnace) {
        return furnace.equals("minecraft:smoker") ? RecipeType.SMOKING
                : furnace.equals("minecraft:blast_furnace") ? RecipeType.BLASTING : RecipeType.SMELTING;
    }
    public static List<FurnaceJeiRecipe> build(Level level) {
        List<FurnaceJeiRecipe> result = new ArrayList<>();
        List<ItemStack> combustible = BuiltInRegistries.ITEM.stream().map(item -> item.getDefaultInstance())
                .filter(stack -> !stack.isEmpty() && FurnaceThermalController.fuel(stack).ticks() > 0)
                .sorted(Comparator.comparing(FurnaceThermalController::id)).toList();
        for (var device : R196FurnaceRules.profiles().entrySet()) {
            String furnaceId = device.getKey();
            var profile = device.getValue();
            var type = recipeType(furnaceId);
            List<ItemStack> inputs = new ArrayList<>();
            for (var recipe : level.getRecipeManager().getAllRecipesFor(type)) {
                if (!recipe.value().getIngredients().isEmpty()) for (ItemStack input : recipe.value().getIngredients().getFirst().getItems())
                    addUnique(inputs, input.copyWithCount(1));
            }
            if (type == RecipeType.SMELTING) {
                // asg supplies these material conversions even without a matching modern recipe-holder.
                for (var item : List.of(Items.SAND, Items.RED_SAND, Items.SANDSTONE, Items.RED_SANDSTONE))
                    addUnique(inputs, new ItemStack(item));
            }
            List<ItemStack> fuels = combustible.stream().filter(stack -> FurnaceThermalController.canFuel(profile, stack)).toList();
            List<Draft> drafts = new ArrayList<>();
            for (ItemStack variant : inputs) {
                if (!FurnaceThermalController.canInput(level, type, profile, variant)) continue;
                int first = type == RecipeType.SMELTING && (variant.is(Items.SAND) || variant.is(Items.RED_SAND)) ? 4 : 1;
                int last = type == RecipeType.SMELTING && variant.is(Items.CLAY_BALL) ? 4 : first;
                for (int quantity = first; quantity <= last; quantity++) {
                    ItemStack input = variant.copyWithCount(quantity);
                    var recipe = recipe(level, type, input);
                    ResourceLocation sourceId = recipe == null ? ResourceLocation.fromNamespaceAndPath("mite",
                            "thermal_" + BuiltInRegistries.ITEM.getKey(input.getItem()).getPath()) : recipe.id();
                    // Split alternate inputs by their actual heat requirement; otherwise a mixed tag could imply a false fuel pairing.
                    int required = FurnaceThermalController.requiredHeat(input);
                    for (int heat = 1; heat <= profile.maxHeat(); heat++) {
                        int actualHeat = heat;
                        var legal = fuels.stream().filter(fuel -> FurnaceThermalController.fuel(fuel).heat() == actualHeat).toList();
                        if (legal.isEmpty()) continue;
                        var batch = FurnaceThermalController.preview(level, type, profile, input, ItemStack.EMPTY, 64, heat, recipe);
                        if (batch == null) continue;
                        Draft draft = drafts.stream().filter(value -> value.matches(sourceId, required, batch.consumed(), batch.result())).findFirst().orElse(null);
                        if (draft == null) {
                            draft = new Draft(sourceId, required, batch.consumed(), batch.result(), heat);
                            drafts.add(draft);
                        }
                        draft.heat = Math.min(draft.heat, heat);
                        addUnique(draft.inputs, input.copyWithCount(batch.consumed()));
                        for (ItemStack fuel : legal) {
                            int burnTime = FurnaceThermalController.fuel(fuel).ticks();
                            int count = (int)(((long)profile.cycleTicks() + burnTime - 1) / burnTime);
                            addUnique(draft.fuels, fuel.copyWithCount(count));
                        }
                    }
                }
            }
            for (Draft draft : drafts) result.add(new FurnaceJeiRecipe(furnaceId, draft.inputs, draft.output,
                    draft.fuels, draft.heat, profile.cycleTicks(), draft.sourceId));
        }
        return List.copyOf(result);
    }
    @SuppressWarnings("unchecked")
    private static RecipeHolder<? extends AbstractCookingRecipe> recipe(Level level, RecipeType<? extends AbstractCookingRecipe> type, ItemStack input) {
        return level.getRecipeManager().getRecipeFor((RecipeType<AbstractCookingRecipe>)type, new SingleRecipeInput(input), level).orElse(null);
    }
    private static void addUnique(List<ItemStack> stacks, ItemStack item) {
        if (!item.isEmpty() && stacks.stream().noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, item) && existing.getCount() == item.getCount()))
            stacks.add(item.copy());
    }
    private static final class Draft {
        final ResourceLocation sourceId;
        final int required, consumed;
        final ItemStack output;
        final List<ItemStack> inputs = new ArrayList<>(), fuels = new ArrayList<>();
        int heat;
        Draft(ResourceLocation sourceId, int required, int consumed, ItemStack output, int heat) {
            this.sourceId = sourceId; this.required = required; this.consumed = consumed; this.output = output.copy(); this.heat = heat;
        }
        boolean matches(ResourceLocation sourceId, int required, int consumed, ItemStack output) {
            return this.sourceId.equals(sourceId) && this.required == required && this.consumed == consumed
                    && this.output.getCount() == output.getCount() && ItemStack.isSameItemSameComponents(this.output, output);
        }
    }
}
