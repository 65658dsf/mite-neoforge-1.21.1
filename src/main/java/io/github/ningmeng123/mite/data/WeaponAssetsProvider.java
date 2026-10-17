package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Thirty arrow-material pull sprites per source reinforced bow, plus five source sphere sprites. @author NingMeng123 */
public final class WeaponAssetsProvider implements DataProvider {
    public static final List<String> BOW_MATERIALS = List.of("ancient_metal", "mithril", "vibranium");
    public static final List<String> ARROW_MATERIALS = List.of("flint", "obsidian", "copper", "silver", "rusted_iron", "gold", "iron", "mithril", "adamantium", "ancient_metal");
    private final Path root;
    public WeaponAssetsProvider(PackOutput output) { root = output.getOutputFolder(); }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> tasks = new ArrayList<>();
        for (String material : BOW_MATERIALS) {
            JsonObject base = model("minecraft:item/bow", "mite:item/bows/" + material + "/standby");
            JsonArray overrides = new JsonArray();
            for (int arrow = 0; arrow < ARROW_MATERIALS.size(); arrow++) for (int stage = 0; stage < 3; stage++) {
                String name = material + "_bow_" + ARROW_MATERIALS.get(arrow) + "_" + stage;
                JsonObject override = new JsonObject(), predicate = new JsonObject();
                predicate.addProperty("pulling", 1); predicate.addProperty("pull", stage == 0 ? 0 : stage == 1 ? .65F : .9F);
                predicate.addProperty("mite:arrow_material", arrow); override.add("predicate", predicate); override.addProperty("model", "mite:item/" + name);
                overrides.add(override);
                save(tasks, cache, "assets/mite/models/item/" + name + ".json", model("minecraft:item/bow", "mite:item/bows/" + material + "/" + ARROW_MATERIALS.get(arrow) + "_arrow_" + stage));
            }
            base.add("overrides", overrides);
            save(tasks, cache, "assets/mite/models/item/" + material + "_bow.json", base);
        }
        String[] names = {"minecraft:slime_ball", "mite:ochre_jelly", "mite:crimson_blob", "mite:grey_gelatinous_sphere", "mite:black_pudding"};
        String[] colors = {"green", "ochre", "crimson", "gray", "black"};
        for (int i = 0; i < names.length; i++) {
            String[] id = names[i].split(":");
            save(tasks, cache, "assets/" + id[0] + "/models/item/" + id[1] + ".json", model("minecraft:item/generated", "mite:item/gelatinous_sphere/" + colors[i]));
        }
        JsonArray bows = new JsonArray(); BOW_MATERIALS.forEach(material -> bows.add("mite:" + material + "_bow"));
        for (String tag : List.of("bow")) {
            JsonObject data = new JsonObject(); data.addProperty("replace", false); data.add("values", bows.deepCopy());
            save(tasks, cache, "data/minecraft/tags/item/enchantable/" + tag + ".json", data);
        }
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }
    private static JsonObject model(String parent, String texture) {
        JsonObject model = new JsonObject(), textures = new JsonObject(); model.addProperty("parent", parent);
        textures.addProperty("layer0", texture); model.add("textures", textures); return model;
    }
    private void save(List<CompletableFuture<?>> tasks, CachedOutput cache, String path, JsonObject json) { tasks.add(DataProvider.saveStable(cache, json, root.resolve(path))); }
    @Override public String getName() { return "MITE reinforced bow animations and gelatinous spheres"; }
}
