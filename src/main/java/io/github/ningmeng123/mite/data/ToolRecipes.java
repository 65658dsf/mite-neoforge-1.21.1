package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.crafting.WorkbenchDismantleRecipe;
import java.util.List;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Exact registered R196 shapes; common bench validation is applied to their output. @author NingMeng123 */
public final class ToolRecipes {
    private record Key(char key, Ingredient ingredient) {}
    private ToolRecipes() {}

    public static void addRecipes(RecipeOutput output) {
        // R196 RecipesMITE:119 and :123: old wood metadata variants collapse to each modern bench ID.
        dismantle(output, "flint");
        dismantle(output, "obsidian");
        // R196 runtime recipe order 301; difficulty 315.0; required bench none.
        shaped(output, "mite:obsidian_hatchet", "mite:obsidian_hatchet", new String[]{"AB", "CB"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:obsidian"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('C', Ingredient.of(Items.STRING, MiteItems.SINEW.get())));
        // R196 runtime recipe order 286; difficulty 450.0; required bench copper.
        shaped(output, "mite:copper_hatchet", "mite:copper_hatchet", new String[]{"AB", "A "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))));
        // R196 runtime recipe order 288; difficulty 450.0; required bench silver.
        shaped(output, "mite:silver_hatchet", "mite:silver_hatchet", new String[]{"AB", "A "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("mite:silver_ingot"))));
        // R196 runtime recipe order 296; difficulty 450.0; required bench gold.
        shaped(output, "mite:golden_hatchet", "mite:golden_hatchet", new String[]{"AB", "A "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))));
        // R196 runtime recipe order 290; difficulty 850.0; required bench iron.
        shaped(output, "mite:iron_hatchet", "mite:iron_hatchet", new String[]{"AB", "A "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))));
        // R196 runtime recipe order 298; difficulty 1650.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_hatchet", "mite:ancient_metal_hatchet", new String[]{"AB", "A "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))));
        // R196 runtime recipe order 292; difficulty 6450.0; required bench mithril.
        shaped(output, "mite:mithril_hatchet", "mite:mithril_hatchet", new String[]{"AB", "A "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("mite:mithril_ingot"))));
        // R196 runtime recipe order 294; difficulty 25650.0; required bench adamantium.
        shaped(output, "mite:adamantium_hatchet", "mite:adamantium_hatchet", new String[]{"AB", "A "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))));
        // R196 runtime recipe order 209; difficulty 1250.0; required bench gold.
        shaped(output, "minecraft:golden_axe", "minecraft:golden_axe", new String[]{"AA", "AB", " B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 203; difficulty 2450.0; required bench iron.
        shaped(output, "minecraft:iron_axe", "minecraft:iron_axe", new String[]{"AA", "AB", " B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 214; difficulty 375.0; required bench none.
        shaped(output, "mite:flint_axe", "mite:flint_axe", new String[]{"AA", "AB", "CB"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:flint"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('C', Ingredient.of(Items.STRING, MiteItems.SINEW.get())));
        // R196 runtime recipe order 216; difficulty 795.00006; required bench none.
        shaped(output, "mite:obsidian_axe", "mite:obsidian_axe", new String[]{"AA", "AB", "CB"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:obsidian"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('C', Ingredient.of(Items.STRING, MiteItems.SINEW.get())));
        // R196 runtime recipe order 199; difficulty 1250.0; required bench copper.
        shaped(output, "mite:copper_axe", "mite:copper_axe", new String[]{"AA", "AB", " B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 201; difficulty 1250.0; required bench silver.
        shaped(output, "mite:silver_axe", "mite:silver_axe", new String[]{"AA", "AB", " B"}, new Key('A', Ingredient.of(MiteTools.get("mite:silver_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 211; difficulty 4850.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_axe", "mite:ancient_metal_axe", new String[]{"AA", "AB", " B"}, new Key('A', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 205; difficulty 19250.0; required bench mithril.
        shaped(output, "mite:mithril_axe", "mite:mithril_axe", new String[]{"AA", "AB", " B"}, new Key('A', Ingredient.of(MiteTools.get("mite:mithril_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 207; difficulty 76850.0; required bench adamantium.
        shaped(output, "mite:adamantium_axe", "mite:adamantium_axe", new String[]{"AA", "AB", " B"}, new Key('A', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 27; difficulty 1250.0; required bench gold.
        shaped(output, "minecraft:golden_pickaxe", "minecraft:golden_pickaxe", new String[]{"AAA", " B ", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 12; difficulty 2450.0; required bench iron.
        shaped(output, "minecraft:iron_pickaxe", "minecraft:iron_pickaxe", new String[]{"AAA", " B ", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 2; difficulty 1250.0; required bench copper.
        shaped(output, "mite:copper_pickaxe", "mite:copper_pickaxe", new String[]{"AAA", " B ", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 7; difficulty 1250.0; required bench silver.
        shaped(output, "mite:silver_pickaxe", "mite:silver_pickaxe", new String[]{"AAA", " B ", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:silver_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 32; difficulty 4850.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_pickaxe", "mite:ancient_metal_pickaxe", new String[]{"AAA", " B ", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 17; difficulty 19250.0; required bench mithril.
        shaped(output, "mite:mithril_pickaxe", "mite:mithril_pickaxe", new String[]{"AAA", " B ", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:mithril_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 22; difficulty 76850.0; required bench adamantium.
        shaped(output, "mite:adamantium_pickaxe", "mite:adamantium_pickaxe", new String[]{"AAA", " B ", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 368; difficulty 450.0; required bench gold.
        shaped(output, "minecraft:golden_shovel", "minecraft:golden_shovel", new String[]{"A", "B", "B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 365; difficulty 850.0; required bench iron.
        shaped(output, "minecraft:iron_shovel", "minecraft:iron_shovel", new String[]{"A", "B", "B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 213; difficulty 175.0; required bench none.
        shaped(output, "mite:flint_shovel", "mite:flint_shovel", new String[]{"A ", "B ", "BC"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:flint"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('C', Ingredient.of(Items.STRING, MiteItems.SINEW.get())));
        // R196 runtime recipe order 215; difficulty 315.0; required bench none.
        shaped(output, "mite:obsidian_shovel", "mite:obsidian_shovel", new String[]{"A ", "B ", "BC"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:obsidian"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('C', Ingredient.of(Items.STRING, MiteItems.SINEW.get())));
        // R196 runtime recipe order 363; difficulty 450.0; required bench copper.
        shaped(output, "mite:copper_shovel", "mite:copper_shovel", new String[]{"A", "B", "B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 364; difficulty 450.0; required bench silver.
        shaped(output, "mite:silver_shovel", "mite:silver_shovel", new String[]{"A", "B", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:silver_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 369; difficulty 1650.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_shovel", "mite:ancient_metal_shovel", new String[]{"A", "B", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 366; difficulty 6450.0; required bench mithril.
        shaped(output, "mite:mithril_shovel", "mite:mithril_shovel", new String[]{"A", "B", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:mithril_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 367; difficulty 25650.0; required bench adamantium.
        shaped(output, "mite:adamantium_shovel", "mite:adamantium_shovel", new String[]{"A", "B", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 210; difficulty 850.0; required bench gold.
        shaped(output, "minecraft:golden_hoe", "minecraft:golden_hoe", new String[]{"AA", " B", " B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 204; difficulty 1650.0; required bench iron.
        shaped(output, "minecraft:iron_hoe", "minecraft:iron_hoe", new String[]{"AA", " B", " B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 200; difficulty 850.0; required bench copper.
        shaped(output, "mite:copper_hoe", "mite:copper_hoe", new String[]{"AA", " B", " B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 202; difficulty 850.0; required bench silver.
        shaped(output, "mite:silver_hoe", "mite:silver_hoe", new String[]{"AA", " B", " B"}, new Key('A', Ingredient.of(MiteTools.get("mite:silver_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 212; difficulty 3250.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_hoe", "mite:ancient_metal_hoe", new String[]{"AA", " B", " B"}, new Key('A', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 206; difficulty 12850.0; required bench mithril.
        shaped(output, "mite:mithril_hoe", "mite:mithril_hoe", new String[]{"AA", " B", " B"}, new Key('A', Ingredient.of(MiteTools.get("mite:mithril_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 208; difficulty 51250.0; required bench adamantium.
        shaped(output, "mite:adamantium_hoe", "mite:adamantium_hoe", new String[]{"AA", " B", " B"}, new Key('A', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 3; difficulty 1650.0; required bench copper.
        shaped(output, "mite:copper_mattock", "mite:copper_mattock", new String[]{"AAA", " BA", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 8; difficulty 1650.0; required bench silver.
        shaped(output, "mite:silver_mattock", "mite:silver_mattock", new String[]{"AAA", " BA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:silver_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 28; difficulty 1650.0; required bench gold.
        shaped(output, "mite:golden_mattock", "mite:golden_mattock", new String[]{"AAA", " BA", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 13; difficulty 3250.0; required bench iron.
        shaped(output, "mite:iron_mattock", "mite:iron_mattock", new String[]{"AAA", " BA", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 33; difficulty 6450.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_mattock", "mite:ancient_metal_mattock", new String[]{"AAA", " BA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 18; difficulty 25650.0; required bench mithril.
        shaped(output, "mite:mithril_mattock", "mite:mithril_mattock", new String[]{"AAA", " BA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:mithril_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 23; difficulty 102450.0; required bench adamantium.
        shaped(output, "mite:adamantium_mattock", "mite:adamantium_mattock", new String[]{"AAA", " BA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 6; difficulty 875.0; required bench copper.
        shaped(output, "mite:copper_scythe", "mite:copper_scythe", new String[]{"AB ", "A B", "A  "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))));
        // R196 runtime recipe order 11; difficulty 875.0; required bench silver.
        shaped(output, "mite:silver_scythe", "mite:silver_scythe", new String[]{"AB ", "A B", "A  "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("mite:silver_ingot"))));
        // R196 runtime recipe order 31; difficulty 875.0; required bench gold.
        shaped(output, "mite:golden_scythe", "mite:golden_scythe", new String[]{"AB ", "A B", "A  "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))));
        // R196 runtime recipe order 16; difficulty 1675.0; required bench iron.
        shaped(output, "mite:iron_scythe", "mite:iron_scythe", new String[]{"AB ", "A B", "A  "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))));
        // R196 runtime recipe order 36; difficulty 3275.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_scythe", "mite:ancient_metal_scythe", new String[]{"AB ", "A B", "A  "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))));
        // R196 runtime recipe order 21; difficulty 12875.0; required bench mithril.
        shaped(output, "mite:mithril_scythe", "mite:mithril_scythe", new String[]{"AB ", "A B", "A  "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("mite:mithril_ingot"))));
        // R196 runtime recipe order 26; difficulty 51275.0; required bench adamantium.
        shaped(output, "mite:adamantium_scythe", "mite:adamantium_scythe", new String[]{"AB ", "A B", "A  "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:stick"))), new Key('B', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))));
        // R196 runtime recipe order 287; difficulty 800.0; required bench copper.
        shaped(output, "mite:copper_shears", "mite:copper_shears", new String[]{" A", "A "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))));
        // R196 runtime recipe order 289; difficulty 800.0; required bench silver.
        shaped(output, "mite:silver_shears", "mite:silver_shears", new String[]{" A", "A "}, new Key('A', Ingredient.of(MiteTools.get("mite:silver_ingot"))));
        // R196 runtime recipe order 297; difficulty 800.0; required bench gold.
        shaped(output, "mite:golden_shears", "mite:golden_shears", new String[]{" A", "A "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))));
        // R196 runtime recipe order 299; difficulty 3200.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_shears", "mite:ancient_metal_shears", new String[]{" A", "A "}, new Key('A', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))));
        // R196 runtime recipe order 293; difficulty 12800.0; required bench mithril.
        shaped(output, "mite:mithril_shears", "mite:mithril_shears", new String[]{" A", "A "}, new Key('A', Ingredient.of(MiteTools.get("mite:mithril_ingot"))));
        // R196 runtime recipe order 295; difficulty 51200.0; required bench adamantium.
        shaped(output, "mite:adamantium_shears", "mite:adamantium_shears", new String[]{" A", "A "}, new Key('A', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))));
        // R196 runtime recipe order 417; difficulty 105.0; required bench none.
        shaped(output, "mite:wooden_cudgel", "mite:wooden_cudgel", new String[]{"A", "B"}, new Key('A', Ingredient.of(ItemTags.PLANKS)), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 303; difficulty 290.0; required bench none.
        shaped(output, "mite:obsidian_knife", "mite:obsidian_knife", new String[]{"AB", "C "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:obsidian"))), new Key('B', Ingredient.of(Items.STRING, MiteItems.SINEW.get())), new Key('C', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 302; difficulty 150.0; required bench none.
        shaped(output, "mite:flint_knife", "mite:flint_knife", new String[]{"AB", "C "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:flint"))), new Key('B', Ingredient.of(Items.STRING, MiteItems.SINEW.get())), new Key('C', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 403; difficulty 425.0; required bench copper.
        shaped(output, "mite:copper_dagger", "mite:copper_dagger", new String[]{"A", "B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 405; difficulty 425.0; required bench silver.
        shaped(output, "mite:silver_dagger", "mite:silver_dagger", new String[]{"A", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:silver_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 413; difficulty 425.0; required bench gold.
        shaped(output, "mite:golden_dagger", "mite:golden_dagger", new String[]{"A", "B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 407; difficulty 825.0; required bench iron.
        shaped(output, "mite:iron_dagger", "mite:iron_dagger", new String[]{"A", "B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 415; difficulty 1625.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_dagger", "mite:ancient_metal_dagger", new String[]{"A", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 409; difficulty 6425.0; required bench mithril.
        shaped(output, "mite:mithril_dagger", "mite:mithril_dagger", new String[]{"A", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:mithril_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 411; difficulty 25625.0; required bench adamantium.
        shaped(output, "mite:adamantium_dagger", "mite:adamantium_dagger", new String[]{"A", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 371; difficulty 825.0; required bench copper.
        shaped(output, "mite:copper_sword", "mite:copper_sword", new String[]{"A", "A", "B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 376; difficulty 825.0; required bench gold.
        shaped(output, "minecraft:golden_sword", "minecraft:golden_sword", new String[]{"A", "A", "B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 373; difficulty 1625.0; required bench iron.
        shaped(output, "minecraft:iron_sword", "minecraft:iron_sword", new String[]{"A", "A", "B"}, new Key('A', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 372; difficulty 825.0; required bench silver.
        shaped(output, "mite:silver_sword", "mite:silver_sword", new String[]{"A", "A", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:silver_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 377; difficulty 3225.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_sword", "mite:ancient_metal_sword", new String[]{"A", "A", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 374; difficulty 12825.0; required bench mithril.
        shaped(output, "mite:mithril_sword", "mite:mithril_sword", new String[]{"A", "A", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:mithril_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 375; difficulty 51225.0; required bench adamantium.
        shaped(output, "mite:adamantium_sword", "mite:adamantium_sword", new String[]{"A", "A", "B"}, new Key('A', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 4; difficulty 1650.0; required bench copper.
        shaped(output, "mite:copper_battle_axe", "mite:copper_battle_axe", new String[]{"A A", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 9; difficulty 1650.0; required bench silver.
        shaped(output, "mite:silver_battle_axe", "mite:silver_battle_axe", new String[]{"A A", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:silver_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 29; difficulty 1650.0; required bench gold.
        shaped(output, "mite:golden_battle_axe", "mite:golden_battle_axe", new String[]{"A A", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 14; difficulty 3250.0; required bench iron.
        shaped(output, "mite:iron_battle_axe", "mite:iron_battle_axe", new String[]{"A A", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 34; difficulty 6450.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_battle_axe", "mite:ancient_metal_battle_axe", new String[]{"A A", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 19; difficulty 25650.0; required bench mithril.
        shaped(output, "mite:mithril_battle_axe", "mite:mithril_battle_axe", new String[]{"A A", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:mithril_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 24; difficulty 102450.0; required bench adamantium.
        shaped(output, "mite:adamantium_battle_axe", "mite:adamantium_battle_axe", new String[]{"A A", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 5; difficulty 2050.0; required bench copper.
        shaped(output, "mite:copper_war_hammer", "mite:copper_war_hammer", new String[]{"AAA", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:copper_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 10; difficulty 2050.0; required bench silver.
        shaped(output, "mite:silver_war_hammer", "mite:silver_war_hammer", new String[]{"AAA", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:silver_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 30; difficulty 2050.0; required bench gold.
        shaped(output, "mite:golden_war_hammer", "mite:golden_war_hammer", new String[]{"AAA", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:gold_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 15; difficulty 4050.0; required bench iron.
        shaped(output, "mite:iron_war_hammer", "mite:iron_war_hammer", new String[]{"AAA", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 35; difficulty 8050.0; required bench ancient_metal.
        shaped(output, "mite:ancient_metal_war_hammer", "mite:ancient_metal_war_hammer", new String[]{"AAA", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:ancient_metal_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 20; difficulty 32050.0; required bench mithril.
        shaped(output, "mite:mithril_war_hammer", "mite:mithril_war_hammer", new String[]{"AAA", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:mithril_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 25; difficulty 128050.0; required bench adamantium.
        shaped(output, "mite:adamantium_war_hammer", "mite:adamantium_war_hammer", new String[]{"AAA", "ABA", " B "}, new Key('A', Ingredient.of(MiteTools.get("mite:adamantium_ingot"))), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
        // R196 runtime recipe order 291; difficulty 1600.0; required bench iron.
        shaped(output, "minecraft:shears", "minecraft:shears", new String[]{" A", "A "}, new Key('A', Ingredient.of(MiteTools.get("minecraft:iron_ingot"))));
        // R196 runtime recipe order 362; difficulty 130.0; required bench none.
        shaped(output, "minecraft:wooden_shovel", "minecraft:wooden_shovel", new String[]{"A", "B", "B"}, new Key('A', Ingredient.of(ItemTags.PLANKS)), new Key('B', Ingredient.of(MiteTools.get("minecraft:stick"))));
    }

    private static void dismantle(RecipeOutput output, String material) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mite", material + "_knife_from_workbench");
        ItemStack result = new ItemStack(MiteTools.get(material + "_knife"));
        var table = MiteTools.get(material + "_crafting_table");
        var advancement = output.advancement()
                .addCriterion("has_table", InventoryChangeTrigger.TriggerInstance.hasItems(table))
                .rewards(net.minecraft.advancements.AdvancementRewards.Builder.recipe(id));
        output.accept(id, new WorkbenchDismantleRecipe(Ingredient.of(table), result), advancement.build(id.withPrefix("recipes/tools/")));
    }

    private static void shaped(RecipeOutput output, String recipeId, String outputId, String[] pattern, Key... keys) {
        var builder = ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, MiteTools.get(outputId));
        for (String row : pattern) builder.pattern(row);
        for (Key key : keys) builder.define(key.key(), key.ingredient());
        // The input identity, not an unattainable copy of the finished tool, unlocks its recipe book entry.
        builder.unlockedBy("has_material", InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(keys[0].ingredient().getItems()[0].getItem())))
                .save(output, ResourceLocation.parse(recipeId));
    }
}
