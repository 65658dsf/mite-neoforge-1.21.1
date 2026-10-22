package io.github.ningmeng123.mite.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class UpperCaveNoiseTest {
    @Test
    void onlyFinalDensityChangesAndThePinnedInputRemainsUntouched() {
        JsonObject original = JsonParser.parseString("""
                {"sea_level":63,"surface_rule":{"type":"minecraft:sequence"},
                  "noise_router":{
                    "temperature":{"type":"minecraft:noise","noise":"minecraft:cave_cheese","xz_scale":1.0,"y_scale":1.0},
                    "final_density":{"type":"minecraft:min",
                      "argument1":{"type":"minecraft:noise","noise":"minecraft:cave_cheese","xz_scale":1.0,"y_scale":0.6666666666666666},
                      "argument2":{"type":"minecraft:noise","noise":"minecraft:cave_entrance","xz_scale":1.0,"y_scale":0.5}}}}
                """).getAsJsonObject();
        JsonObject before = original.deepCopy();
        JsonObject adjusted = UpperCaveNoise.apply(original);
        assertEquals(before, original, "Generating an override cannot mutate the independent vanilla source");
        JsonObject originalRouter = original.getAsJsonObject("noise_router");
        JsonObject adjustedRouter = adjusted.getAsJsonObject("noise_router");
        assertEquals(originalRouter.get("temperature"), adjustedRouter.get("temperature"), "Climate routing stays untouched");
        JsonObject originalFinal = originalRouter.getAsJsonObject("final_density");
        JsonObject adjustedFinal = adjustedRouter.getAsJsonObject("final_density");
        assertEquals(originalFinal.get("argument2"), adjustedFinal.get("argument2"), "Existing entrances are preserved");
        JsonObject wrapper = adjustedFinal.getAsJsonObject("argument1");
        assertEquals("minecraft:add", wrapper.get("type").getAsString());
        assertEquals(originalFinal.get("argument1"), wrapper.get("argument1"), "The seeded native noise parameters are preserved");
        adjustedFinal.add("argument1", wrapper.get("argument1"));
        assertEquals(original, adjusted, "Removing only the explicit cheese wrapper restores the complete source preset");
    }

    @Test
    void entranceReferencesOnlyChangeWithinFinalDensity() {
        JsonObject original = JsonParser.parseString("""
                {"noise_router":{"continents":"minecraft:overworld/caves/entrances",
                  "final_density":{"type":"minecraft:min","argument1":"minecraft:overworld/caves/entrances",
                    "argument2":{"type":"minecraft:max","argument1":"minecraft:overworld/caves/entrances",
                      "argument2":"minecraft:overworld/caves/spaghetti_2d"}}}}
                """).getAsJsonObject();
        JsonObject before = original.deepCopy();
        JsonObject adjusted = UpperCaveNoise.apply(original).getAsJsonObject("noise_router");
        assertEquals(before, original);
        assertEquals("minecraft:overworld/caves/entrances", adjusted.get("continents").getAsString());
        JsonObject density = adjusted.getAsJsonObject("final_density");
        assertEquals("mite:upper_cave_entrances", density.get("argument1").getAsString());
        assertEquals("mite:upper_cave_entrances", density.getAsJsonObject("argument2").get("argument1").getAsString());
        assertEquals("minecraft:overworld/caves/spaghetti_2d", density.getAsJsonObject("argument2").get("argument2").getAsString());
    }

    @Test
    void spaghettiBiasPreservesWideEntrancesAndAllNativeBranchInternals() {
        JsonObject original = JsonParser.parseString("""
                {"type":"minecraft:cache_once","argument":{"type":"minecraft:min",
                  "argument1":{"type":"minecraft:noise","noise":"minecraft:cave_entrance","xz_scale":0.75,"y_scale":0.5},
                  "argument2":{"type":"minecraft:add","argument1":"minecraft:overworld/caves/spaghetti_roughness_function",
                    "argument2":{"type":"minecraft:noise","noise":"minecraft:spaghetti_3d_1","xz_scale":1.0,"y_scale":1.0}}}}
                """).getAsJsonObject();
        JsonObject before = original.deepCopy();
        JsonObject adjusted = UpperCaveNoise.applyEntrances(original);
        assertEquals(before, original);
        JsonObject density = adjusted.getAsJsonObject("argument");
        assertEquals(original.getAsJsonObject("argument").get("argument1"), density.get("argument1"));
        JsonObject wrapper = density.getAsJsonObject("argument2");
        assertEquals("minecraft:add", wrapper.get("type").getAsString());
        assertEquals(original.getAsJsonObject("argument").get("argument2"), wrapper.get("argument1"));
        assertEquals(0.035, evaluate(wrapper.getAsJsonObject("argument2"), 40), 1.0E-12);
        density.add("argument2", wrapper.get("argument1"));
        assertEquals(original, adjusted);
    }

    @ParameterizedTest
    @CsvSource({"-64,0", "-24,0", "-8,0.25", "8,0.5", "48,0.5", "80,0.5", "96,0.25", "112,0", "240,0"})
    void cheeseBiasTapersOutsideTheUpperCaveBand(int height, double expected) {
        JsonObject original = JsonParser.parseString("""
                {"noise_router":{"final_density":{"type":"minecraft:noise",
                  "noise":"minecraft:cave_cheese","xz_scale":1.0,"y_scale":0.6666666666666666}}}
                """).getAsJsonObject();
        JsonElement bias = UpperCaveNoise.apply(original).getAsJsonObject("noise_router")
                .getAsJsonObject("final_density").get("argument2");
        assertEquals(expected, evaluate(bias.getAsJsonObject(), height), 1.0E-12,
                "Deep caves and the high surface receive no adjustment; the middle band reduces cheese voids");
    }

    @ParameterizedTest
    @CsvSource({"-64,0", "-24,0", "-8,0.0175", "8,0.035", "48,0.035", "80,0.035", "96,0.0175", "112,0", "240,0"})
    void spaghettiBiasTapersOutsideTheUpperCaveBand(int height, double expected) {
        JsonObject original = JsonParser.parseString("""
                {"type":"minecraft:cache_once","argument":{"type":"minecraft:min",
                  "argument1":{"type":"minecraft:noise","noise":"minecraft:cave_entrance","xz_scale":0.75,"y_scale":0.5},
                  "argument2":"minecraft:overworld/caves/spaghetti_roughness_function"}}
                """).getAsJsonObject();
        JsonObject bias = UpperCaveNoise.applyEntrances(original).getAsJsonObject("argument")
                .getAsJsonObject("argument2").getAsJsonObject("argument2");
        assertEquals(expected, evaluate(bias, height), 1.0E-12);
    }

    private static double evaluate(JsonObject function, int height) {
        return switch (function.get("type").getAsString()) {
            case "minecraft:min" -> Math.min(evaluate(function.getAsJsonObject("argument1"), height),
                    evaluate(function.getAsJsonObject("argument2"), height));
            case "minecraft:y_clamped_gradient" -> {
                double progress = Math.clamp((double)(height - function.get("from_y").getAsInt())
                        / (function.get("to_y").getAsInt() - function.get("from_y").getAsInt()), 0.0, 1.0);
                double lower = function.get("from_value").getAsDouble();
                yield lower + progress * (function.get("to_value").getAsDouble() - lower);
            }
            default -> throw new IllegalArgumentException("Unexpected cave adjustment function");
        };
    }
}
