package io.github.ningmeng123.mite.integration.jei;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.ILookupState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Supplies JEI's native outer sidebar from the current, sorted repair page. @author NingMeng123 */
public final class AnvilJeiSidebar {
    private AnvilJeiSidebar() {}

    public static Stream<Consumer<IIngredientAcceptor<?>>> groups(ILookupState state,
            IRecipeCategory<?> category, IRecipeCategory<?> cachedCategory, IRecipeLayoutList cachedLayouts) {
        List<AnvilJeiRecipe> rows = visibleRecipes(state, category, cachedCategory, cachedLayouts);
        if (rows.isEmpty()) return Stream.empty();
        var anvils = new LinkedHashSet<>(AnvilJeiRecipes.categoryAnvils(rows.getFirst().anvilId()));
        for (int i = 1; i < rows.size(); i++)
            anvils.retainAll(AnvilJeiRecipes.categoryAnvils(rows.get(i).anvilId()));
        // One native catalyst group per anvil preserves the standard vertical strip and its interactions.
        return anvils.stream().map(id -> acceptor -> acceptor.addItemStack(
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)))));
    }

    private static List<AnvilJeiRecipe> visibleRecipes(ILookupState state, IRecipeCategory<?> category,
            IRecipeCategory<?> cachedCategory, IRecipeLayoutList cachedLayouts) {
        if (state == null) return List.of();
        // RecipesGui refreshes the sorted layout cache before requesting catalyst groups.
        // A category change invalidates the cache; do not reuse the preceding category's rows.
        if (cachedLayouts != null && cachedCategory == category) {
            try {
                var layouts = state.getVisible(cachedLayouts);
                List<AnvilJeiRecipe> rows = new ArrayList<>();
                for (var layout : layouts) {
                    var drawable = layout.getRecipeLayout();
                    if (drawable.getRecipeCategory() != category || !(drawable.getRecipe() instanceof AnvilJeiRecipe repair)) {
                        rows.clear();
                        break;
                    }
                    rows.add(repair);
                }
                if (!rows.isEmpty()) return List.copyOf(rows);
            } catch (IndexOutOfBoundsException staleLayout) {
                // Handle the brief interval before JEI has recreated a changed layout list.
            }
        }
        // Initial requests can arrive before any drawable layouts exist; use this query's page only.
        var focused = state.getFocusedRecipes().getRecipes();
        int start = Math.clamp(state.getRecipeIndex(), 0, focused.size());
        int count = Math.min(Math.max(1, state.getRecipesPerPage()), focused.size() - start);
        List<AnvilJeiRecipe> rows = new ArrayList<>();
        for (Object recipe : focused.subList(start, start + count)) {
            if (!(recipe instanceof AnvilJeiRecipe repair)) return List.of();
            rows.add(repair);
        }
        return List.copyOf(rows);
    }
}
