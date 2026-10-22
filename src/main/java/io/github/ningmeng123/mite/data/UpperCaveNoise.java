package io.github.ningmeng123.mite.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class UpperCaveNoise {
    private UpperCaveNoise() {}

    public static JsonObject apply(JsonObject settings) {
        JsonObject adjusted = settings.deepCopy();
        JsonObject router = adjusted.getAsJsonObject("noise_router");
        router.add("final_density", transform(router.get("final_density")));
        return adjusted;
    }

    public static JsonObject applyEntrances(JsonObject entrances) {
        JsonObject adjusted = entrances.deepCopy();
        JsonObject branches = adjusted.getAsJsonObject("argument");
        branches.add("argument2", withBias(branches.get("argument2"), 0.035));
        return adjusted;
    }

    private static JsonElement transform(JsonElement density) {
        if (density.isJsonObject()) {
            JsonObject object = density.getAsJsonObject();
            if (object.has("type") && object.get("type").getAsString().equals("minecraft:noise")
                    && object.has("noise") && object.get("noise").getAsString().equals("minecraft:cave_cheese")) {
                return withBias(object, 0.5);
            }
            JsonObject adjusted = new JsonObject();
            object.entrySet().forEach(entry -> adjusted.add(entry.getKey(), transform(entry.getValue())));
            return adjusted;
        }
        if (density.isJsonArray()) {
            JsonArray adjusted = new JsonArray();
            density.getAsJsonArray().forEach(element -> adjusted.add(transform(element)));
            return adjusted;
        }
        if (density.isJsonPrimitive() && density.getAsJsonPrimitive().isString()
                && density.getAsString().equals("minecraft:overworld/caves/entrances"))
            return new JsonPrimitive("mite:upper_cave_entrances");
        return density.deepCopy();
    }

    private static JsonObject withBias(JsonElement density, double maximumBias) {
        JsonObject bias = new JsonObject();
        bias.addProperty("type", "minecraft:min");
        bias.add("argument1", gradient(-24, 8, 0.0, maximumBias));
        bias.add("argument2", gradient(80, 112, maximumBias, 0.0));
        JsonObject adjusted = new JsonObject();
        adjusted.addProperty("type", "minecraft:add");
        adjusted.add("argument1", density.deepCopy());
        adjusted.add("argument2", bias);
        return adjusted;
    }

    private static JsonObject gradient(int minimumY, int maximumY, double fromValue, double toValue) {
        JsonObject gradient = new JsonObject();
        gradient.addProperty("type", "minecraft:y_clamped_gradient");
        gradient.addProperty("from_y", minimumY);
        gradient.addProperty("to_y", maximumY);
        gradient.addProperty("from_value", fromValue);
        gradient.addProperty("to_value", toValue);
        return gradient;
    }
}
