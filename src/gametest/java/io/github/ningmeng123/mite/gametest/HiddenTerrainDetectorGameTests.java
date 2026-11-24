package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.client.HiddenTerrainDetector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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

/** Exercise inherited geometry and resized PNG transparency with actual pack reads. @author NingMeng123 */
@GameTestHolder("mite_visual_packs")
@PrefixGameTestTemplate(false)
public final class HiddenTerrainDetectorGameTests {
    private HiddenTerrainDetectorGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void redirectedTerrainCountsOnlyWhenAllVariantsHideTheirGeometry(GameTestHelper helper) throws IOException {
        try (var pack = new TestPack(Files.createTempDirectory("mite-hidden-terrain-models-"))) {
            pack.write("minecraft/models/block/dirt.json", "{\"parent\":\"example:block/hidden\"}");
            pack.write("minecraft/models/block/tuff.json", """
                    {
                      "elements": [{
                        "from": [8, 8, 8],
                        "to": [8, 8, 8],
                        "faces": {"up": {"texture": "minecraft:block/tuff"}}
                      }]
                    }
                    """);
            pack.write("example/models/block/hidden.json", "{\"parent\":\"minecraft:block/block\",\"elements\":[]}");
            pack.write("minecraft/blockstates/granite.json", """
                    {"variants":{"":[{"model":"example:block/hidden"},{"model":"minecraft:block/granite"}]}}
                    """);
            helper.assertTrue(!pack.matches(), "A visible weighted variant keeps granite from counting as hidden");

            pack.write("minecraft/blockstates/granite.json", "{\"variants\":{\"\":{\"model\":\"example:block/hidden\"}}}");
            helper.assertTrue(pack.matches(), "Three terrain blocks hidden through direct and inherited models are detected");

            pack.write("example/models/block/hidden.json", "{\"parent\":\"minecraft:block/cube_all\",\"elements\":[]}");
            helper.assertTrue(!pack.matches(), "An empty element list still inherits the visible cube geometry");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void transparentTerrainRequiresThreeBlocksAndBothColumnTextures(GameTestHelper helper) throws IOException {
        try (var pack = new TestPack(Files.createTempDirectory("mite-hidden-terrain-textures-"))) {
            pack.png("dirt", 16, true);
            pack.png("netherrack", 64, true);
            pack.png("deepslate", 32, true);
            pack.png("deepslate_top", 128, false);
            pack.png("oak_leaves", 16, true);
            helper.assertTrue(!pack.matches(), "Opaque deepslate ends and leaves do not count toward the threshold");

            pack.png("deepslate_top", 128, true);
            helper.assertTrue(pack.matches(), "Three nearly transparent terrain blocks are detected across different resolutions");
        }
        helper.succeed();
    }

    private record TestPack(Path root) implements AutoCloseable {
        boolean matches() {
            var location = new PackLocationInfo("test", Component.literal("test"), PackSource.DEFAULT, Optional.empty());
            try (var pack = new PathPackResources(location, root)) {
                return HiddenTerrainDetector.matches(pack);
            }
        }

        void write(String asset, String text) throws IOException {
            Files.writeString(assetPath(asset), text);
        }

        void png(String texture, int size, boolean transparent) throws IOException {
            var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            if (!transparent) {
                var graphics = image.createGraphics();
                graphics.fillRect(0, 0, size, size);
                graphics.dispose();
            }
            ImageIO.write(image, "png", assetPath("minecraft/textures/block/" + texture + ".png").toFile());
        }

        private Path assetPath(String asset) throws IOException {
            Path path = root.resolve("assets/" + asset);
            Files.createDirectories(path.getParent());
            return path;
        }

        @Override public void close() throws IOException {
            java.util.List<Path> paths;
            try (var tree = Files.walk(root)) {
                paths = tree.sorted(Comparator.reverseOrder()).toList();
            }
            for (Path path : paths) Files.delete(path);
        }
    }
}
