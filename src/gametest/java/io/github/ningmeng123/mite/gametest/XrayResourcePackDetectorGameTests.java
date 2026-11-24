package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.client.XrayResourcePackDetector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real pack reads preserve inherited culling and recognize detached full ore faces. @author NingMeng123 */
@GameTestHolder("mite_visual_packs")
@PrefixGameTestTemplate(false)
public final class XrayResourcePackDetectorGameTests {
    private XrayResourcePackDetectorGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void detectsAnInheritedUnculledCubeUnderRenamedModelPaths(GameTestHelper helper) throws IOException {
        String cube = """
                {"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                  "down": {"texture":"#all"}, "up": {"texture":"#all"},
                  "north": {"texture":"#all"}, "south": {"texture":"#all"},
                  "west": {"texture":"#all"}, "east": {"texture":"#all"}
                }}]}
                """;
        String culledCube = cube;
        for (String side : List.of("down", "up", "north", "south", "west", "east")) {
            culledCube = culledCube.replace("\"" + side + "\": {", "\"" + side + "\": {\"cullface\":\"" + side + "\",");
        }
        try (var fixture = DirectoryPack.create()) {
            fixture.writeModel("minecraft/blockstates/diamond_ore", """
                    {"variants":{"":{"model":"example:block/diamond"}}}
                    """);
            fixture.writeModel("example/models/block/diamond", """
                    {"parent":"example:block/mineral_variant","textures":{"all":"minecraft:block/diamond_ore"}}
                    """);
            fixture.writeModel("example/models/block/mineral_variant", "{\"parent\":\"block/framing\"}");
            fixture.writeModel("minecraft/models/block/framing", culledCube);
            helper.assertFalse(fixture.matches(), "Ordinary full cubes with neighbor culling stay available");

            fixture.writeModel("minecraft/models/block/framing", cube);
            helper.assertTrue(fixture.matches(), "The same ore models expose all faces without neighbor culling");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void detectsThinFullOreShellsWhileAllowingSmallDecorativeGeometry(GameTestHelper helper) throws IOException {
        String shell = """
                {"ambientocclusion":false,"textures":{"detail":"minecraft:block/diamond_ore"},"elements":[
                  {"from":[0,0.01,0],"to":[%1$d,0.03,%1$d],"faces":{"down":{"texture":"#detail"}}},
                  {"from":[0,15.97,0],"to":[%1$d,15.99,%1$d],"faces":{"up":{"texture":"#detail"}}},
                  {"from":[0,0,0.01],"to":[%1$d,%1$d,0.03],"faces":{"north":{"texture":"#detail"}}},
                  {"from":[0,0,15.97],"to":[%1$d,%1$d,15.99],"faces":{"south":{"texture":"#detail"}}},
                  {"from":[0.01,0,0],"to":[0.03,%1$d,%1$d],"faces":{"west":{"texture":"#detail"}}},
                  {"from":[15.97,0,0],"to":[15.99,%1$d,%1$d],"faces":{"east":{"texture":"#detail"}}}
                ]}
                """;
        try (var fixture = DirectoryPack.create()) {
            fixture.writeModel("minecraft/models/block/diamond_ore", String.format(Locale.ROOT, shell, 8));
            helper.assertFalse(fixture.matches(), "Unculled details that cover only part of a face stay available");

            fixture.writeModel("minecraft/models/block/diamond_ore", String.format(Locale.ROOT, shell, 16));
            helper.assertTrue(fixture.matches(), "Separate full planes preserve buried ore faces");
        }
        helper.succeed();
    }

    private record DirectoryPack(Path root) implements AutoCloseable {
        private static DirectoryPack create() throws IOException {
            return new DirectoryPack(Files.createTempDirectory("mite-xray-pack-").toAbsolutePath().normalize());
        }

        private void writeModel(String model, String json) throws IOException {
            Path destination = root.resolve("assets/" + model + ".json");
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, json);
        }

        private boolean matches() {
            var location = new PackLocationInfo("file/ordinary-labeled-assets", Component.literal("Ordinary assets"),
                    PackSource.DEFAULT, Optional.empty());
            try (var pack = new PathPackResources(location, root)) {
                return XrayResourcePackDetector.matches(pack);
            }
        }

        @Override public void close() throws IOException {
            List<Path> entries;
            try (var paths = Files.walk(root)) {
                entries = paths.sorted(Comparator.reverseOrder()).toList();
            }
            for (Path entry : entries) {
                if (!entry.normalize().startsWith(root)) throw new IOException("Unexpected test pack path: " + entry);
                Files.delete(entry);
            }
        }
    }
}
