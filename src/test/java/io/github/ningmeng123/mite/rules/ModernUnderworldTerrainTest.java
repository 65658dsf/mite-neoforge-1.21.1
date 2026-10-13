package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.worldgen.ModernUnderworldTerrain;
import io.github.ningmeng123.mite.worldgen.R196UnderworldTerrain;
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

/** Tests the documented modern height/accessibility contract, separately from the source oracle. @author NingMeng123 */
final class ModernUnderworldTerrainTest {
    @Test
    void modernDimensionsAndGameplayBoundariesAreExplicit() {
        assertEquals(2, ModernUnderworldTerrain.CURRENT_LAYOUT_VERSION);
        assertEquals(-64, ModernUnderworldTerrain.MIN_Y);
        assertEquals(384, ModernUnderworldTerrain.HEIGHT);
        assertEquals(320, ModernUnderworldTerrain.MAX_Y_EXCLUSIVE);
        assertEquals(116, ModernUnderworldTerrain.CAVERN_OFFSET);
        assertEquals(308, ModernUnderworldTerrain.CAVERN_TOP_EXCLUSIVE);
        assertEquals(152, ModernUnderworldTerrain.SEA_LEVEL);
        assertEquals(176, ModernUnderworldTerrain.SPAWN_HEIGHT);
        assertEquals(-60, ModernUnderworldTerrain.MAX_MANTLE_Y);
        assertEquals(316, ModernUnderworldTerrain.MIN_ROOF_Y);
    }

    @Test
    void sourceLayerBoundariesMapToNegativeAndPositiveModernHeight() {
        int[][] boundaries = {{0, -64}, {1, -62}, {2, -61}, {3, -59}, {32, -16},
                {72, 44}, {96, 80}, {120, 116}, {144, 152}, {160, 176}, {248, 308},
                {253, 316}, {255, 319}, {256, 320}};
        for (int[] boundary : boundaries) {
            assertEquals(boundary[1], ModernUnderworldTerrain.fromLegacyY(boundary[0]));
            assertEquals(boundary[0], ModernUnderworldTerrain.toLegacyY(boundary[1]));
            assertEquals(boundary[0] - 1, ModernUnderworldTerrain.toLegacyY(boundary[1] - 1));
        }
    }

