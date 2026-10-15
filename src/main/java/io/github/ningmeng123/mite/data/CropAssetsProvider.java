package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Disease models, source crop loot and agricultural enchantment data. @author NingMeng123 */
public final class CropAssetsProvider implements DataProvider {
    private final Path output;

    public CropAssetsProvider(PackOutput output) { this.output = output.getOutputFolder(); }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> work = new ArrayList<>();
        for (String crop : List.of("wheat", "carrots", "potatoes", "beetroots")) {
            cropBlockstate(cache, work, crop);
            save(cache, work, "data/minecraft/loot_table/blocks/" + crop + ".json", cropLoot(crop));
        }
        for (String crop : List.of("wheat", "carrots", "potatoes")) {
            int blighted = crop.equals("wheat") ? 8 : 4;
            int dead = crop.equals("wheat") ? 7 : 3;
            for (int stage = 0; stage < blighted; stage++) cropModel(cache, work, crop, "blighted", stage);
            for (int stage = 0; stage < dead; stage++) cropModel(cache, work, crop, "dead", stage);
        }
        farmland(cache, work);
        agricultureEnchantments(cache, work);
        save(cache, work, "data/minecraft/loot_table/blocks/melon.json", melonLoot());
        for (String fruit : List.of("melon", "pumpkin")) {
            JsonObject loot = object("type", "minecraft:block");
            JsonObject pool = new JsonObject(); pool.addProperty("rolls", 1);
            pool.add("entries", array(item("minecraft:" + fruit + "_seeds", 1))); loot.add("pools", array(pool));
            save(cache, work, "data/mite/loot_table/crop_explosion/" + fruit + ".json", loot);
        }
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    private void cropBlockstate(CachedOutput cache, List<CompletableFuture<?>> work, String crop) {
        JsonObject variants = new JsonObject();
        int max = crop.equals("beetroots") ? 3 : 7;
        for (int age = 0; age <= max; age++) for (boolean blighted : List.of(false, true)) for (boolean dead : List.of(false, true)) {
            String model;
            if (!blighted && !dead) {
                int stage = crop.equals("carrots") || crop.equals("potatoes") ? rootStage(age) : age;
                model = "minecraft:block/" + crop + "_stage" + stage;
            } else {
                // R196 has no beetroot sprites: reuse carrot disease/death frames as a documented modern adaptation.
                String sourceCrop = crop.equals("beetroots") ? "carrots" : crop;
                int stage = crop.equals("beetroots") ? age : crop.equals("wheat") ? age : rootStage(age);
                if (dead) stage = Math.min(stage, sourceCrop.equals("wheat") ? 6 : 2);
                model = "mite:block/crop_" + sourceCrop + "_" + (dead ? "dead" : "blighted") + "_" + stage;
            }
            variants.add("age=" + age + ",mite_blighted=" + blighted + ",mite_dead=" + dead, object("model", model));
        }
        JsonObject blockstate = new JsonObject(); blockstate.add("variants", variants);
        save(cache, work, "assets/minecraft/blockstates/" + crop + ".json", blockstate);
    }

    private static int rootStage(int age) { return (age == 6 ? 5 : age) / 2; }

    private void cropModel(CachedOutput cache, List<CompletableFuture<?>> work, String crop, String state, int stage) {
        String name = "crop_" + crop + "_" + state + "_" + stage;
        JsonObject model = object("parent", "minecraft:block/crop");
        model.add("textures", object("crop", "mite:block/" + name));
        save(cache, work, "assets/mite/models/block/" + name + ".json", model);
    }

