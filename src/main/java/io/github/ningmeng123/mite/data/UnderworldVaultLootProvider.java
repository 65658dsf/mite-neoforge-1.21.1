package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** User-balanced underworld vault rewards, isolated from vanilla vaults. @author NingMeng123 */
public final class UnderworldVaultLootProvider implements DataProvider {
    private static final String CONFIG_RESOURCE = "/mite/underworld-vault-loot.json";
    private final PackOutput output;
    private final ExistingFileHelper existingFiles;

    public UnderworldVaultLootProvider(PackOutput output, ExistingFileHelper existingFiles) {
        this.output = output;
        this.existingFiles = existingFiles;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonObject config = configuration();
        String sourcePrefix = config.get("sourcePrefix").getAsString();
        String targetPrefix = config.get("targetPrefix").getAsString();
        int weightScale = positive(config, "weightScale");
        Map<String, JsonObject> tables = new LinkedHashMap<>();
        for (JsonElement name : config.getAsJsonArray("tables")) {
            String suffix = name.getAsString();
            JsonObject table = vanilla(sourcePrefix + suffix);
            remapReferences(table, sourcePrefix, targetPrefix);
            replaceItems(table, config.getAsJsonObject("itemReplacements"));
            if (config.getAsJsonObject("weightOverrides").has(suffix)) {
                JsonObject overrides = config.getAsJsonObject("weightOverrides").getAsJsonObject(suffix);
                for (JsonElement entryElement : entries(table)) {
                    JsonObject entry = entryElement.getAsJsonObject();
                    int weight = entry.has("weight") ? positive(entry, "weight") : 1;
                    String item = entry.get("name").getAsString();
                    entry.addProperty("weight", overrides.has(item) ? positive(overrides, item) : Math.multiplyExact(weight, weightScale));
                }
            }
            if (config.getAsJsonObject("uniquePools").has(suffix)) {
                JsonArray replacements = new JsonArray();
                for (JsonElement row : config.getAsJsonObject("uniquePools").getAsJsonArray(suffix)) {
                    JsonObject definition = row.getAsJsonObject();
                    replacements.add(itemEntry(definition.get("item").getAsString(), positive(definition, "weight"),
                            positive(definition, "minCount"), positive(definition, "maxCount")));
                }
                table.getAsJsonArray("pools").get(0).getAsJsonObject().add("entries", replacements);
            }
            tables.put(suffix, table);
        }

        JsonObject arrows = config.getAsJsonObject("materialArrows");
        int baseWeight = positive(arrows, "inverseWeightBase");
        for (JsonElement poolElement : arrows.getAsJsonArray("pools")) {
            JsonObject definition = poolElement.getAsJsonObject();
            String suffix = definition.get("table").getAsString();
            String tableId = targetPrefix + suffix;
            JsonArray entries = new JsonArray();
            for (JsonElement materialElement : arrows.getAsJsonArray("materials")) {
                JsonObject material = materialElement.getAsJsonObject();
                int rarity = positive(material, "rarityFactor");
                if (baseWeight % rarity != 0) throw new IllegalStateException("Arrow rarity must divide inverseWeightBase exactly: " + material);
                entries.add(itemEntry(material.get("item").getAsString(), baseWeight / rarity,
                        positive(definition, "minCount"), positive(definition, "maxCount")));
            }
            tables.put(suffix, singlePoolTable(tableId, entries));
            JsonArray parentEntries = entries(tables.get(definition.get("parentTable").getAsString()));
            if (definition.has("replaceItem")) {
                String oldItem = definition.get("replaceItem").getAsString();
                boolean found = false;
                for (int index = 0; index < parentEntries.size(); index++) {
                    JsonObject old = parentEntries.get(index).getAsJsonObject();
                    if (old.has("name") && old.get("name").getAsString().equals(oldItem)) {
                        // Quantities belong solely to the selected child item, not the table reference.
                        parentEntries.set(index, tableEntry(tableId, positive(old, "weight")));
                        found = true;
                    }
                }
                if (!found) throw new IllegalStateException("Pinned material-arrow parent entry missing: " + oldItem);
            } else {
                parentEntries.add(tableEntry(tableId, positive(definition, "insertWeight")));
            }
        }

        List<CompletableFuture<?>> work = new ArrayList<>();
        for (var entry : tables.entrySet()) {
            rejectRemovedRewards(entry.getValue(), config.getAsJsonArray("removedItemPathFragments"));
            ResourceLocation id = ResourceLocation.parse(targetPrefix + entry.getKey());
            work.add(DataProvider.saveStable(cache, entry.getValue(), output.getOutputFolder()
                    .resolve("data/" + id.getNamespace() + "/loot_table/" + id.getPath() + ".json")));
        }
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    private static JsonArray entries(JsonObject table) {
        return table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
    }

    private static JsonObject itemEntry(String item, int weight, int min, int max) {
        if (max < min) throw new IllegalStateException("Invalid loot count range: " + min + ".." + max);
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", item);
        entry.addProperty("weight", weight);
        if (min != 1 || max != 1) {
            JsonObject count = new JsonObject();
            count.addProperty("type", "minecraft:uniform");
            count.addProperty("min", min);
            count.addProperty("max", max);
            JsonObject function = new JsonObject();
            function.addProperty("function", "minecraft:set_count");
            function.addProperty("add", false);
            function.add("count", count);
            JsonArray functions = new JsonArray();
            functions.add(function);
            entry.add("functions", functions);
        }
        return entry;
    }

    private static JsonObject tableEntry(String id, int weight) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:loot_table");
        entry.addProperty("value", id);
        entry.addProperty("weight", weight);
        return entry;
    }