    @Test
    void negativeWorldYHasAUniqueArraySlotInEveryColumn() {
        boolean[] occupied = new boolean[16 * 16 * 384];
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            for (int y = -64; y <= 319; y++) {
                int index = ModernUnderworldTerrain.index(x, y, z);
                assertTrue(index >= 0 && index < occupied.length);
                assertFalse(occupied[index], "两个高度或柱子不能覆盖同一数组位置");
                occupied[index] = true;
            }
        }
        assertEquals(0, ModernUnderworldTerrain.index(0, -64, 0));
        assertEquals(occupied.length - 1, ModernUnderworldTerrain.index(15, 319, 15));
    }

    @Test
    void dimensionDataAdvertisesTheSameModernHeight() throws Exception {
        JsonObject dimension = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/data/mite/dimension_type/underworld.json"))).getAsJsonObject();
        assertEquals(-64, dimension.get("min_y").getAsInt());
        assertEquals(384, dimension.get("height").getAsInt());
        assertEquals(384, dimension.get("logical_height").getAsInt());
        assertFalse(dimension.get("has_skylight").getAsBoolean());
        assertFalse(dimension.get("ultrawarm").getAsBoolean());
    }

    @TestFactory
    Stream<DynamicTest> everyArchivedSeedCanReachMantleWithoutMiningBedrock() throws Exception {
        JsonObject fixture = JsonParser.parseString(Files.readString(
                Path.of("spec/r196-underworld-terrain-fixtures.json"))).getAsJsonObject();
        return fixture.getAsJsonArray("cases").asList().stream().map(entry -> {
            JsonObject sourceCase = entry.getAsJsonObject();
            long seed = Long.parseLong(sourceCase.get("seed").getAsString());
            int chunkX = sourceCase.get("chunk_x").getAsInt(), chunkZ = sourceCase.get("chunk_z").getAsInt();
            return DynamicTest.dynamicTest("modern " + seed + " / " + chunkX + "," + chunkZ, () -> {
                byte[] blocks = new ModernUnderworldTerrain(seed).generate(chunkX, chunkZ);
                assertEquals(16 * 16 * 384, blocks.length);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    assertEquals(R196UnderworldTerrain.MANTLE, blocks[ModernUnderworldTerrain.index(x, -64, z)]);
                    assertEquals(R196UnderworldTerrain.BEDROCK, blocks[ModernUnderworldTerrain.index(x, 319, z)]);
                    boolean reachedMantle = false;
                    // Descending from the cavern base must require ordinary stone mining only.
                    for (int y = 115; y >= -64; y--) {
                        byte block = blocks[ModernUnderworldTerrain.index(x, y, z)];
                        if (block == R196UnderworldTerrain.MANTLE) {
                            assertTrue(y >= -64 && y <= -60, "地幔只能位于现代底界范围");
                            reachedMantle = true;
                            break;
                        }
                        assertEquals(R196UnderworldTerrain.STONE, block, "生存下掘不能被内部基岩封住");
                    }
                    assertTrue(reachedMantle);
                    for (int y = -59; y < 316; y++) {
                        assertNotEquals(R196UnderworldTerrain.BEDROCK, blocks[ModernUnderworldTerrain.index(x, y, z)],
                                "只有顶部屋顶可以保留基岩");
                    }
                }
                // The source oracle independently verifies these 25 chunks. Expansion must retain
                // their complete cavern contents, including water and cobwebs, at the mapped layers.
                byte[] original = new R196UnderworldTerrain(seed).generate(chunkX, chunkZ);
                for (int legacyY = 120; legacyY <= 247; legacyY++) {
                    int start = ModernUnderworldTerrain.fromLegacyY(legacyY);
                    int end = ModernUnderworldTerrain.fromLegacyY(legacyY + 1);
                    for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                        byte expected = original[R196UnderworldTerrain.index(x, legacyY, z)];
                        for (int y = start; y < end; y++) {
                            assertEquals(expected, blocks[ModernUnderworldTerrain.index(x, y, z)], "洞穴内容映射后改变");
                        }
                    }
                }
            });
        });
    }

    @Test
    void waterAndCobwebExamplesRemainPresentAboveTheMantleMiningZone() {
        byte[] water = new ModernUnderworldTerrain(1).generate(0, 0);
        int waterCount = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y <= 319; y++) {
            if (water[ModernUnderworldTerrain.index(x, y, z)] == R196UnderworldTerrain.WATER) {
                assertTrue(y >= 116 && y < 152, "洞穴水只能处于扩展后的水面以下");
                waterCount++;
            }
        }
        assertTrue(waterCount >= 207, "源版 seed 1 / 0,0 的 207 个水格不能丢失");
        byte[] cobweb = new ModernUnderworldTerrain(0).generate(-11, 1);
        int cobwebCount = 0;
        for (byte block : cobweb) if (block == R196UnderworldTerrain.COBWEB) cobwebCount++;
        assertTrue(cobwebCount >= 2, "源版 seed 0 / -11,1 的两处蛛网不能丢失");
    }

    @Test
    void modernGenerationStillSupportsParallelChunks() throws Exception {
        ModernUnderworldTerrain generator = new ModernUnderworldTerrain(123456789L);
        String first = digest(generator.generate(0, 0));
        String second = digest(generator.generate(-7, 11));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<String>> calls = List.of(() -> digest(generator.generate(-7, 11)),
                    () -> digest(generator.generate(0, 0)), () -> digest(generator.generate(-7, 11)));
            var results = executor.invokeAll(calls);
            assertEquals(second, results.get(0).get());
            assertEquals(first, results.get(1).get());
            assertEquals(second, results.get(2).get());
        }
    }

    private static String digest(byte[] values) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(values));
    }
}
