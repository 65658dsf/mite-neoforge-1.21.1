package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

/** Material-specific rods, cast sprites, arrows and native ammunition/fishing enchantment tags. @author NingMeng123 */
public final class RangedAssetsProvider implements DataProvider {
    private final Path output;
    public RangedAssetsProvider(PackOutput output) { this.output = output.getOutputFolder(); }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        JsonArray arrows = new JsonArray(), rods = new JsonArray();
        for (Material material : Material.values()) {
            ResourceLocation arrow = MiteRangedItems.arrowId(material);
            model(work, cache, arrow, "generated", "mite:item/" + arrow.getPath(), null);
            if (material != Material.FLINT) arrows.add(arrow.toString());
            if (!material.hasRod()) continue;
            ResourceLocation rod = MiteRangedItems.rodId(material);
            JsonArray overrides = new JsonArray();
            JsonObject cast = new JsonObject(), predicate = new JsonObject();
            predicate.addProperty("cast", 1);
            cast.add("predicate", predicate); cast.addProperty("model", rod.getNamespace() + ":item/" + rod.getPath() + "_cast"); overrides.add(cast);
            model(work, cache, rod, "handheld_rod", "mite:item/" + rod.getPath(), overrides);
            model(work, cache, rod.withSuffix("_cast"), "handheld_rod", "mite:item/fishing_rod_cast", null);
            ResourceLocation carrot = MiteRangedItems.carrotRodId(material);
            model(work, cache, carrot, "handheld_rod", "mite:item/carrot_on_a_stick", null);
            if (material != Material.IRON) rods.add(rod.toString());
        }
        tag(work, cache, "data/minecraft/tags/item/arrows.json", arrows);
        tag(work, cache, "data/minecraft/tags/item/enchantable/fishing.json", rods);
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }
    private void model(List<CompletableFuture<?>> work, CachedOutput cache, ResourceLocation id, String parent, String texture, JsonArray overrides) {
        JsonObject model = new JsonObject(), textures = new JsonObject();
        model.addProperty("parent", "minecraft:item/" + parent);
        textures.addProperty("layer0", texture); model.add("textures", textures);
        if (overrides != null) model.add("overrides", overrides);
        work.add(DataProvider.saveStable(cache, model, output.resolve("assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json")));
    }
    private void tag(List<CompletableFuture<?>> work, CachedOutput cache, String path, JsonArray values) {
        JsonObject tag = new JsonObject(); tag.addProperty("replace", false); tag.add("values", values);
        work.add(DataProvider.saveStable(cache, tag, output.resolve(path)));
    }
    @Override public String getName() { return "MITE R196 ranged models and native ranged tags"; }
}
