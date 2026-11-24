package io.github.ningmeng123.mite.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;

/** Identifies X-ray resource packs that remove the ordinary terrain around ores. @author NingMeng123 */
public final class HiddenTerrainDetector {
    private static final List<String> SOLID_TERRAIN = List.of("stone", "deepslate", "dirt", "cobblestone",
            "tuff", "granite", "andesite", "diorite", "gravel", "sand", "red_sand", "netherrack", "blackstone", "end_stone");
    private static final Set<String> MIRRORED_MODELS = Set.of("stone", "deepslate");
    private static final Set<String> COLUMN_TEXTURES = Set.of("deepslate", "blackstone");
    private static final ResourceLocation EMPTY_VANILLA_MODEL = ResourceLocation.withDefaultNamespace("block/block");
    private static final int MINIMUM_HIDDEN_BLOCKS = 3;
    private static final int MAX_JSON_BYTES = 128 * 1024;
    private static final int MAX_PNG_BYTES = 4 * 1024 * 1024;
    private static final int MAX_PNG_PIXELS = 1024 * 1024;

    private HiddenTerrainDetector() {}

    public static boolean matches(PackResources pack) {
        int hiddenBlocks = 0;
        for (String block : SOLID_TERRAIN) {
            if (hiddenModels(pack, block) || hiddenTextures(pack, block)) {
                hiddenBlocks++;
                if (hiddenBlocks >= MINIMUM_HIDDEN_BLOCKS) return true;
            }
        }
        return false;
    }

    private static boolean hiddenModels(PackResources pack, String block) {
        JsonObject state = json(pack, ResourceLocation.withDefaultNamespace("blockstates/" + block + ".json"));
        if (state == null) {
            return hiddenModel(pack, ResourceLocation.withDefaultNamespace("block/" + block), 0)
                    && (!MIRRORED_MODELS.contains(block)
                    || hiddenModel(pack, ResourceLocation.withDefaultNamespace("block/" + block + "_mirrored"), 0));
        }

        if (!state.has("variants") || !state.get("variants").isJsonObject() || state.has("multipart")) return false;
        JsonObject variants = state.getAsJsonObject("variants");
        if (variants.isEmpty() || variants.size() > 8) return false;
        for (JsonElement variant : variants.asMap().values()) {
            if (variant.isJsonArray()) {
                if (variant.getAsJsonArray().isEmpty() || variant.getAsJsonArray().size() > 32) return false;
                for (JsonElement model : variant.getAsJsonArray()) {
                    if (!hiddenVariant(pack, model)) return false;
                }
            } else if (!hiddenVariant(pack, variant)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hiddenVariant(PackResources pack, JsonElement variant) {
        if (!variant.isJsonObject()) return false;
        ResourceLocation model = location(variant.getAsJsonObject().get("model"));
        return model != null && hiddenModel(pack, model, 0);
    }

    private static boolean hiddenModel(PackResources pack, ResourceLocation id, int depth) {
        if (depth == 8) return false;
        JsonObject model = json(pack, ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "models/" + id.getPath() + ".json"));
        if (model == null) return EMPTY_VANILLA_MODEL.equals(id);
        if (model.has("loader")) return false;
        if (model.has("elements")) {
            JsonElement elements = model.get("elements");
            if (!elements.isJsonArray()) return false;
            if (!elements.getAsJsonArray().isEmpty()) return hasTinyFaces(elements);
        }
        // Minecraft inherits the parent's geometry when the child has no elements.
        if (!model.has("parent")) return true;
        ResourceLocation parent = location(model.get("parent"));
        return parent != null && hiddenModel(pack, parent, depth + 1);
    }

    private static boolean hasTinyFaces(JsonElement elements) {
        double area = 0;
        for (JsonElement element : elements.getAsJsonArray()) {
            if (!element.isJsonObject()) return false;
            JsonObject box = element.getAsJsonObject();
            double[] from = point(box.get("from"));
            double[] to = point(box.get("to"));
            JsonElement faces = box.get("faces");
            if (from == null || to == null || faces == null || !faces.isJsonObject() || faces.getAsJsonObject().isEmpty()) {
                return false;
            }
            double x = Math.abs(to[0] - from[0]);
            double y = Math.abs(to[1] - from[1]);
            double z = Math.abs(to[2] - from[2]);
            for (String face : faces.getAsJsonObject().keySet()) {
                area += switch (face) {
                    case "east", "west" -> y * z;
                    case "up", "down" -> x * z;
                    case "north", "south" -> x * y;
                    default -> Double.POSITIVE_INFINITY;
                };
                if (area > 32) return false;
            }
        }
        return true;
    }

    private static double[] point(JsonElement value) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 3) return null;
        var coordinates = value.getAsJsonArray();
        double[] point = new double[3];
        for (int i = 0; i < point.length; i++) {
            JsonElement coordinate = coordinates.get(i);
            if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber()) return null;
            double number = coordinate.getAsDouble();
            if (!Double.isFinite(number) || number < -16 || number > 32) return null;
            point[i] = number;
        }
        return point;
    }

    private static boolean hiddenTextures(PackResources pack, String block) {
        return mostlyTransparent(pack, block) && (!COLUMN_TEXTURES.contains(block) || mostlyTransparent(pack, block + "_top"));
    }

    private static boolean mostlyTransparent(PackResources pack, String texture) {
        ResourceLocation id = ResourceLocation.withDefaultNamespace("textures/block/" + texture + ".png");
        byte[] png = read(pack, id, MAX_PNG_BYTES);
        if (png == null) return false;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            var reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("png")) return false;
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width <= 0 || height <= 0 || pixels > MAX_PNG_PIXELS) return false;
                var image = reader.read(0);
                if (!image.getColorModel().hasAlpha()) return false;
                int maximumVisible = (int) pixels / 10;
                int visible = 0;
                var row = new int[width];
                for (int y = 0; y < height; y++) {
                    image.getRGB(0, y, width, 1, row, 0, width);
                    for (int rgba : row) {
                        if ((rgba >>> 24) >= 26) {
                            visible++;
                            if (visible > maximumVisible) return false;
                        }
                    }
                }
                return true;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static ResourceLocation location(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? ResourceLocation.tryParse(value.getAsString()) : null;
    }

    private static JsonObject json(PackResources pack, ResourceLocation id) {
        byte[] bytes = read(pack, id, MAX_JSON_BYTES);
        if (bytes == null) return null;
        try {
            JsonElement value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return value.isJsonObject() ? value.getAsJsonObject() : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static byte[] read(PackResources pack, ResourceLocation id, int limit) {
        try {
            var resource = pack.getResource(PackType.CLIENT_RESOURCES, id);
            if (resource == null) return null;
            try (var input = resource.get()) {
                byte[] bytes = input.readNBytes(limit + 1);
                return bytes.length <= limit ? bytes : null;
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }
}
