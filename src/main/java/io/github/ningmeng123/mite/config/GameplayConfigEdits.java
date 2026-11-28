package io.github.ningmeng123.mite.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative, validated writes from the in-game editors. @author NingMeng123 */
public final class GameplayConfigEdits {
    private static final Gson GSON = new Gson();
    private static final Type NUMBERS = new TypeToken<Map<String, Double>>() {}.getType();
    private static final Type VALUES = new TypeToken<Map<String, Object>>() {}.getType();
    public record SpawnRates(Map<String, Double> rates, Map<String, Double> weights) {}
    public record Enchantments(Map<String, Integer> caps, Set<String> disabled, Map<String, Double> chances) {}
    public record OreMultipliers(Map<Dimension, Map<Kind, Double>> multipliers,
            Map<Dimension, Map<Kind, Double>> deepslateMultipliers,
            Map<Dimension, Map<Kind, String>> heights, Map<Dimension, Map<Kind, String>> deepslateHeights) {}
    public record OreGeneration(Map<Dimension, Map<Kind, Double>> candidates, Map<Dimension, Map<Kind, Double>> chancePercent) {}

    private GameplayConfigEdits() {}

    /** Permission is checked before parsing, and again for every individual request. Null means saved. */
    public static String apply(ServerPlayer player, String section, String json) {
        if (player == null || !player.hasPermissions(2)) return "mite.config.remote.no_permission";
        try {
            if (!save(section, json)) return "mite.config.remote.invalid";
            GameplayConfigSync.broadcast();
            return null;
        } catch (IllegalArgumentException | JsonParseException | NullPointerException exception) {
            return "mite.config.remote.invalid";
        } catch (RuntimeException exception) {
            Mite.LOGGER.warn("Cannot save gameplay settings requested by {}", player.getGameProfile().getName(), exception);
            return "mite.config.remote.save_failed";
        }
    }

