package io.github.ningmeng123.mite.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.MonsterArmorRules;
import io.github.ningmeng123.mite.rules.MonsterArmorRules.Row;
import io.github.ningmeng123.mite.rules.MonsterArmorRules.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Common configuration and wire validation for monster armor progression. @author NingMeng123 */
public final class MonsterArmorSettings {
    private static final Set<String> ROW_FIELDS = Set.of("startDay", "endDay", "ordinaryPercent", "bloodPercent");
    private static volatile Settings local = MonsterArmorRules.DEFAULTS;
    private record Entry(int index, Row row) {}

    private MonsterArmorSettings() {}

    public static Settings localSettings() { return local; }

    public static synchronized boolean save(Settings settings) {
        if (!MiteConfig.SPEC.isLoaded() || settings == null) return false;
        var previousSettings = local;
        var previousEntries = MiteConfig.MONSTER_ARMOR_PROGRESSION.get();
        var entries = encode(settings);
        try {
            MiteConfig.MONSTER_ARMOR_PROGRESSION.set(entries);
            MiteConfig.SPEC.save();
        } catch (RuntimeException exception) {
            if (MiteConfig.MONSTER_ARMOR_PROGRESSION.get().equals(entries)) {
                MiteConfig.MONSTER_ARMOR_PROGRESSION.set(previousEntries);
                local = previousSettings;
            }
            throw exception;
        }
        return true;
    }

    static void reload() {
        var entries = MiteConfig.MONSTER_ARMOR_PROGRESSION.get();
        if (entries.isEmpty()) {
            local = MonsterArmorRules.DEFAULTS;
            return;
        }
        try {
            var rows = new Row[MonsterArmorRules.MATERIALS.size()];
            if (entries.size() != rows.length) throw new IllegalArgumentException("Expected one row per armor material");
            for (String text : entries) {
                var entry = decodeEntry(text);
                if (rows[entry.index()] != null) throw new IllegalArgumentException("Repeated armor material");
                rows[entry.index()] = entry.row();
            }
            local = new Settings(List.of(rows));
        } catch (IllegalArgumentException exception) {
            Mite.LOGGER.warn("Invalid monster armor progression, retaining the last valid settings: {}", exception.getMessage());
        }
    }

    static boolean validEntry(Object input) {
        if (!(input instanceof String text)) return false;
        try {
            decodeEntry(text);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static List<String> exportEntries() { return encode(local); }

    private static Entry decodeEntry(String text) {
        int separator = text.indexOf('=');
        if (separator < 1) throw new IllegalArgumentException("Missing armor material");
        int index = MonsterArmorRules.MATERIALS.indexOf(text.substring(0, separator).trim());
        String[] values = text.substring(separator + 1).split(",", -1);
        if (index < 0 || values.length != 4) throw new IllegalArgumentException("Invalid armor progression row");
        String end = values[1].trim();
        return new Entry(index, new Row(Long.parseLong(values[0].trim()),
                end.equals("*") ? MonsterArmorRules.OPEN_END : Long.parseLong(end),
                Double.parseDouble(values[2].trim()), Double.parseDouble(values[3].trim())));
    }

    private static List<String> encode(Settings settings) {
        if (settings.equals(MonsterArmorRules.DEFAULTS)) return List.of();
        var entries = new ArrayList<String>();
        for (int i = 0; i < settings.rows().size(); i++) {
            var row = settings.rows().get(i);
            String end = row.endDay() == MonsterArmorRules.OPEN_END ? "*" : Long.toString(row.endDay());
            entries.add(MonsterArmorRules.MATERIALS.get(i) + "=" + row.startDay() + "," + end
                    + "," + row.ordinaryPercent() + "," + row.bloodPercent());
        }
        return List.copyOf(entries);
    }

    /** Parse exact integer day values before a JSON decoder can coerce or truncate them. */
    public static Settings decodeJson(JsonElement json) {
        if (!json.isJsonObject() || !json.getAsJsonObject().keySet().equals(Set.of("rows"))
                || !json.getAsJsonObject().get("rows").isJsonArray())
            throw new IllegalArgumentException("Expected armor progression rows");
        var source = json.getAsJsonObject().getAsJsonArray("rows");
        if (source.size() != MonsterArmorRules.MATERIALS.size()) throw new IllegalArgumentException("Invalid armor progression row count");
        var rows = new ArrayList<Row>();
        for (var element : source) {
            if (!element.isJsonObject() || !element.getAsJsonObject().keySet().equals(ROW_FIELDS))
                throw new IllegalArgumentException("Invalid armor progression fields");
            var row = element.getAsJsonObject();
            rows.add(new Row(Long.parseLong(number(row.get("startDay")).getAsString()),
                    Long.parseLong(number(row.get("endDay")).getAsString()),
                    number(row.get("ordinaryPercent")).getAsDouble(), number(row.get("bloodPercent")).getAsDouble()));
        }
        return new Settings(rows);
    }

    private static JsonPrimitive number(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected a number in the armor progression row");
        return element.getAsJsonPrimitive();
    }
}
