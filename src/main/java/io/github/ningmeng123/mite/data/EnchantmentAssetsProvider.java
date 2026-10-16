package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog.Definition;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Source metadata and supported-item tags share the server's audited catalogue. @author NingMeng123 */
public final class EnchantmentAssetsProvider implements DataProvider {
    private static final List<String> MODERN_EQUIPMENT = List.of("minecraft:crossbow", "minecraft:trident", "minecraft:mace", "minecraft:shield", "minecraft:elytra");
    private final Path output;
    private final ExistingFileHelper existingFiles;
    private final Map<String, Set<String>> vanillaItemTags = new LinkedHashMap<>();

    public EnchantmentAssetsProvider(PackOutput output, ExistingFileHelper existingFiles) {
        this.output = output.getOutputFolder();
        this.existingFiles = existingFiles;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        JsonArray all = new JsonArray(), added = new JsonArray();
        for (Definition definition : R196EnchantmentCatalog.definitions()) {
            ResourceLocation location = ResourceLocation.parse(definition.resourceId());
            boolean vanilla = location.getNamespace().equals("minecraft");
            JsonObject data = vanilla ? vanilla(definition.id()) : new JsonObject();
            if (!vanilla) {
                data.addProperty("anvil_cost", 2);
                data.add("description", object("translate", "enchantment.mite." + definition.id()));
                data.add("effects", new JsonObject());
            }
            data.addProperty("max_level", definition.maxLevel());
            data.addProperty("weight", definition.weight());
            data.add("min_cost", cost(definition.minCost(1), definition.maxLevel() == 1 ? 0 : definition.difficulty()));
            data.add("max_cost", cost(Integer.MAX_VALUE, 0));
            String sourceTag = "#mite:enchantable/" + definition.id();
            if (vanilla) {
                // Source tools retain exact source support. Devices introduced after 1.6.4 keep their own native support.
                JsonElement originalSupport = data.get("supported_items");
                JsonElement originalPrimary = data.has("primary_items") ? data.get("primary_items") : originalSupport;
                Set<String> modernSupport = modernItems(originalSupport), modernPrimary = modernItems(originalPrimary);
                String compatible = "#mite:enchantable/native_compat/" + definition.id();
                save(cache, work, "data/mite/tags/item/enchantable/native_compat/" + definition.id() + ".json", unionTag(sourceTag, modernSupport));
                data.addProperty("supported_items", compatible);
                if (modernPrimary.equals(modernSupport)) data.addProperty("primary_items", compatible);
                else {
                    String primary = "#mite:enchantable/native_primary/" + definition.id();
                    save(cache, work, "data/mite/tags/item/enchantable/native_primary/" + definition.id() + ".json", unionTag(sourceTag, modernPrimary));
                    data.addProperty("primary_items", primary);
                }
            } else {
                data.addProperty("supported_items", sourceTag);
                data.addProperty("primary_items", sourceTag);
            }
            data.add("slots", strings(slots(definition.id())));
            if (definition.id().equals("silk_touch")) data.add("exclusive_set", strings("minecraft:fortune", "minecraft:looting"));
            if (definition.id().equals("looting")) data.add("exclusive_set", strings("minecraft:silk_touch"));
            if (definition.id().equals("fortune")) data.add("exclusive_set", strings("minecraft:silk_touch"));
            sourceEffects(definition.id(), data);
            save(cache, work, "data/" + location.getNamespace() + "/enchantment/" + location.getPath() + ".json", data);

            JsonArray items = new JsonArray();
            BuiltInRegistries.ITEM.forEach(item -> {
                if (!RemovedDiamondEquipment.isRemoved(item) && definition.supported(new ItemStack(item)))
                    items.add(BuiltInRegistries.ITEM.getKey(item).toString());
            });
            save(cache, work, "data/mite/tags/item/enchantable/" + definition.id() + ".json", tag(true, items));
            all.add(definition.resourceId());
            if (!vanilla) added.add(definition.resourceId());
        }
        save(cache, work, "data/mite/tags/enchantment/r196_source.json", tag(true, all));
        // Keep the original agriculture integration tag for other crop/data consumers.
        JsonArray agriculture = new JsonArray();
        BuiltInRegistries.ITEM.forEach(item -> {
            if (!RemovedDiamondEquipment.isRemoved(item) && R196EnchantmentCatalog.supported("harvesting", new ItemStack(item)))
                agriculture.add(BuiltInRegistries.ITEM.getKey(item).toString());
        });
        save(cache, work, "data/mite/tags/item/agricultural_tools.json", tag(false, agriculture));
        for (String id : List.of("in_enchanting_table", "tradeable", "on_random_loot", "non_treasure"))
            save(cache, work, "data/minecraft/tags/enchantment/" + id + ".json", tag(false, added));
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    private static String[] slots(String id) {
        return switch (id) {
            case "protection", "fire_protection" -> new String[]{"armor"};
            case "blast_protection", "projectile_protection" -> new String[]{"chest", "legs"};
            case "respiration", "aqua_affinity" -> new String[]{"head"};
            case "feather_falling", "speed" -> new String[]{"feet"};
            case "thorns", "regeneration", "endurance" -> new String[]{"chest"};
            case "free_action" -> new String[]{"legs"};
            case "unbreaking" -> new String[]{"any"};
            default -> new String[]{"mainhand"};
        };
    }

    /** Retain modern plumbing where its effect matches the source; replace only changed source arithmetic. */
    private static void sourceEffects(String id, JsonObject data) {
        JsonObject effects = data.has("effects") ? data.getAsJsonObject("effects") : new JsonObject();
        switch (id) {
            case "sharpness" -> changeDamage(effects, 1F, 1F);
            case "smite", "bane_of_arthropods" -> {
                changeDamage(effects, 2F, 2F);
                if (id.equals("bane_of_arthropods")) effects.remove("minecraft:post_attack");
            }
            case "power" -> changeDamage(effects, 1F, .5F);
            // nn.d converts source seconds to ticks and appends ten ticks.
            case "fire_aspect" -> effects.getAsJsonArray("minecraft:post_attack").get(0).getAsJsonObject()
                    .getAsJsonObject("effect").getAsJsonObject("duration").addProperty("base", 4.5F);
            case "flame" -> effects.getAsJsonArray("minecraft:projectile_spawned").get(0).getAsJsonObject()
                    .getAsJsonObject("effect").addProperty("duration", 100.5F);
            // Native attributes stack across armor pieces; source helpers select the highest worn level.
            case "fire_protection", "blast_protection" -> effects.remove("minecraft:attributes");
            // R196 hit chance, 1..4 retaliation and exact 3/1 durability wear run in the server handler.
            case "thorns" -> effects = new JsonObject();
            default -> { }
        }
        data.add("effects", effects);
    }

    private static void changeDamage(JsonObject effects, float base, float perLevel) {
        JsonArray damage = effects.getAsJsonArray("minecraft:damage");
        if (damage == null || damage.isEmpty()) throw new IllegalStateException("Pinned vanilla enchantment has no expected damage effect");
        for (JsonElement row : damage) {
            JsonObject value = object("type", "minecraft:linear");
            value.addProperty("base", base);
            value.addProperty("per_level_above_first", perLevel);
            row.getAsJsonObject().getAsJsonObject("effect").add("value", value);
        }
    }

    private JsonObject vanilla(String id) {
        return vanillaResource(ResourceLocation.withDefaultNamespace("enchantment/" + id + ".json"));
    }

    private JsonObject vanillaResource(ResourceLocation path) {
        var resource = existingFiles.getResourceStack(path, PackType.SERVER_DATA).stream()
                .filter(entry -> entry.sourcePackId().equals("vanilla")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned vanilla enchantment resource: " + path));
        try (InputStream input = resource.open()) {
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read pinned vanilla enchantment resource: " + path, exception);
        }
    }

    private Set<String> modernItems(JsonElement itemSet) {
        Set<String> nativeItems = expandNativeItems(itemSet);
        Set<String> modern = new LinkedHashSet<>();
        for (String id : MODERN_EQUIPMENT) if (nativeItems.contains(id)) modern.add(id);
        return modern;
    }

    private Set<String> expandNativeItems(JsonElement itemSet) {
        Set<String> result = new LinkedHashSet<>();
        if (itemSet.isJsonArray()) {
            for (JsonElement item : itemSet.getAsJsonArray()) result.addAll(expandNativeItems(item));
            return result;
        }
        String name = itemSet.isJsonObject() ? itemSet.getAsJsonObject().get("id").getAsString() : itemSet.getAsString();
        if (!name.startsWith("#")) return Set.of(name);
        String tag = name.substring(1);
        if (vanillaItemTags.containsKey(tag)) return vanillaItemTags.get(tag);
        ResourceLocation location = ResourceLocation.parse(tag);
        JsonArray entries = vanillaResource(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "tags/item/" + location.getPath() + ".json"))
                .getAsJsonArray("values");
        // Reserve before traversing references, so an accidental cycle cannot recurse forever.
        vanillaItemTags.put(tag, result);
        for (JsonElement entry : entries) result.addAll(expandNativeItems(entry));
        return result;
    }

    private static JsonObject unionTag(String sourceTag, Set<String> modern) {
        JsonArray entries = new JsonArray(); entries.add(sourceTag); modern.forEach(entries::add);
        return tag(true, entries);
    }

    private static JsonObject cost(int base, int perLevel) {
        JsonObject cost = new JsonObject();
        cost.addProperty("base", base);
        cost.addProperty("per_level_above_first", perLevel);
        return cost;
    }

    private static JsonObject tag(boolean replace, JsonArray values) {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", replace);
        tag.add("values", values);
        return tag;
    }

    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    private static JsonObject object(String key, String value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private void save(CachedOutput cache, List<CompletableFuture<?>> work, String path, JsonObject data) {
        work.add(DataProvider.saveStable(cache, data, output.resolve(path)));
    }

    @Override public String getName() { return "MITE R196 enchantment identities, effects and source item support"; }
}
