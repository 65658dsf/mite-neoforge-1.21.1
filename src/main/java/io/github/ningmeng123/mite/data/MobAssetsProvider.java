package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.entity.MobKind;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Registry-backed eggs, original sounds and explicit source biology/damage tags. @author NingMeng123 */
public final class MobAssetsProvider implements DataProvider {
    private final Path output;
    public MobAssetsProvider(PackOutput pack) { output = pack.getOutputFolder(); }
    public static JsonObject soundDefinitions() {
        try (var reader = new InputStreamReader(MobAssetsProvider.class.getResourceAsStream("/mite/mob-assets-031.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("sounds");
        } catch (Exception e) { throw new IllegalStateException("Cannot read mob sound manifest", e); }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        for (MobKind kind : MobKind.values()) {
            JsonObject model = new JsonObject(); model.addProperty("parent", "minecraft:item/template_spawn_egg");
            save(work, cache, "assets/mite/models/item/" + kind.path() + "_spawn_egg.json", model);
        }
        for (String name : List.of("water", "sunlight", "acid", "pepsin", "melt")) {
            JsonObject damage = new JsonObject(); damage.addProperty("message_id", "mite." + name);
            damage.addProperty("scaling", "never"); damage.addProperty("exhaustion", 0);
            save(work, cache, "data/mite/damage_type/" + name + ".json", damage);
        }
        for (String tag : List.of("bypasses_armor", "bypasses_invulnerability", "bypasses_effects", "bypasses_resistance")) {
            JsonArray values = new JsonArray(); values.add("mite:melt");
            if (tag.equals("bypasses_armor")) { values.add("mite:water"); values.add("mite:sunlight"); values.add("mite:acid"); values.add("mite:pepsin"); }
            save(work, cache, "data/minecraft/tags/damage_type/" + tag + ".json", tag(values));
        }
        JsonArray undead = new JsonArray(), arthropods = new JsonArray();
        for (MobKind kind : MobKind.values()) {
            if (kind.family() == MobKind.Family.SKELETON || kind.family() == MobKind.Family.ZOMBIE || kind == MobKind.WIGHT || kind == MobKind.SHADOW || kind == MobKind.NIGHTWING)
                undead.add("mite:" + kind.path());
            if (kind.family() == MobKind.Family.SPIDER || kind.family() == MobKind.Family.SILVERFISH) arthropods.add("mite:" + kind.path());
        }
        for (String name : List.of("undead", "sensitive_to_smite", "ignores_poison_and_regen", "inverted_healing_and_harm")) save(work, cache, "data/minecraft/tags/entity_type/" + name + ".json", tag(undead));
        for (String name : List.of("arthropod", "sensitive_to_bane_of_arthropods")) save(work, cache, "data/minecraft/tags/entity_type/" + name + ".json", tag(arthropods));
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }
    private static JsonObject tag(JsonArray values) { JsonObject tag = new JsonObject(); tag.addProperty("replace", false); tag.add("values", values); return tag; }
    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, JsonObject json) { work.add(DataProvider.saveStable(cache, json, output.resolve(path))); }
    @Override public String getName() { return "MITE R196 mob eggs, biology and special damage"; }
}
