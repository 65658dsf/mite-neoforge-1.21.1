package io.github.ningmeng123.mite.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;

/** Detects ore models that render full faces adjacent to opaque blocks. @author NingMeng123 */
public final class XrayResourcePackDetector {
    private static final List<String> ORES = List.of(
            "coal_ore", "copper_ore", "iron_ore", "gold_ore", "redstone_ore", "lapis_ore", "diamond_ore", "emerald_ore",
            "deepslate_coal_ore", "deepslate_copper_ore", "deepslate_iron_ore", "deepslate_gold_ore",
            "deepslate_redstone_ore", "deepslate_lapis_ore", "deepslate_diamond_ore", "deepslate_emerald_ore",
            "nether_gold_ore", "nether_quartz_ore", "ancient_debris");
    private static final Direction[] FACES = Direction.values();
    private static final int MAX_PARENT_DEPTH = 16;
    private static final int MAX_MODEL_BYTES = 64 * 1024;
    private static final double EDGE_TOLERANCE = 0.125;

    private XrayResourcePackDetector() {}

    public static boolean matches(PackResources pack) {
        Map<ResourceLocation, JsonObject> models = new HashMap<>();
        for (String ore : ORES) {
            JsonObject state = readJson(pack, ResourceLocation.withDefaultNamespace("blockstates/" + ore + ".json"));
            if (state == null) {
                if (exposesAllFaces(pack, ResourceLocation.withDefaultNamespace("block/" + ore), models)) return true;
            } else if (state.get("variants") instanceof JsonObject variants) {
                for (var choice : variants.asMap().values()) {
                    if (choice instanceof JsonArray alternatives) {
                        for (var variant : alternatives) if (exposesVariant(pack, variant, models)) return true;
                    } else if (exposesVariant(pack, choice, models)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean exposesVariant(PackResources pack, JsonElement variant, Map<ResourceLocation, JsonObject> models) {
        return variant instanceof JsonObject object && exposesAllFaces(pack, modelId(object.get("model")), models);
    }

    private static boolean exposesAllFaces(PackResources pack, ResourceLocation id, Map<ResourceLocation, JsonObject> models) {
        for (int depth = 0; id != null && depth < MAX_PARENT_DEPTH; depth++) {
            if (!models.containsKey(id)) models.put(id, readJson(pack, id.withPrefix("models/").withSuffix(".json")));
            JsonObject model = models.get(id);
            if (model == null || model.has("loader")) return false;

            JsonElement elements = model.get("elements");
            if (elements instanceof JsonArray array && !array.isEmpty()) return isExposedShell(array);
            id = modelId(model.get("parent"));
        }
        return false;
    }

    private static ResourceLocation modelId(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? ResourceLocation.tryParse(value.getAsString()) : null;
    }

    private static JsonObject readJson(PackResources pack, ResourceLocation id) {
        var source = pack.getResource(PackType.CLIENT_RESOURCES, id);
        if (source == null) return null;
        try (var stream = source.get()) {
            byte[] bytes = stream.readNBytes(MAX_MODEL_BYTES + 1);
            if (bytes.length > MAX_MODEL_BYTES) return null;
            JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return json instanceof JsonObject object ? object : null;
        } catch (IOException | JsonParseException ignored) {
            return null;
        }
    }

    private static boolean isExposedShell(JsonArray elements) {
        EnumSet<Direction> exposed = EnumSet.noneOf(Direction.class);
        for (JsonElement entry : elements) {
            if (!(entry instanceof JsonObject element) || element.has("rotation")
                    || !(element.get("faces") instanceof JsonObject faces)) continue;
            double[] from = vector(element.get("from"));
            double[] to = vector(element.get("to"));
            if (from == null || to == null) continue;

            for (Direction direction : FACES) {
                if (faces.get(direction.getName()) instanceof JsonObject face && !face.has("cullface")
                        && coversBlockFace(from, to, direction)) exposed.add(direction);
            }
            if (exposed.size() == FACES.length) return true;
        }
        return false;
    }

    private static boolean coversBlockFace(double[] from, double[] to, Direction direction) {
        int normal = switch (direction.getAxis()) {
            case X -> 0;
            case Y -> 1;
            case Z -> 2;
        };
        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        if (from[normal] > to[normal] || (positive ? !near(to[normal], 16) : !near(from[normal], 0))) return false;
        // Glow packs use a thin quad at each boundary. Other packs put all six faces on a single cube.
        for (int axis = 0; axis < 3; axis++) {
            if (axis != normal && (!near(from[axis], 0) || !near(to[axis], 16))) return false;
        }
        return true;
    }

    private static boolean near(double value, double edge) {
        return Math.abs(value - edge) <= EDGE_TOLERANCE;
    }

    private static double[] vector(JsonElement value) {
        if (!(value instanceof JsonArray array) || array.size() != 3) return null;
        double[] vector = new double[3];
        for (int axis = 0; axis < vector.length; axis++) {
            JsonElement coordinate = array.get(axis);
            if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber()) return null;
            vector[axis] = coordinate.getAsDouble();
            if (!Double.isFinite(vector[axis])) return null;
        }
        return vector;
    }
}
