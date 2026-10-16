package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.advancement.StructureAdvancements;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.ResourceLocation;

/** Visible structure requirements alongside the MITE achievement tree. @author NingMeng123 */
public final class StructureAdvancementAssets {
    private StructureAdvancementAssets() {}

    public static void addAdvancements(CachedOutput cache, Path output, List<CompletableFuture<?>> writes) {
        add(cache, output, writes, StructureAdvancements.GUIDE_ID, "mite:r196/open_inventory",
                "root", "minecraft:map", List.of("visible"));
        for (var entry : StructureAdvancements.Entry.values()) {
            add(cache, output, writes, entry.id(), StructureAdvancements.GUIDE_ID.toString(),
                    entry.path(), entry.icon(), entry.criteria());
        }
    }

    private static void add(CachedOutput cache, Path output, List<CompletableFuture<?>> writes,
            ResourceLocation id, String parent, String path, String item, List<String> conditions) {
        var json = new JsonObject();
        json.addProperty("parent", parent);
        var display = new JsonObject();
        var icon = new JsonObject();
        icon.addProperty("id", item);
        display.add("icon", icon);
        display.add("title", translated("advancement.mite.structures." + path + ".title"));
        display.add("description", translated("advancement.mite.structures." + path + ".description"));
        display.addProperty("frame", "task");
        display.addProperty("show_toast", false);
        display.addProperty("announce_to_chat", false);
        display.addProperty("hidden", false);
        json.add("display", display);
        var criteria = new JsonObject();
        var requirements = new JsonArray();
        for (String condition : conditions) {
            var trigger = new JsonObject();
            trigger.addProperty("trigger", "minecraft:impossible");
            criteria.add(condition, trigger);
            var requirement = new JsonArray();
            requirement.add(condition);
            requirements.add(requirement);
        }
        json.add("criteria", criteria);
        json.add("requirements", requirements);
        writes.add(DataProvider.saveStable(cache, json,
                output.resolve("data/" + id.getNamespace() + "/advancement/" + id.getPath() + ".json")));
    }

    private static JsonObject translated(String key) {
        var component = new JsonObject();
        component.addProperty("translate", key);
        return component;
    }
}
