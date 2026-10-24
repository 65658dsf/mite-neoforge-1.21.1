package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.conditions.FalseCondition;

/** R196 starter recipes, using ordinary recipes for every binding position. @author NingMeng123 */
public final class MiteRecipeProvider extends RecipeProvider {
    public MiteRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        MaterialRecipes.addRecipes(output);
        MaterialUpgradeRecipes.addRecipes(output);
        VibraniumRecipes.addRecipes(output);
        MetalStorageRecipes.addRecipes(output);
        ShardRecipes.addRecipes(output);
        ToolRecipes.addRecipes(output);
        EquipmentRecyclingRecipes.addRecipes(output);
        WorkbenchRecipes.addRecipes(output);
        WorldRecipes.addRecipes(output);
        FurnaceRecipes.addRecipes(output);
        ArmorRecipes.addRecipes(output);
        RangedRecipes.addRecipes(output);
        DecorativeRecipes.addRecipes(output);
        WeaponRecipes.addRecipes(output);
        PhantomBladeRecipes.addRecipes(output);
        UtilityRecipes.addRecipes(output);
        SoupRecipes.addRecipes(output);
        DiamondEquipmentRecipes.addRecipes(output);
        // R196's flint workbench uses a fixed 2x2 pattern (horizontal mirror allowed).
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, MiteWorkbenches.TABLES.get("flint").get())
                .pattern("FB").pattern("SL")
                .define('F', Items.FLINT)
                .define('B', Ingredient.of(Items.STRING, MiteItems.SINEW.get()))
                .define('S', Items.STICK).define('L', ItemTags.LOGS)
                .unlockedBy("has_flint", has(Items.FLINT))
                .save(output, id("flint_crafting_table"));

        // Override the vanilla recipe IDs. RecipeProvider applies these conditions to both
        // recipes and their recipe-book advancements; old registered item stacks stay valid.
        RecipeOutput disabledWoodenTools = output.withConditions(FalseCondition.INSTANCE);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, Items.WOODEN_AXE)
                .pattern("XX").pattern("X#").pattern(" #")
                .define('X', ItemTags.PLANKS).define('#', Items.STICK)
                .unlockedBy("has_planks", has(ItemTags.PLANKS))
                .save(disabledWoodenTools, ResourceLocation.withDefaultNamespace("wooden_axe"));
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, Items.WOODEN_PICKAXE)
                .pattern("XXX").pattern(" # ").pattern(" # ")
                .define('X', ItemTags.PLANKS).define('#', Items.STICK)
                .unlockedBy("has_planks", has(ItemTags.PLANKS))
                .save(disabledWoodenTools, ResourceLocation.withDefaultNamespace("wooden_pickaxe"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Items.FLINT)
                .pattern("CC").pattern("CC").define('C', MiteItems.FLINT_CHIP.get())
                .unlockedBy("has_flint_chip", has(MiteItems.FLINT_CHIP.get()))
                .save(output, id("flint_from_chips"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MiteItems.FLINT_CHIP.get(), 4)
                .pattern("F").define('F', Items.FLINT)
                .unlockedBy("has_flint", has(Items.FLINT))
                .save(output, id("flint_chips_from_flint"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MiteItems.SINEW.get(), 4)
                .pattern("L").define('L', Items.LEATHER)
                .unlockedBy("has_leather", has(Items.LEATHER))
                .save(output, id("sinew_from_leather"));

        // aai's R196 matcher removes exactly one string/sinew slot before matching
        // the mirrored/translatable flint + two-stick skeleton. Vanilla recipes
        // can express the finite set; no custom matcher or crafting bypass is needed.
        // Professions, timed crafting and experience costs are not implemented here.
        int variant = 0;
        for (List<String> pattern : hatchetPatterns()) {
            ShapedRecipeBuilder recipe = ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, MiteItems.FLINT_HATCHET.get())
                    .define('F', Items.FLINT).define('S', Items.STICK)
                    .define('B', Ingredient.of(Items.STRING, MiteItems.SINEW.get()))
                    .group("mite:flint_hatchet")
                    .unlockedBy("has_flint", has(Items.FLINT));
            pattern.forEach(recipe::pattern);
            recipe.save(output, id(variant == 0 ? "flint_hatchet" : "flint_hatchet_binding_" + variant));
            variant++;
        }
    }

    private static List<List<String>> hatchetPatterns() {
        Map<String, List<String>> patterns = new LinkedHashMap<>();
        List<String> starter = List.of("FS", "BS");
        patterns.put(String.join("/", starter), starter);
        for (int top = 0; top < 2; top++) {
            for (int left = 0; left < 2; left++) {
                for (int mirror = 0; mirror < 2; mirror++) {
                    char[][] grid = new char[3][3];
                    for (char[] row : grid) Arrays.fill(row, ' ');
                    grid[top][left + mirror] = 'F';
                    grid[top][left + 1 - mirror] = 'S';
                    grid[top + 1][left + 1 - mirror] = 'S';
                    for (int y = 0; y < 3; y++) {
                        for (int x = 0; x < 3; x++) {
                            if (grid[y][x] != ' ') continue;
                            grid[y][x] = 'B';
                            List<String> pattern = trim(grid);
                            List<String> reversed = pattern.stream()
                                    .map(row -> new StringBuilder(row).reverse().toString()).toList();
                            String forwardKey = String.join("/", pattern);
                            String reversedKey = String.join("/", reversed);
                            if (forwardKey.compareTo(reversedKey) > 0) {
                                pattern = reversed;
                                forwardKey = reversedKey;
                            }
                            patterns.putIfAbsent(forwardKey, pattern);
                            grid[y][x] = ' ';
                        }
                    }
                }
            }
        }
        return List.copyOf(patterns.values());
    }

    private static List<String> trim(char[][] grid) {
        int minX = 3, minY = 3, maxX = -1, maxY = -1;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (grid[y][x] == ' ') continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        List<String> rows = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) rows.add(new String(grid[y], minX, maxX - minX + 1));
        return rows;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, path);
    }
}
