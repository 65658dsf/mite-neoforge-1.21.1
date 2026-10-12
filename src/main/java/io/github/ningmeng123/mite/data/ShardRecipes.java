package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteShards;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** R196 aae's five nine-fragment conversions; glass restores a pane, not a block. @author NingMeng123 */
public final class ShardRecipes extends RecipeProvider {
    public ShardRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) { super(output, lookup); }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        addRecipes(output);
    }

    public static void addRecipes(RecipeOutput output) {
        pair(output, MiteShards.OBSIDIAN_SHARD.get(), Items.OBSIDIAN);
        pair(output, MiteShards.EMERALD_SHARD.get(), Items.EMERALD);
        pair(output, MiteShards.DIAMOND_SHARD.get(), Items.DIAMOND);
        pair(output, MiteShards.QUARTZ_SHARD.get(), Items.QUARTZ);
        pair(output, MiteShards.GLASS_SHARD.get(), Items.GLASS_PANE);
    }

    private static void pair(RecipeOutput output, Item shard, Item whole) {
        String fragment = BuiltInRegistries.ITEM.getKey(shard).getPath();
        String product = BuiltInRegistries.ITEM.getKey(whole).getPath();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, whole)
                .pattern("###").pattern("###").pattern("###").define('#', shard)
                .unlockedBy("has_fragment", has(shard))
                .save(output, ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, product + "_from_shards"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, shard, 9).requires(whole)
                .unlockedBy("has_whole", has(whole))
                .save(output, ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, fragment + "s"));
    }
}