    /** The caller must already run on the server thread and have authorized the edit. */
    public static boolean save(String section, String json) {
        if (section == null || json == null || json.length() > 262144) return false;
        var parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) return false;
        JsonObject object = parsed.getAsJsonObject();
        return switch (section) {
            case "spawning_rules" -> {
                Map<String, Double> values = GSON.fromJson(object, NUMBERS);
                Set<String> keys = java.util.Arrays.stream(MonsterSpawningSettings.Option.values())
                        .map(MonsterSpawningSettings.Option::key).collect(java.util.stream.Collectors.toSet());
                yield numericTree(object, false) && values.keySet().equals(keys) && MiteConfig.saveMonsterSpawningSettings(values);
            }
            case "addon_spawning" -> MiteExtensions.policy().saveSpawningControls(
                    validateAddonSpawning(GSON.fromJson(object, VALUES)));
            case "growth" -> saveGrowth(object);
            case "spawn_rates" -> {
                if (!object.keySet().equals(Set.of("rates", "weights")) || !numericTree(object, false)) yield false;
                var draft = GSON.fromJson(object, SpawnRates.class);
                yield MiteConfig.saveMonsterSpawnSettings(draft.rates(), draft.weights());
            }
            case "enchantments" -> validEnchantmentsShape(object) && saveEnchantments(GSON.fromJson(object, Enchantments.class));
            case "monster_armor" -> MonsterArmorSettings.save(MonsterArmorSettings.decodeJson(object));
            case "gravel_drops" -> object.keySet().equals(Set.of("ordinary", "nether"))
                    && object.get("ordinary").isJsonObject() && object.get("nether").isJsonObject()
                    && numericTree(object, false) && GravelDropSettings.save(GSON.fromJson(object, GravelDropSettings.Rates.class));
            case "ore_multipliers" -> {
                var draft = GSON.fromJson(object, OreMultipliers.class);
                yield MiteConfig.saveOreMultipliers(draft.multipliers(), draft.deepslateMultipliers(), draft.heights(), draft.deepslateHeights());
            }
            case "ore_generation" -> {
                var draft = GSON.fromJson(object, OreGeneration.class);
                yield MiteConfig.saveOreGeneration(draft.candidates(), draft.chancePercent());
            }
            case "gameplay" -> saveGameplay(object);
            default -> false;
        };
    }

    private static boolean validGrowthShape(JsonObject object) {
        Set<String> numeric = Set.of("healthBase", "damageBase", "skeletonReference", "healthRate", "damageRate",
                "minimumHealthReference");
        Set<String> text = Set.of("healthFormula", "damageFormula", "healthBloodMoonFormula", "damageBloodMoonFormula");
        if (object.size() != numeric.size() + text.size()) return false;
        for (String key : numeric) {
            var value = object.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble())) return false;
        }
        for (String key : text) {
            var value = object.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return false;
        }
        return true;
    }

    private static boolean validEnchantmentsShape(JsonObject object) {
        if (!object.keySet().equals(Set.of("caps", "disabled", "chances"))
                || !object.get("caps").isJsonObject() || !object.get("chances").isJsonObject()
                || !numericTree(object.get("caps"), true) || !numericTree(object.get("chances"), false)
                || !object.get("disabled").isJsonArray()) return false;
        for (var entry : object.getAsJsonObject("caps").entrySet())
            if (!entry.getValue().isJsonPrimitive() || !finiteRange(entry.getValue().getAsDouble(), 1D, 255D)) return false;
        for (var value : object.getAsJsonArray("disabled"))
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return false;
        return true;
    }

    /** Check raw JSON before Gson can coerce a string or truncate a fractional integer. */
    private static boolean numericTree(JsonElement element, boolean whole) {
        if (element.isJsonObject()) return element.getAsJsonObject().entrySet().stream().allMatch(entry -> numericTree(entry.getValue(), whole));
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return false;
        double value = element.getAsDouble();
        return Double.isFinite(value) && (!whole || value == Math.rint(value));
    }

    private static boolean saveGrowth(JsonObject object) {
        // Older editors omit the new field. Retain the server's configured minimum on their edits.
        if (!object.has("minimumHealthReference"))
            object.addProperty("minimumHealthReference", MiteConfig.localSnapshot().monsterSettings().minimumHealthReference());
        if (!validGrowthShape(object)) return false;
        var draft = GSON.fromJson(object, MonsterGrowthRules.Settings.class);
        // Apply storage bounds before validating the resulting growth formulas.
        if (draft == null || !finiteRange(draft.damageRate(), 0D, 1D)
                || draft.damageFormula() == null || draft.damageBloodMoonFormula() == null
                || draft.damageFormula().length() > 512 || draft.damageBloodMoonFormula().length() > 512) return false;
        return MiteConfig.saveMonsterGrowth(draft).valid();
    }

    private static boolean saveEnchantments(Enchantments draft) {
        if (draft.caps() == null || draft.disabled() == null || draft.chances() == null) return false;
        for (var entry : draft.caps().entrySet())
            if (!MiteConfig.validEnchantmentId(entry.getKey()) || entry.getValue() == null
                    || entry.getValue() < 1 || entry.getValue() > 255) return false;
        if (!draft.disabled().stream().allMatch(MiteConfig::validEnchantmentId)) return false;
        for (var entry : draft.chances().entrySet())
            if (!MiteConfig.validEnchantmentId(entry.getKey()) || entry.getValue() == null
                    || !finiteRange(entry.getValue(), 0D, 100D)) return false;
        MiteConfig.saveEnchantments(draft.caps(), draft.disabled(), draft.chances());
        return true;
    }

    /** Rebuild the addon's declared types after JSON decoding, without trusting client metadata. */
    public static Map<String, Object> validateAddonSpawning(Map<String, Object> values) {
        var controls = MiteExtensions.policy().spawningControls();
        if (values == null || controls.isEmpty() || values.size() != controls.size())
            throw new IllegalArgumentException("Missing addon spawning settings");
        var checked = new HashMap<String, Object>();
        for (var control : controls) {
            Object value = values.get(control.key()), factory = control.defaultValue();
            if (factory instanceof Boolean) {
                if (!(value instanceof Boolean)) throw new IllegalArgumentException("Invalid spawning toggle");
                checked.put(control.key(), value);
            } else {
                if (!(value instanceof Number number) || !(factory instanceof Number))
                    throw new IllegalArgumentException("Invalid spawning number");
                double numeric = number.doubleValue();
                boolean whole = factory instanceof Byte || factory instanceof Short || factory instanceof Integer || factory instanceof Long;
                if (!finiteRange(numeric, control.minimum(), control.maximum()) || whole && numeric != Math.rint(numeric))
                    throw new IllegalArgumentException("Spawning number outside declared range");
                Object typed;
                if (factory instanceof Byte) { if (!finiteRange(numeric, Byte.MIN_VALUE, Byte.MAX_VALUE)) throw new IllegalArgumentException(); typed = (byte)numeric; }
                else if (factory instanceof Short) { if (!finiteRange(numeric, Short.MIN_VALUE, Short.MAX_VALUE)) throw new IllegalArgumentException(); typed = (short)numeric; }
                else if (factory instanceof Integer) { if (!finiteRange(numeric, Integer.MIN_VALUE, Integer.MAX_VALUE)) throw new IllegalArgumentException(); typed = (int)numeric; }
                else if (factory instanceof Long) { if (numeric < Long.MIN_VALUE || numeric >= 0x1.0p63) throw new IllegalArgumentException(); typed = (long)numeric; }
                else if (factory instanceof Float) { if (!Float.isFinite((float)numeric)) throw new IllegalArgumentException(); typed = (float)numeric; }
                else typed = numeric;
                checked.put(control.key(), typed);
            }
        }
        if (!checked.keySet().equals(values.keySet())) throw new IllegalArgumentException("Unknown spawning settings");
        return Map.copyOf(checked);
    }

    private static Map<String, ModConfigSpec.ConfigValue<?>> gameplaySettings() {
        var values = new LinkedHashMap<String, ModConfigSpec.ConfigValue<?>>();
        values.put("monsters.growth_multiplier", MiteConfig.MONSTER_GROWTH);
        values.put("monsters.health_growth_multiplier", MiteConfig.MONSTER_HEALTH_GROWTH);
        values.put("monsters.damage_growth_multiplier", MiteConfig.MONSTER_DAMAGE_GROWTH);
        values.put("combat.weapon_reach_multiplier", MiteConfig.WEAPON_REACH_MULTIPLIER);
        values.put("combat.weapon_reach_reduction", MiteConfig.WEAPON_REACH_REDUCTION);
        values.put("combat.height_reach_penalty", MiteConfig.HEIGHT_REACH_PENALTY);
        values.put("combat.better_combat_damage_multiplier", MiteConfig.BETTER_COMBAT_DAMAGE_MULTIPLIER);
        values.put("combat.better_combat_attack_speed_multiplier", MiteConfig.BETTER_COMBAT_ATTACK_SPEED_MULTIPLIER);
        values.put("combat.better_combat_mobs", MiteConfig.BETTER_COMBAT_MOBS);
        values.put("structures.underworld_trials", MiteConfig.UNDERWORLD_TRIALS);
        values.put("structures.overworld_trials", MiteConfig.OVERWORLD_TRIALS);
        values.put(MiteConfig.BLOCK_NIGHT_VISION_SHADERS_PATH, MiteConfig.BLOCK_NIGHT_VISION_SHADERS);
        values.put(MiteConfig.BLOCK_NIGHT_VISION_RESOURCE_PACKS_PATH, MiteConfig.BLOCK_NIGHT_VISION_RESOURCE_PACKS);
        values.put(MiteConfig.BLOCK_XRAY_RESOURCE_PACKS_PATH, MiteConfig.BLOCK_XRAY_RESOURCE_PACKS);
        Object asynchronous = MiteConfig.SPEC.getValues().get("performance.async_pathfinding");
        if (asynchronous instanceof ModConfigSpec.BooleanValue setting)
            values.put("performance.async_pathfinding", setting);
        return values;
    }

    public static Map<String, Object> gameplayValues() {
        var values = new LinkedHashMap<String, Object>();
        gameplaySettings().forEach((key, setting) -> values.put(key, MiteConfig.SPEC.isLoaded() ? setting.get() : setting.getDefault()));
        return Map.copyOf(values);
    }

    private static boolean saveGameplay(JsonObject object) {
        var settings = gameplaySettings();
        if (object.isEmpty() || !settings.keySet().containsAll(object.keySet())) return false;
        var checked = new HashMap<String, Object>();
        for (var entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) return false;
            var primitive = entry.getValue().getAsJsonPrimitive();
            var setting = settings.get(entry.getKey());
            if (setting instanceof ModConfigSpec.BooleanValue) {
                if (!primitive.isBoolean()) return false;
                checked.put(entry.getKey(), primitive.getAsBoolean());
            } else {
                if (!primitive.isNumber()) return false;
                double number = primitive.getAsDouble();
                if (!Double.isFinite(number) || !setting.getSpec().test(number)) return false;
                checked.put(entry.getKey(), number);
            }
        }
        var current = MiteConfig.localSnapshot();
        double growth = ((Number)checked.getOrDefault("monsters.growth_multiplier", current.growth())).doubleValue();
        double health = ((Number)checked.getOrDefault("monsters.health_growth_multiplier", current.healthGrowth())).doubleValue();
        double damage = ((Number)checked.getOrDefault("monsters.damage_growth_multiplier", current.damageGrowth())).doubleValue();
        if (!MonsterGrowthRules.validate(current.monsterSettings(), growth * health, growth * damage).valid()) return false;
        // Nothing is mutated until every field and the resulting formula execution have passed.
        checked.forEach((key, value) -> {
            var setting = settings.get(key);
            if (setting instanceof ModConfigSpec.BooleanValue bool) bool.set((Boolean)value);
            else ((ModConfigSpec.DoubleValue)setting).set((Double)value);
        });
        MiteConfig.refreshEnchantmentRules();
        MiteConfig.SPEC.save();
        return true;
    }

    private static boolean finiteRange(double value, double minimum, double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }
}
