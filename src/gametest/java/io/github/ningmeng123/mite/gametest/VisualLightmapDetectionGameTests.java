package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.client.NightVisionShaderPack;
import io.github.ningmeng123.mite.client.VisualLightmapDetector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** @author NingMeng123 */
@GameTestHolder("mite_visual_packs")
@PrefixGameTestTemplate(false)
public final class VisualLightmapDetectionGameTests {
    private VisualLightmapDetectionGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void identifiesUnlitTerrainByItsColorFlow(GameTestHelper helper) throws IOException {
        withTemporaryPack(root -> {
            Path shaders = Files.createDirectory(root.resolve("shaders"));
            Files.writeString(shaders.resolve("gbuffers_terrain.fsh"), """
                    #version 330
                    uniform sampler2D terrainAtlas;
                    in vec2 terrainUV;
                    in vec4 surfaceTint;
                    out vec4 surfaceColor;
                    void main() {
                        surfaceColor = surfaceTint * texture(terrainAtlas, terrainUV);
                    }
                    """);
            Path vertex = shaders.resolve("gbuffers_terrain.vsh");
            Files.writeString(vertex, """
                    #version 330
                    in vec4 vaColor;
                    out vec4 surfaceTint;
                    void main() {
                        gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
                        surfaceTint = vaColor;
                    }
                    """);
            helper.assertTrue(NightVisionShaderPack.matches(shaders), "Raw terrain color is detected after renaming variables");

            Files.writeString(vertex, """
                    #version 330
                    uniform sampler2D lightmap;
                    in vec4 vaColor;
                    in vec2 lightUV;
                    out vec4 surfaceTint;
                    void main() {
                        gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
                        surfaceTint = vaColor * texture(lightmap, lightUV);
                    }
                    """);
            helper.assertFalse(NightVisionShaderPack.matches(shaders), "A terrain program which samples actual lighting is retained");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void identifiesFullbrightNormalRowsAndPreservesNightVisionEffectRows(GameTestHelper helper) throws IOException {
        withTemporaryPack(root -> {
            Path lightmap = root.resolve("assets/minecraft/optifine/lightmap/world0.png");
            Files.createDirectories(lightmap.getParent());
            var location = new PackLocationInfo("test", Component.literal("test"), PackSource.DEFAULT, Optional.empty());

            // The independent sky and block light ramps both contribute to the light of an unlit block.
            writeLightmap(lightmap, 0xFF777777);
            try (var pack = new PathPackResources(location, root)) {
                helper.assertTrue(VisualLightmapDetector.matches(pack), "Bright normal rows raise the unlit light level");
            }

            writeLightmap(lightmap, 0xFF101010);
            try (var pack = new PathPackResources(location, root)) {
                helper.assertFalse(VisualLightmapDetector.matches(pack), "Bright Night Vision rows alone are retained");
            }
        });
        helper.succeed();
    }

    private static void writeLightmap(Path path, int normalLight) throws IOException {
        BufferedImage image = new BufferedImage(8, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            int color = y < 32 ? normalLight : 0xFFFFFFFF;
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, color);
        }
        try (var stream = Files.newOutputStream(path)) {
            if (!ImageIO.write(image, "png", stream)) throw new IOException("PNG writer is unavailable");
        }
    }

    private static void withTemporaryPack(PackCheck check) throws IOException {
        Path directory = Files.createTempDirectory("mite-visual-pack-test-");
        Path archive = directory.resolve("pack.zip");
        try (var fileSystem = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            check.run(fileSystem.getPath("/"));
        } finally {
            Files.deleteIfExists(archive);
            Files.delete(directory);
        }
    }

    @FunctionalInterface
    private interface PackCheck {
        void run(Path root) throws IOException;
    }
}
