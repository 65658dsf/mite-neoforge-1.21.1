package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.worldgen.R196UnderworldTerrain;
import io.github.ningmeng123.mite.worldgen.R196MyceliumPostField;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Expectations are exported from the archived R196 bytecode, independently of production classes. @author NingMeng123 */
final class R196UnderworldTerrainTest {
    @TestFactory
    Stream<DynamicTest> archivedTerrainMatchesEveryBlock() throws Exception {
        JsonObject fixture = JsonParser.parseString(Files.readString(Path.of("spec/r196-underworld-terrain-fixtures.json"))).getAsJsonObject();
        assertEquals("fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3", fixture.get("archive_sha256").getAsString());
        return fixture.getAsJsonArray("cases").asList().stream().map(entry -> {
            JsonObject expected = entry.getAsJsonObject();
            long seed = Long.parseLong(expected.get("seed").getAsString());
            int x = expected.get("chunk_x").getAsInt(), z = expected.get("chunk_z").getAsInt();
            return DynamicTest.dynamicTest(seed + " / " + x + "," + z, () -> {
                byte[] actual = new R196UnderworldTerrain(seed).generate(x, z);
                assertEquals(expected.get("sha256").getAsString(), digest(actual));
                JsonObject counts = expected.getAsJsonObject("counts");
                assertCount(actual, R196UnderworldTerrain.AIR, counts.get("air").getAsInt());
                assertCount(actual, R196UnderworldTerrain.STONE, counts.get("stone").getAsInt());
                assertCount(actual, R196UnderworldTerrain.BEDROCK, counts.get("bedrock").getAsInt());
                assertCount(actual, R196UnderworldTerrain.WATER, counts.get("water").getAsInt());
                assertCount(actual, R196UnderworldTerrain.COBWEB, counts.get("cobweb").getAsInt());
                assertCount(actual, R196UnderworldTerrain.MANTLE, counts.get("mantle").getAsInt());
                var posts = R196MyceliumPostField.nearbyPosts(x, z, new R196UnderworldTerrain(seed).hashedSeed());
                var expectedPosts = expected.getAsJsonArray("mycelium_posts");
                assertEquals(expectedPosts.size(), posts.size());
                for (int i = 0; i < posts.size(); i++) {
                    var ep = expectedPosts.get(i).getAsJsonObject();
                    assertEquals(ep.get("x").getAsInt(), posts.get(i).x());
                    assertEquals(ep.get("z").getAsInt(), posts.get(i).z());
                    assertEquals(Long.parseLong(ep.get("seed").getAsString()), posts.get(i).seed());
                }
            });
        });
    }

    @Test
    void bottomAndRoofRemainClosedWithTheCorrectMaterials() {
        byte[] terrain = new R196UnderworldTerrain(123456789L).generate(-7, 11);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            assertEquals(R196UnderworldTerrain.MANTLE, terrain[R196UnderworldTerrain.index(x, 0, z)]);
            assertEquals(R196UnderworldTerrain.BEDROCK, terrain[R196UnderworldTerrain.index(x, 255, z)]);
            for (int y = 0; y < 120; y++) {
                byte block = terrain[R196UnderworldTerrain.index(x, y, z)];
                assertTrue(block == R196UnderworldTerrain.STONE || block == R196UnderworldTerrain.BEDROCK || block == R196UnderworldTerrain.MANTLE);
            }
            for (int y = 144; y < 256; y++) assertNotEquals(R196UnderworldTerrain.WATER, terrain[R196UnderworldTerrain.index(x, y, z)]);
        }
    }

    @Test
    void parallelGenerationIsIndependentOfChunkOrder() throws Exception {
        R196UnderworldTerrain generator = new R196UnderworldTerrain(123456789L);
        String a = digest(generator.generate(0, 0));
        String b = digest(generator.generate(-7, 11));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<String>> calls = List.of(() -> digest(generator.generate(-7, 11)),
                    () -> digest(generator.generate(0, 0)), () -> digest(generator.generate(-7, 11)),
                    () -> digest(generator.generate(0, 0)));
            var actual = executor.invokeAll(calls);
            assertEquals(b, actual.get(0).get());
            assertEquals(a, actual.get(1).get());
            assertEquals(b, actual.get(2).get());
            assertEquals(a, actual.get(3).get());
        }
    }

    private static String digest(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static void assertCount(byte[] values, byte block, int expected) {
        int count = 0;
        for (byte value : values) if (value == block) count++;
        assertEquals(expected, count);
    }
}
