package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.advancement.StructureAdvancements;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
        display.add("description", description(path));
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

    private static JsonObject description(String path) {
        var text = translated("advancement.mite.structures." + path + ".description");
        var entry = StructureAdvancements.entry(path);
        if (entry == null) return text;
        var extra = new JsonArray();
        line(extra, translated("structure_progress.mite.enabled"));
        line(extra, translated("structure_progress.mite.required_date", entry.minimumDay()));
        for (var need : entry.flagRequirements()) line(extra, translated("structure_progress.mite." + need.criterion()));
        for (var need : entry.definition().requirements()) {
            var label = translated("structure_progress.mite." + need.metric().key());
            line(extra, need.metric().distance()
                    ? translated("structure_progress.mite.required_distance", label, String.format(Locale.ROOT, "%.2f", need.target() / 100D))
                    : translated("structure_progress.mite.required_count", label, need.target()));
        }
        line(extra, translated("structure_progress.mite.new_chunks"));
        text.add("extra", extra);
        return text;
    }

    private static void line(JsonArray lines, JsonObject text) {
        var newline = new JsonObject();
        newline.addProperty("text", "\n");
        lines.add(newline);
        lines.add(text);
    }

    private static JsonObject translated(String key, Object... arguments) {
        var component = new JsonObject();
        component.addProperty("translate", key);
        if (arguments.length > 0) {
            var values = new JsonArray();
            for (Object argument : arguments) {
                if (argument instanceof JsonObject object) values.add(object);
                else if (argument instanceof Number number) values.add(number);
                else values.add(argument.toString());
            }
            component.add("with", values);
        }
        return component;
    }
}