    private void farmland(CachedOutput cache, List<CompletableFuture<?>> work) {
        JsonObject variants = new JsonObject();
        for (int moisture = 0; moisture <= 7; moisture++) for (boolean fertilized : List.of(false, true)) {
            String model = fertilized ? "mite:block/farmland_" + (moisture == 0 ? "dry" : "wet") + "_fertilized"
                    : "minecraft:block/" + (moisture == 0 ? "farmland" : "farmland_moist");
            variants.add("mite_fertilized=" + fertilized + ",moisture=" + moisture, object("model", model));
        }
        JsonObject blockstate = new JsonObject(); blockstate.add("variants", variants);
        save(cache, work, "assets/minecraft/blockstates/farmland.json", blockstate);
        for (String moisture : List.of("dry", "wet")) {
            String name = "farmland_" + moisture + "_fertilized";
            JsonObject model = object("parent", "minecraft:block/template_farmland");
            JsonObject textures = object("dirt", "minecraft:block/dirt"); textures.addProperty("top", "mite:block/" + name);
            model.add("textures", textures);
            save(cache, work, "assets/mite/models/block/" + name + ".json", model);
        }
        JsonObject snowSupport = new JsonObject(); snowSupport.addProperty("replace", false);
        JsonArray supports = new JsonArray(); supports.add("minecraft:farmland"); snowSupport.add("values", supports);
        save(cache, work, "data/minecraft/tags/block/snow_layer_can_survive_on.json", snowSupport);
    }

    private static JsonObject cropLoot(String crop) {
        JsonObject loot = object("type", "minecraft:block"); JsonArray pools = new JsonArray();
        String block = "minecraft:" + crop;
        if (crop.equals("beetroots")) {
            JsonObject mature = stateCondition(block, "3", false, false);
            JsonObject healthy = stateCondition(block, null, false, false);
            JsonObject alternatives = object("type", "minecraft:alternatives"); JsonArray children = new JsonArray();
            JsonObject beet = item("minecraft:beetroot", 1); beet.add("conditions", array(mature)); children.add(beet);
            children.add(item("minecraft:beetroot_seeds", 1)); alternatives.add("children", children);
            pools.add(pool(alternatives, healthy));
            JsonObject seeds = item("minecraft:beetroot_seeds", 1);
            JsonObject extra = object("function", "minecraft:set_count");
            JsonObject binomial = object("type", "minecraft:binomial"); binomial.addProperty("n", 3); binomial.addProperty("p", 0.5714286F);
            extra.add("count", binomial); extra.addProperty("add", true); seeds.add("functions", array(extra));
            pools.add(pool(seeds, mature));
        } else {
            String seed = crop.equals("wheat") ? "minecraft:wheat_seeds" : crop.equals("carrots") ? "minecraft:carrot" : "minecraft:potato";
            String produce = crop.equals("wheat") ? "minecraft:wheat" : seed;
            pools.add(pool(item(seed, 1), stateCondition(block, "0", false, false)));
            JsonObject mature = item(produce, crop.equals("wheat") ? 1 : 2);
            if (!crop.equals("wheat")) {
                JsonObject count = object("function", "minecraft:set_count");
                JsonObject binomial = object("type", "minecraft:binomial"); binomial.addProperty("n", 1); binomial.addProperty("p", .25F);
                count.add("count", binomial); count.addProperty("add", true);
                mature.getAsJsonArray("functions").add(count);
            }
            pools.add(pool(mature, stateCondition(block, "7", false, false)));
            if (crop.equals("potatoes")) {
                JsonObject unhealthy = object("condition", "minecraft:any_of"); JsonArray terms = new JsonArray();
                terms.add(stateCondition(block, null, true, false));
                for (int age = 1; age < 7; age++) terms.add(stateCondition(block, String.valueOf(age), false, false));
                unhealthy.add("terms", terms);
                pools.add(pool(item("minecraft:poisonous_potato", 1), unhealthy));
            }
        }
        loot.add("pools", pools);
        return loot;
    }

    private static JsonObject stateCondition(String block, String age, boolean blighted, boolean dead) {
        JsonObject condition = object("condition", "minecraft:block_state_property"); condition.addProperty("block", block);
        JsonObject properties = object("mite_blighted", String.valueOf(blighted)); properties.addProperty("mite_dead", String.valueOf(dead));
        if (age != null) properties.addProperty("age", age);
        condition.add("properties", properties); return condition;
    }

    private static JsonObject item(String id, int count) {
        JsonObject entry = object("type", "minecraft:item"); entry.addProperty("name", id);
        if (count != 1) {
            JsonObject function = object("function", "minecraft:set_count"); function.addProperty("count", count);
            entry.add("functions", array(function));
        }
        return entry;
    }

