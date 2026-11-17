package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** R196 destination textures and both modern portal axes. @author NingMeng123 */
class PortalResourcesTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String[] DESTINATIONS = {"overworld", "underworld", "nether"};

    @ParameterizedTest
    @CsvSource({
            "x,0,false", "x,0,true", "x,1,false", "x,1,true", "x,2,false", "x,2,true",
            "z,0,false", "z,0,true", "z,1,false", "z,1,true", "z,2,false", "z,2,true"
    })
    void everyPortalStateUsesItsDestinationTextureAndCorrectAxis(String axis, int destination, boolean runegate)
            throws Exception {
        JsonObject variants = json(RESOURCES.resolve("assets/minecraft/blockstates/nether_portal.json"))
                .getAsJsonObject("variants");
        Map<String, String> properties = Map.of("axis", axis, "mite_destination", Integer.toString(destination),
                "mite_runegate", Boolean.toString(runegate));
        var matching = variants.entrySet().stream()
                .filter(entry -> matches(entry.getKey(), properties)).toList();
        assertEquals(1, matching.size(), "Exactly one model must match " + properties);

        String modelId = matching.getFirst().getValue().getAsJsonObject().get("model").getAsString();
        String[] identifier = modelId.split(":", 2);
        assertEquals("mite", identifier[0]);
        JsonObject model = json(RESOURCES.resolve("assets/" + identifier[0] + "/models/" + identifier[1] + ".json"));
        assertEquals("minecraft:block/nether_portal_" + (axis.equals("x") ? "ns" : "ew"),
                model.get("parent").getAsString());
        String texture = "mite:block/portal_" + DESTINATIONS[destination];
        assertEquals(texture, model.getAsJsonObject("textures").get("portal").getAsString());
        assertEquals(texture, model.getAsJsonObject("textures").get("particle").getAsString());
    }

    @ParameterizedTest
    @CsvSource({
            "overworld,190d396ebc104086181c02ad8d29ec2869586897612bd3612cf552f52cb1e86a",
            "underworld,1c937c9d010aa3c9b3406bb8e253f13594ec42d097a1bed0c0458aedcbb434ea",
            "nether,9b7cd6cc5ec26487116f8d412bddcf4abd8556e31ce5cec7a2fa0851d8b3c82d"
    })
    void portalAnimationsRetainAllThirtyTwoOriginalFrames(String destination, String expectedSha256)
            throws Exception {
        Path texture = RESOURCES.resolve("assets/mite/textures/block/portal_" + destination + ".png");
        String actualSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(texture)));
        assertEquals(expectedSha256, actualSha256, "Texture must retain the R196 source pixels");
        var image = ImageIO.read(texture.toFile());
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(512, image.getHeight());
        assertEquals(32, image.getHeight() / image.getWidth());
        JsonObject metadata = json(Path.of(texture + ".mcmeta"));
        assertEquals(JsonParser.parseString("{\"animation\":{}}"), metadata,
                "All source frames use the original default animation timing");
    }

    private static boolean matches(String variant, Map<String, String> properties) {
        for (String property : variant.split(",")) {
            String[] pair = property.split("=", 2);
            if (!pair[1].equals(properties.get(pair[0]))) return false;
        }
        return true;
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