    private static JsonObject singlePoolTable(String id, JsonArray entries) {
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.addProperty("bonus_rolls", 0);
        pool.add("entries", entries);
        JsonArray pools = new JsonArray();
        pools.add(pool);
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:chest");
        table.addProperty("random_sequence", id);
        table.add("pools", pools);
        return table;
    }

    private static void remapReferences(JsonElement node, String sourcePrefix, String targetPrefix) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            for (var entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && value.getAsString().startsWith(sourcePrefix)) {
                    entry.setValue(new com.google.gson.JsonPrimitive(targetPrefix + value.getAsString().substring(sourcePrefix.length())));
                } else remapReferences(value, sourcePrefix, targetPrefix);
            }
        } else if (node.isJsonArray()) for (JsonElement child : node.getAsJsonArray()) remapReferences(child, sourcePrefix, targetPrefix);
    }

    private static void replaceItems(JsonElement node, JsonObject replacements) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (object.has("type") && object.get("type").getAsString().equals("minecraft:item") && object.has("name")) {
                String item = object.get("name").getAsString();
                if (replacements.has(item)) object.addProperty("name", replacements.get(item).getAsString());
            }
            for (var child : object.entrySet()) replaceItems(child.getValue(), replacements);
        } else if (node.isJsonArray()) for (JsonElement child : node.getAsJsonArray()) replaceItems(child, replacements);
    }

    private static void rejectRemovedRewards(JsonElement node, JsonArray removedFragments) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (object.has("type") && object.get("type").getAsString().equals("minecraft:item") && object.has("name")) {
                String item = object.get("name").getAsString();
                for (JsonElement fragment : removedFragments) if (item.contains(fragment.getAsString()))
                    throw new IllegalStateException("Forbidden underworld vault reward: " + item);
            }
            for (var child : object.entrySet()) rejectRemovedRewards(child.getValue(), removedFragments);
        } else if (node.isJsonArray()) for (JsonElement child : node.getAsJsonArray()) rejectRemovedRewards(child, removedFragments);
    }

    private static int positive(JsonObject object, String key) {
        int value = object.get(key).getAsInt();
        if (value <= 0) throw new IllegalStateException("Expected positive loot value: " + key);
        return value;
    }

    private static JsonObject configuration() {
        try (InputStream input = UnderworldVaultLootProvider.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing underworld vault configuration " + CONFIG_RESOURCE);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read underworld vault configuration", exception);
        }
    }

    private JsonObject vanilla(String id) {
        ResourceLocation source = ResourceLocation.parse(id);
        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(source.getNamespace(), "loot_table/" + source.getPath() + ".json");
        var resource = existingFiles.getResourceStack(path, PackType.SERVER_DATA).stream()
                .filter(entry -> entry.sourcePackId().equals("vanilla")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned vanilla vault resource: " + id));
        try (InputStream input = resource.open()) {
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read pinned vanilla vault resource: " + id, exception);
        }
    }

    @Override public String getName() { return "MITE underworld vault loot"; }
}
