package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.rules.R196FurnaceRules;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** JEI discovers this optional client integration without any common-side linkage. @author NingMeng123 */
@JeiPlugin
public final class MiteJeiPlugin implements IModPlugin {
    private static final Map<String, RecipeType<FurnaceJeiRecipe>> TYPES = recipeTypes();
    private static final Map<String, RecipeType<WorkbenchJeiRecipe>> WORKBENCH_TYPES = workbenchTypes();
    private List<FurnaceJeiRecipe> recipes = List.of();
    private WorkbenchJeiRecipes.Index workbenchIndex = new WorkbenchJeiRecipes.Index(List.of(), List.of());
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

    private static Map<String, RecipeType<WorkbenchJeiRecipe>> workbenchTypes() {
        Map<String, RecipeType<WorkbenchJeiRecipe>> result = new LinkedHashMap<>();
        WorkbenchJeiRecipes.categoryProfiles().keySet().forEach(id -> {
            ResourceLocation bench = ResourceLocation.parse(id);
            result.put(id, RecipeType.create(Mite.MOD_ID,
                    "workbench_" + bench.getNamespace() + "_" + bench.getPath(), WorkbenchJeiRecipe.class));
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
        WORKBENCH_TYPES.forEach((id, type) -> registration.addRecipeCategories(new WorkbenchJeiCategory(type,
                id.equals(WorkbenchJeiRecipes.INVENTORY) ? new ItemStack(Items.CHEST) : furnace(id),
                registration.getJeiHelpers().getGuiHelper(), id.equals(WorkbenchJeiRecipes.INVENTORY))));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        recipes = List.of();
        workbenchIndex = new WorkbenchJeiRecipes.Index(List.of(), List.of());
        recipeLevel = null;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        recipeLevel = level;
        try {
            var built = WorkbenchJeiRecipes.groupedIndex(level);
            WORKBENCH_TYPES.forEach((id, type) -> registration.addRecipes(type,
                    built.recipes().stream().filter(recipe -> recipe.workstationId().equals(id)).toList()));
            workbenchIndex = built;
        } catch (RuntimeException failure) {
            Mite.LOGGER.error("Unable to build MITE workbench recipes for JEI", failure);
        }
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
        WORKBENCH_TYPES.forEach((id, type) -> {
            for (String workstation : WorkbenchJeiRecipes.categoryWorkstations(id))
                registration.addRecipeCatalysts(type, furnace(workstation));
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        var ingredientManager = runtime.getIngredientManager();
        var removedEquipment = ingredientManager.getAllItemStacks().stream()
                .filter(RemovedDiamondEquipment::isRemoved).toList();
        if (!removedEquipment.isEmpty()) {
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, removedEquipment);
        }
        if (recipeLevel == null || Minecraft.getInstance().level != recipeLevel) return;
        // A catalyst applies to a whole JEI category. The native category cannot express furnace tiers.
        var manager = runtime.getRecipeManager();
        // Preserve JEI's dynamic repair/dye/map/firework extensions and other mods' custom recipe categories.
        if (!workbenchIndex.originals().isEmpty()) manager.hideRecipes(RecipeTypes.CRAFTING, workbenchIndex.originals());
        if (workbenchIndex.recipes().stream().anyMatch(recipe -> SuspiciousStewJeiRecipes.isDisplayVariant(recipe.displayId()))) {
            var obsoleteStews = manager.createRecipeLookup(RecipeTypes.CRAFTING).get()
                    .filter(holder -> SuspiciousStewJeiRecipes.isObsoleteVanillaPage(holder, recipeLevel)).toList();
            if (!obsoleteStews.isEmpty()) manager.hideRecipes(RecipeTypes.CRAFTING, obsoleteStews);
        }
        if (recipes.isEmpty()) return;
        manager.hideRecipeCategory(RecipeTypes.SMELTING);
        manager.hideRecipeCategory(RecipeTypes.BLASTING);
        manager.hideRecipeCategory(RecipeTypes.SMOKING);
        manager.hideRecipeCategory(RecipeTypes.FUELING);
    }

    @Override
    public void onRuntimeUnavailable() {
        recipes = List.of();
        workbenchIndex = new WorkbenchJeiRecipes.Index(List.of(), List.of());
        recipeLevel = null;
    }

    private static ItemStack furnace(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }
}