    private static JsonObject pool(JsonObject entry, JsonObject condition) {
        JsonObject pool = new JsonObject(); pool.addProperty("rolls", 1); pool.add("entries", array(entry));
        pool.add("conditions", array(condition)); return pool;
    }

    private static JsonObject melonLoot() {
        JsonObject loot = object("type", "minecraft:block");
        JsonObject silk = item("minecraft:melon", 1);
        JsonObject match = object("condition", "minecraft:match_tool");
        JsonObject predicate = new JsonObject(); JsonObject predicates = new JsonObject();
        JsonObject enchantment = object("enchantments", "minecraft:silk_touch");
        JsonObject levels = new JsonObject(); levels.addProperty("min", 1); enchantment.add("levels", levels);
        predicates.add("minecraft:enchantments", array(enchantment)); predicate.add("predicates", predicates);
        match.add("predicate", predicate); silk.add("conditions", array(match));
        JsonObject alternatives = object("type", "minecraft:alternatives"); JsonArray children = array(silk);
        children.add(item("minecraft:melon_slice", 4)); alternatives.add("children", children);
        JsonObject pool = new JsonObject(); pool.addProperty("rolls", 1); pool.add("entries", array(alternatives));
        loot.add("pools", array(pool)); return loot;
    }

    private void agricultureEnchantments(CachedOutput cache, List<CompletableFuture<?>> work) {
        for (String id : List.of("harvesting", "fertility")) {
            JsonObject enchantment = new JsonObject(); enchantment.addProperty("anvil_cost", 2);
            enchantment.add("description", object("translate", "enchantment.mite." + id));
            enchantment.addProperty("supported_items", "#mite:agricultural_tools");
            enchantment.addProperty("max_level", 5); enchantment.addProperty("weight", 25);
            JsonObject min = new JsonObject(); min.addProperty("base", 1); min.addProperty("per_level_above_first", 10);
            JsonObject max = new JsonObject(); max.addProperty("base", Integer.MAX_VALUE); max.addProperty("per_level_above_first", 0);
            enchantment.add("min_cost", min); enchantment.add("max_cost", max);
            JsonArray slots = new JsonArray(); slots.add("mainhand"); enchantment.add("slots", slots);
            save(cache, work, "data/mite/enchantment/" + id + ".json", enchantment);
        }
        Set<String> tools = new LinkedHashSet<>();
        for (String material : List.of("wooden", "stone", "iron", "golden", "diamond", "netherite")) tools.add("minecraft:" + material + "_hoe");
        for (MiteTools.Definition definition : MiteTools.definitions())
            if (Set.of("HOE", "MATTOCK", "SCYTHE").contains(definition.family().name())) tools.add(definition.id());
        JsonArray values = new JsonArray(); tools.forEach(values::add);
        JsonObject toolTag = new JsonObject(); toolTag.addProperty("replace", false); toolTag.add("values", values);
        save(cache, work, "data/mite/tags/item/agricultural_tools.json", toolTag);
        for (String tag : List.of("in_enchanting_table", "tradeable", "on_random_loot", "non_treasure")) {
            JsonObject enchantmentTag = new JsonObject(); enchantmentTag.addProperty("replace", false);
            JsonArray enchantments = new JsonArray(); enchantments.add("mite:harvesting"); enchantments.add("mite:fertility");
            enchantmentTag.add("values", enchantments);
            save(cache, work, "data/minecraft/tags/enchantment/" + tag + ".json", enchantmentTag);
        }
    }

    private void save(CachedOutput cache, List<CompletableFuture<?>> work, String path, JsonObject json) {
        work.add(DataProvider.saveStable(cache, json, output.resolve(path)));
    }
    private static JsonObject object(String key, String value) { JsonObject object = new JsonObject(); object.addProperty(key, value); return object; }
    private static JsonArray array(JsonObject value) { JsonArray array = new JsonArray(); array.add(value); return array; }
    @Override public String getName() { return "MITE crop disease assets, crop loot and agricultural enchantments"; }
}
