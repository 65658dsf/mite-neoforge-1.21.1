package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Both placed and inventory ores must resolve to real, distinct deepslate artwork. @author NingMeng123 */
class DeepslateOreAssetsTest {
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path MAIN = Path.of("src/main/resources");

    @ParameterizedTest
    @ValueSource(strings = {"silver", "mithril", "adamantium"})
    void deepslateBlockAndItemModelsResolveToOpaqueDeepslateSprites(String metal) throws IOException {
        String ore = "deepslate_" + metal + "_ore";
        JsonObject state = json(GENERATED.resolve("assets/mite/blockstates/" + ore + ".json"));
        String modelId = state.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString();
        JsonObject model = json(resource(GENERATED, modelId, "models", ".json"));
        assertEquals("minecraft:block/cube_all", model.get("parent").getAsString());

        JsonObject item = json(GENERATED.resolve("assets/mite/models/item/" + ore + ".json"));
        assertEquals(modelId, item.get("parent").getAsString(), "Inventory and placed forms must use the same ore artwork");
        String textureId = model.getAsJsonObject("textures").get("all").getAsString();
        Path texture = resource(MAIN, textureId, "textures", ".png");
        assertTrue(Files.isRegularFile(texture), "The model must reference a shipped PNG: " + texture);
        var deep = ImageIO.read(texture.toFile());
        assertNotNull(deep, "The texture must decode as an image");
        assertEquals(16, deep.getWidth());
        assertEquals(16, deep.getHeight());
        var stone = ImageIO.read(MAIN.resolve("assets/mite/textures/block/" + metal + "_ore.png").toFile());
        boolean differs = false;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int pixel = deep.getRGB(x, y);
            assertEquals(255, pixel >>> 24, "Full cube ore textures must be opaque");
            differs |= pixel != stone.getRGB(x, y);
        }
        assertTrue(differs, "Deep ores must have their own host-rock texture rather than the ordinary ore image");
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @ParameterizedTest
    @ValueSource(strings = {"silver", "mithril", "adamantium"})
    void newRawMetalsHaveItemArtAndBothCookingRecipes(String metal) throws IOException {
        String raw = "raw_" + metal;
        JsonObject model = json(GENERATED.resolve("assets/mite/models/item/" + raw + ".json"));
        String textureId = model.getAsJsonObject("textures").get("layer0").getAsString();
        var sprite = ImageIO.read(resource(MAIN, textureId, "textures", ".png").toFile());
        assertNotNull(sprite);
        assertEquals(16, sprite.getWidth());
        assertEquals(16, sprite.getHeight());
        boolean hasVisiblePixel = false, hasTransparentPixel = false;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int alpha = sprite.getRGB(x, y) >>> 24;
            hasVisiblePixel |= alpha >= 128;
            hasTransparentPixel |= alpha == 0;
        }
        assertTrue(hasVisiblePixel && hasTransparentPixel, "Raw ore artwork must be a visible item with a transparent surround");
        for (String device : new String[]{"smelting", "blasting"}) {
            JsonObject recipe = json(GENERATED.resolve("data/mite/recipe/" + raw + "_from_" + device + ".json"));
            assertEquals("minecraft:" + device, recipe.get("type").getAsString());
            assertEquals("mite:" + raw, recipe.getAsJsonObject("ingredient").get("item").getAsString());
            assertEquals("mite:" + metal + "_ingot", recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
        }
    }

    private static Path resource(Path root, String id, String type, String suffix) {
        String[] parts = id.split(":", 2);
        assertEquals(2, parts.length, "A resource ID must include its namespace");
        return root.resolve("assets/" + parts[0] + "/" + type + "/" + parts[1] + suffix);
    }
}
