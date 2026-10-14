package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original unmodified record sprites/OGGs with modern registry-backed jukebox songs. @author NingMeng123 */
public final class UtilityAssetsProvider implements DataProvider {
    private final Path output;
    public UtilityAssetsProvider(PackOutput output) { this.output = output.getOutputFolder(); }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        MiteUtilityContent.entries().keySet().forEach(path -> {
            JsonObject model = new JsonObject(); model.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject(); textures.addProperty("layer0", "mite:item/" + path); model.add("textures", textures);
            work.add(DataProvider.saveStable(cache, model, output.resolve("assets/mite/models/item/" + path + ".json")));
        });
        JsonObject sounds = new JsonObject();
        try (var reader = new InputStreamReader(Objects.requireNonNull(UtilityAssetsProvider.class.getResourceAsStream("/mite/utility-028.json")), StandardCharsets.UTF_8)) {
            for (var value : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("songs")) {
                JsonObject row = value.getAsJsonObject(); String name = row.get("name").getAsString();
                JsonObject song = new JsonObject(); song.addProperty("sound_event", "mite:record." + name);
                JsonObject description = new JsonObject(); description.addProperty("translate", "item.mite.music_disc_" + name + ".desc"); song.add("description", description);
                song.add("length_in_seconds", row.get("length_in_seconds")); song.add("comparator_output", row.get("comparator_output"));
                work.add(DataProvider.saveStable(cache, song, output.resolve("data/mite/jukebox_song/" + name + ".json")));
                JsonObject event = new JsonObject(), sound = new JsonObject(); JsonArray array = new JsonArray();
                sound.addProperty("name", "mite:records/" + name); sound.addProperty("stream", true); array.add(sound); event.add("sounds", array);
                sounds.add("record." + name, event);
            }
        } catch (Exception failure) { throw new IllegalStateException("Cannot generate source record metadata", failure); }
        work.add(DataProvider.saveStable(cache, sounds, output.resolve("assets/mite/sounds.json")));
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "MITE R196 utility models and playable records"; }
}
