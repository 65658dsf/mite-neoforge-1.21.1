package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.R196FurnaceRules;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** JEI discovers this optional client integration without any common-side linkage. @author NingMeng123 */
@JeiPlugin
public final class MiteJeiPlugin implements IModPlugin {
    private static final Map<String, RecipeType<FurnaceJeiRecipe>> TYPES = recipeTypes();
    private List<FurnaceJeiRecipe> recipes = List.of();
    private Level recipeLevel;

    private static Map<String, RecipeType<FurnaceJeiRecipe>> recipeTypes() {
        Map<String, RecipeType<FurnaceJeiRecipe>> result = new LinkedHashMap<>();
        R196FurnaceRules.profiles().keySet().forEach(id -> {
            ResourceLocation furnace = ResourceLocation.parse(id);
            result.put(id, RecipeType.create(Mite.MOD_ID,
                    "furnace_" + furnace.getNamespace() + "_" + furnace.getPath(), FurnaceJeiRecipe.class));
        });
        return Collections.unmodifiableMap(result);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "furnaces");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        TYPES.forEach((id, type) -> registration.addRecipeCategories(
                new FurnaceJeiCategory(type, furnace(id), registration.getJeiHelpers().getGuiHelper())));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        recipes = List.of();
        recipeLevel = null;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        List<FurnaceJeiRecipe> built;
        try {
            built = List.copyOf(FurnaceJeiRecipes.build(level));
        } catch (RuntimeException failure) {
            // Keep JEI's existing views usable if the actual world cannot provide a thermal index.
            Mite.LOGGER.error("Unable to build MITE furnace recipes for JEI", failure);
            return;
        }
        TYPES.forEach((id, type) -> registration.addRecipes(type,
                built.stream().filter(recipe -> recipe.furnaceId().equals(id)).toList()));
        recipes = built;
        recipeLevel = level;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        TYPES.forEach((id, type) -> registration.addRecipeCatalysts(type, furnace(id)));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (recipes.isEmpty() || recipeLevel == null || Minecraft.getInstance().level != recipeLevel) return;
        // A catalyst applies to a whole JEI category. The native category cannot express furnace tiers.
        var manager = runtime.getRecipeManager();
        manager.hideRecipeCategory(RecipeTypes.SMELTING);
        manager.hideRecipeCategory(RecipeTypes.BLASTING);
        manager.hideRecipeCategory(RecipeTypes.SMOKING);
        manager.hideRecipeCategory(RecipeTypes.FUELING);
    }

    @Override
    public void onRuntimeUnavailable() {
        recipes = List.of();
        recipeLevel = null;
    }

    private static ItemStack furnace(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }
}
