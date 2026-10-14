package io.github.ningmeng123.mite.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.rules.DecorativeResourceRules;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Original texture references on modern geometry; source identities in modern surface rules. @author NingMeng123 */
public final class DecorativeAssetsProvider implements DataProvider {
    private final PackOutput output;
    private final ExistingFileHelper existingFiles;
    public DecorativeAssetsProvider(PackOutput output, ExistingFileHelper existingFiles) { this.output = output; this.existingFiles = existingFiles; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> tasks = new ArrayList<>();
        for (String metal : List.of("copper", "silver", "gold", "ancient_metal", "mithril", "adamantium")) {
            String name = (metal.equals("gold") ? "golden" : metal) + "_door", ns = metal.equals("copper") ? "minecraft" : "mite";
            JsonObject states = resource("blockstates/copper_door.json", PackType.CLIENT_RESOURCES);
            replaceStrings(states, "minecraft:block/copper_door", ns + ":block/" + name);
            save(tasks, cache, "assets/" + ns + "/blockstates/" + name + ".json", states.toString());
            for (String part : List.of("bottom_left", "bottom_right", "top_left", "top_right", "bottom_left_open", "bottom_right_open", "top_left_open", "top_right_open"))
                save(tasks, cache, "assets/" + ns + "/models/block/" + name + "_" + part + ".json", "{\"parent\":\"minecraft:block/door_" + part
                        + "\",\"render_type\":\"minecraft:cutout\",\"textures\":{\"bottom\":\"mite:block/door_" + metal + "_lower\",\"top\":\"mite:block/door_" + metal + "_upper\"}}");
            save(tasks, cache, "assets/" + ns + "/models/item/" + name + ".json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mite:item/door_" + metal + "\"}}");
            save(tasks, cache, "data/" + ns + "/loot_table/blocks/" + name + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"conditions\":[{\"condition\":\"minecraft:block_state_property\",\"block\":\"" + ns + ":" + name
                    + "\",\"properties\":{\"half\":\"lower\"}},{\"condition\":\"minecraft:survives_explosion\"}],\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + ns + ":" + name + "\"}]}]}");
        }
        for (String metal : List.of("copper", "gold", "ancient_metal", "mithril", "adamantium")) {
            String name = (metal.equals("gold") ? "golden" : metal) + "_bars";
            JsonObject states = resource("blockstates/iron_bars.json", PackType.CLIENT_RESOURCES);
            replaceStrings(states, "minecraft:block/iron_bars", "mite:block/" + name);
            save(tasks, cache, "assets/mite/blockstates/" + name + ".json", states.toString());
            for (String part : List.of("post", "post_ends", "cap", "cap_alt", "side", "side_alt"))
                save(tasks, cache, "assets/mite/models/block/" + name + "_" + part + ".json", "{\"parent\":\"minecraft:block/iron_bars_" + part
                        + "\",\"render_type\":\"minecraft:cutout\",\"textures\":{\"particle\":\"mite:block/" + metal + "_bars\",\"bars\":\"mite:block/" + metal + "_bars\"}}");
            save(tasks, cache, "assets/mite/models/item/" + name + ".json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mite:block/" + metal + "_bars\"}}");
            save(tasks, cache, "data/mite/loot_table/blocks/" + name + ".json", selfLoot("mite:" + name));
        }
        for (String row : List.of("mite:dahlia:dahlia", "minecraft:allium:allium", "minecraft:orange_tulip:tulip", "minecraft:blue_orchid:orchid", "minecraft:oxeye_daisy:daisy", "mite:witherwood_bush:witherwood")) {
            String[] parts = row.split(":"); String ns = parts[0], name = parts[1], texture = parts[2];
            String texturePath = texture.equals("witherwood") ? texture : "flowers/" + texture;
            save(tasks, cache, "assets/" + ns + "/models/block/" + name + ".json", "{\"parent\":\"minecraft:block/cross\",\"render_type\":\"minecraft:cutout\",\"textures\":{\"cross\":\"mite:block/" + texturePath + "\"}}");
            save(tasks, cache, "assets/" + ns + "/models/item/" + name + ".json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mite:block/" + texturePath + "\"}}");
            if (ns.equals("mite")) {
                save(tasks, cache, "assets/mite/blockstates/" + name + ".json", "{\"variants\":{\"\":{\"model\":\"mite:block/" + name + "\"}}}");
                save(tasks, cache, "data/mite/loot_table/blocks/" + name + ".json", name.equals("witherwood_bush") ? witherLoot() : selfLoot("mite:" + name));
            }
        }
        save(tasks, cache, "assets/mite/blockstates/potted_dahlia.json", "{\"variants\":{\"\":{\"model\":\"mite:block/potted_dahlia\"}}}");
        save(tasks, cache, "assets/mite/models/block/potted_dahlia.json", "{\"parent\":\"minecraft:block/flower_pot_cross\",\"render_type\":\"minecraft:cutout\",\"textures\":{\"plant\":\"mite:block/flowers/dahlia\"}}");
        save(tasks, cache, "data/mite/loot_table/blocks/potted_dahlia.json", "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:flower_pot\"}]},{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mite:dahlia\"}]}]}");
        // Source adr turns all Nether generated gravel into metadata 2. Keep the modern rules
        // and noise router byte-for-byte semantically, replacing only that block-state identity.
        JsonObject nether = resource("worldgen/noise_settings/nether.json", PackType.SERVER_DATA);
        DecorativeResourceRules.replaceBlockIdentity(nether.get("surface_rule"), "minecraft:gravel", "mite:nether_gravel");
        save(tasks, cache, "data/minecraft/worldgen/noise_settings/nether.json", nether.toString());
        for (String plant : List.of("dahlia", "allium", "witherwood_bush")) {
            String id = "patch_" + plant;
            String state = plant.equals("allium") ? "minecraft:allium" : "mite:" + plant;
            save(tasks, cache, "data/mite/worldgen/configured_feature/" + id + ".json", "{\"type\":\"mite:r196_decorative_patch\",\"config\":{\"state\":{\"Name\":\"" + state + "\"}}}");
            save(tasks, cache, "data/mite/worldgen/placed_feature/" + id + ".json", "{\"feature\":\"mite:" + id + "\",\"placement\":[]}");
            String biomes = plant.equals("witherwood_bush") ? "#minecraft:is_nether" : "#minecraft:is_overworld";
            save(tasks, cache, "data/mite/neoforge/biome_modifier/" + id + ".json", "{\"type\":\"neoforge:add_features\",\"biomes\":\"" + biomes + "\",\"features\":[\"mite:" + id + "\"],\"step\":\"vegetal_decoration\"}");
        }
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }
    private static String selfLoot(String id) {
        return "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}],\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + id + "\"}]}]}";
    }
    private static String witherLoot() {
        return "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"name\":\"mite:witherwood_bush\",\"conditions\":[{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"#mite:shears\"}},{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}]},{\"type\":\"minecraft:item\",\"name\":\"minecraft:stick\",\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.5}]}]}]}]}";
    }
    private JsonObject resource(String path, PackType type) {
        var location = ResourceLocation.withDefaultNamespace(path);
        var resource = existingFiles.getResourceStack(location, type).stream().filter(entry -> entry.sourcePackId().equals("vanilla")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned vanilla resource: " + path));
        try (InputStream input = resource.open()) { return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (Exception error) { throw new IllegalStateException("Cannot read pinned native geometry/worldgen", error); }
    }
    static void replaceStrings(JsonElement element, String from, String to) {
        if (element == null) return;
        if (element.isJsonObject()) for (var entry : element.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                String value = entry.getValue().getAsString();
                if (value.startsWith(from)) entry.setValue(new com.google.gson.JsonPrimitive(to + value.substring(from.length())));
            } else replaceStrings(entry.getValue(), from, to);
        }
        else if (element.isJsonArray()) element.getAsJsonArray().forEach(entry -> replaceStrings(entry, from, to));
    }
    private void save(List<CompletableFuture<?>> tasks, CachedOutput cache, String path, String json) {
        tasks.add(DataProvider.saveStable(cache, JsonParser.parseString(json), output.getOutputFolder().resolve(path)));
    }
    @Override public String getName() { return "MITE R196 decorative content"; }
}
