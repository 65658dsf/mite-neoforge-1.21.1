package io.github.ningmeng123.mite.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
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

/** Missing source ores and every corresponding vanilla ore use a shared R196 loot serializer. @author NingMeng123 */
public final class OreAssetsProvider implements DataProvider {
    private final PackOutput output;
    private final ExistingFileHelper existingFiles;
    public OreAssetsProvider(PackOutput output, ExistingFileHelper existingFiles) {
        this.output = output; this.existingFiles = existingFiles;
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        for (String metal : List.of("silver", "mithril", "adamantium")) {
            for (String prefix : List.of("", "deepslate_")) {
                String ore = prefix + metal + "_ore";
                save(work, cache, "assets/mite/blockstates/" + ore + ".json", "{\"variants\":{\"\":{\"model\":\"mite:block/" + ore + "\"}}}");
                save(work, cache, "assets/mite/models/block/" + ore + ".json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"mite:block/" + ore + "\"}}");
                save(work, cache, "assets/mite/models/item/" + ore + ".json", "{\"parent\":\"mite:block/" + ore + "\"}");
                loot(work, cache, "mite", ore, true);
                newMetalCooking(work, cache, ore, "mite:" + metal + "_ingot", false);
                newMetalCooking(work, cache, ore, "mite:" + metal + "_ingot", true);
            }
            String raw = "raw_" + metal;
            save(work, cache, "assets/mite/models/item/" + raw + ".json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mite:item/" + raw + "\"}}");
            newMetalCooking(work, cache, raw, "mite:" + metal + "_ingot", false);
            newMetalCooking(work, cache, raw, "mite:" + metal + "_ingot", true);
            tag(work, cache, "item", "raw_materials/" + metal, "mite:" + raw);
        }
        tag(work, cache, "item", "raw_materials", "#c:raw_materials/silver", "#c:raw_materials/mithril", "#c:raw_materials/adamantium");
        for (String metal : List.of("copper", "iron", "gold")) {
            vanillaCooking(work, cache, metal + "_ingot_from_smelting_raw_" + metal, 1, false);
            vanillaCooking(work, cache, metal + "_ingot_from_blasting_raw_" + metal, 1, true);
        }
        for (String ore : List.of("coal_ore", "copper_ore", "iron_ore", "gold_ore", "lapis_ore", "redstone_ore", "diamond_ore", "emerald_ore")) {
            boolean metal = ore.equals("copper_ore") || ore.equals("iron_ore") || ore.equals("gold_ore");
            loot(work, cache, "minecraft", ore, metal);
            loot(work, cache, "minecraft", "deepslate_" + ore, metal);
            String result = switch (ore) {
                case "coal_ore" -> "coal";
                case "copper_ore" -> "copper_ingot";
                case "iron_ore" -> "iron_ingot";
                case "gold_ore" -> "gold_ingot";
                case "lapis_ore" -> "lapis_lazuli";
                case "redstone_ore" -> "redstone";
                case "diamond_ore" -> "diamond";
                case "emerald_ore" -> "emerald";
                default -> throw new IllegalArgumentException(ore);
            };
            int quantity = ore.equals("lapis_ore") || ore.equals("redstone_ore") ? 4 : 1;
            for (String host : List.of(ore, "deepslate_" + ore)) {
                vanillaCooking(work, cache, result + "_from_smelting_" + host, quantity, false);
                vanillaCooking(work, cache, result + "_from_blasting_" + host, quantity, true);
            }
        }
        loot(work, cache, "minecraft", "nether_gold_ore", true);
        loot(work, cache, "minecraft", "nether_quartz_ore", false);
        vanillaCooking(work, cache, "gold_ingot_from_smelting_nether_gold_ore", 1, false);
        vanillaCooking(work, cache, "gold_ingot_from_blasting_nether_gold_ore", 1, true);
        // Quartz's pinned vanilla recipe IDs are exceptional: quartz / quartz_from_blasting.
        vanillaCooking(work, cache, "quartz", 1, false);
        vanillaCooking(work, cache, "quartz_from_blasting", 1, true);
        save(work, cache, "assets/mite/blockstates/infested_netherrack.json", "{\"variants\":{\"\":{\"model\":\"minecraft:block/netherrack\"}}}");
        save(work, cache, "assets/mite/models/item/infested_netherrack.json", "{\"parent\":\"minecraft:block/netherrack\"}");
        save(work, cache, "data/mite/loot_table/blocks/infested_netherrack.json",
                "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:netherrack\","
                + "\"conditions\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"predicates\":{\"minecraft:enchantments\":[{\"enchantments\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}}]}]}]}");
        for (String metal : List.of("silver", "mithril", "adamantium")) {
            tag(work, cache, "block", "ores/" + metal, "mite:" + metal + "_ore", "mite:deepslate_" + metal + "_ore");
            tag(work, cache, "item", "ores/" + metal, "mite:" + metal + "_ore", "mite:deepslate_" + metal + "_ore");
        }
        tag(work, cache, "block", "ores", "#c:ores/silver", "#c:ores/mithril", "#c:ores/adamantium");
        tag(work, cache, "item", "ores", "#c:ores/silver", "#c:ores/mithril", "#c:ores/adamantium");
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }
    private void loot(List<CompletableFuture<?>> work, CachedOutput cache, String namespace, String ore, boolean placed) {
        save(work, cache, "data/" + namespace + "/loot_table/blocks/" + ore + ".json",
                "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + namespace + ":" + ore
                        + "\",\"functions\":[{\"function\":\"mite:r196_ore_bonus\",\"suppress_placed\":" + placed + "}]}]}]}");
    }
    private void newMetalCooking(List<CompletableFuture<?>> work, CachedOutput cache, String ore, String result, boolean blasting) {
        // Heat/fuel/furnace tiers remain tracked separately. The modern blast furnace keeps its 100-tick cycle.
        JsonObject json = new JsonObject();
        json.addProperty("type", blasting ? "minecraft:blasting" : "minecraft:smelting"); json.addProperty("category", "misc");
        JsonObject ingredient = new JsonObject(); ingredient.addProperty("item", "mite:" + ore); json.add("ingredient", ingredient);
        JsonObject outputItem = new JsonObject(); outputItem.addProperty("id", result); outputItem.addProperty("count", 1); json.add("result", outputItem);
        json.addProperty("experience", 0F); json.addProperty("cookingtime", blasting ? 100 : 200);
        save(work, cache, "data/mite/recipe/" + ore + (blasting ? "_from_blasting" : "_from_smelting") + ".json", json.toString());
    }
    private void vanillaCooking(List<CompletableFuture<?>> work, CachedOutput cache, String id, int quantity, boolean blasting) {
        String path = "data/minecraft/recipe/" + id + ".json";
        var location = ResourceLocation.withDefaultNamespace("recipe/" + id + ".json");
        var vanilla = existingFiles.getResourceStack(location, PackType.SERVER_DATA).stream()
                .filter(resource -> resource.sourcePackId().equals("vanilla")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned Minecraft 1.21.1 recipe: " + path));
        try (InputStream input = vanilla.open()) {
            JsonObject json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            json.getAsJsonObject("result").addProperty("count", quantity);
            json.addProperty("experience", 0F);
            json.addProperty("cookingtime", blasting ? 100 : 200);
            save(work, cache, path, json.toString());
        } catch (IOException exception) { throw new IllegalStateException("Cannot read pinned mineral recipe: " + path, exception); }
    }
    private void tag(List<CompletableFuture<?>> work, CachedOutput cache, String registry, String name, String... values) {
        JsonObject json = new JsonObject(); json.addProperty("replace", false);
        var array = new com.google.gson.JsonArray(); for (String value : values) array.add(value); json.add("values", array);
        save(work, cache, "data/c/tags/" + registry + "/" + name + ".json", json.toString());
    }
    private void save(List<CompletableFuture<?>> work, CachedOutput cache, String path, String json) {
        work.add(DataProvider.saveStable(cache, JsonParser.parseString(json), output.getOutputFolder().resolve(path)));
    }
    @Override public String getName() { return "MITE R196 mineral assets and outputs"; }
}
