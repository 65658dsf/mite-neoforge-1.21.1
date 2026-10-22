package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Keep native advancement identities and completed criteria while replacing retired equipment. @author NingMeng123 */
public final class DiamondEquipmentAdvancements {
    private DiamondEquipmentAdvancements() {}

    public static void addOverrides(CachedOutput cache, Path output, ExistingFileHelper existingFiles,
            List<CompletableFuture<?>> writes) {
        for (String path : List.of("story/shiny_gear", "adventure/adventuring_time", "adventure/kill_all_mobs",
                "adventure/lighten_up", "husbandry/wax_off", "husbandry/obtain_netherite_hoe",
                "nether/explore_nether", "nether/netherite_armor")) {
            ResourceLocation source = ResourceLocation.withDefaultNamespace("advancement/" + path + ".json");
            var vanilla = existingFiles.getResourceStack(source, PackType.SERVER_DATA).stream()
                    .filter(resource -> resource.sourcePackId().equals("vanilla")).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing pinned vanilla advancement: " + path));
            try (var input = vanilla.open()) {
                JsonObject original = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject adapted = replaceEquipment(original).getAsJsonObject();
                if (adapted.equals(original)) throw new IllegalStateException("Pinned advancement lost its expected equipment reference: " + path);
                if (path.equals("story/shiny_gear")) {
                    displayText(adapted, "远古金属护体", "获得一件远古金属盔甲");
                } else if (path.equals("husbandry/obtain_netherite_hoe")) {
                    displayText(adapted, "秘银耕作", "获得一把秘银锄");
                } else if (path.equals("nether/netherite_armor")) {
                    displayText(adapted, "秘银全副武装", "获得一整套秘银盔甲");
                }
                writes.add(DataProvider.saveStable(cache, adapted,
                        output.resolve("data/minecraft/advancement/" + path + ".json")));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot adapt pinned vanilla advancement: " + path, exception);
            }
        }
    }

    private static void displayText(JsonObject advancement, String title, String description) {
        var titleText = new JsonObject(); titleText.addProperty("text", title);
        var descriptionText = new JsonObject(); descriptionText.addProperty("text", description);
        advancement.getAsJsonObject("display").add("title", titleText);
        advancement.getAsJsonObject("display").add("description", descriptionText);
    }

    private static JsonElement replaceEquipment(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().entrySet().forEach(entry -> result.add(entry.getKey(), replaceEquipment(entry.getValue())));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(element -> result.add(replaceEquipment(element)));
            return result;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String item = value.getAsString();
            ResourceLocation id = ResourceLocation.tryParse(item);
            if (item.startsWith("minecraft:") && id != null && RemovedDiamondEquipment.IDS.contains(id)
                    && (id.getPath().startsWith("diamond_") || id.getPath().startsWith("netherite_"))) {
                String material = id.getPath().startsWith("diamond_") ? "diamond" : "netherite";
                String replacement = material.equals("diamond") ? "ancient_metal" : "mithril";
                return new JsonPrimitive("mite:" + replacement + "_" + id.getPath().substring(material.length() + 1));
            }
        }
        return value;
    }
}
