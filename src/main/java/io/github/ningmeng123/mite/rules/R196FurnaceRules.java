package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable source oracle shared by server decisions and client descriptions. @author NingMeng123 */
public final class R196FurnaceRules {
    public record Fuel(int ticks, int heat) {}
    public record Profile(int maxHeat, boolean acceptsLarge, int cycleTicks) {}
    private static final JsonObject DATA = load();
    private R196FurnaceRules() {}
    private static JsonObject load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                R196FurnaceRules.class.getResourceAsStream("/mite/furnaces-024.json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception failure) { throw new IllegalStateException("Cannot load R196 furnace evidence", failure); }
    }
    public static Fuel fuel(String id) {
        var value = fuelEntry(id);
        return value == null ? null : new Fuel(value.get("ticks").getAsInt(), value.get("heat").getAsInt());
    }
    public static Profile profile(String id) {
        var value = DATA.getAsJsonObject("profiles").getAsJsonObject(id);
        return value == null ? null : new Profile(value.get("max_heat").getAsInt(),
                value.get("accepts_large").getAsBoolean(), value.get("cycle_ticks").getAsInt());
    }
    public static Map<String, Profile> profiles() {
        Map<String, Profile> result = new LinkedHashMap<>();
        DATA.getAsJsonObject("profiles").keySet().forEach(id -> result.put(id, profile(id)));
        return java.util.Collections.unmodifiableMap(result);
    }
    public static int requiredHeat(String id) {
        // Modern raw metals retain the heat tier of the corresponding source ore.
        id = switch (id) {
            case "minecraft:raw_copper" -> "minecraft:copper_ore";
            case "minecraft:raw_iron" -> "minecraft:iron_ore";
            case "minecraft:raw_gold" -> "minecraft:gold_ore";
            case "mite:raw_silver" -> "mite:silver_ore";
            case "mite:raw_mithril" -> "mite:mithril_ore";
            case "mite:raw_adamantium" -> "mite:adamantium_ore";
            default -> id;
        };
        var value = DATA.getAsJsonObject("required_heat").get(id);
        return value == null ? 1 : value.getAsInt();
    }
    public static Boolean large(String id) {
        var value = fuelEntry(id);
        return value == null || !value.has("large") ? null : value.get("large").getAsBoolean();
    }
    private static JsonObject fuelEntry(String id) {
        var value = DATA.getAsJsonObject("fuel").getAsJsonObject(id);
        return value != null || !DATA.has("modern_adapted_fuel") ? value : DATA.getAsJsonObject("modern_adapted_fuel").getAsJsonObject(id);
    }
    public static boolean fuelAccepted(int heat, int maximum, boolean large, boolean acceptsLarge) {
        return heat > 0 && heat <= maximum && (!large || acceptsLarge);
    }
}
