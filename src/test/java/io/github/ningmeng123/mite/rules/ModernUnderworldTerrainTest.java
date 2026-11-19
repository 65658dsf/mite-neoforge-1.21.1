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
    private static final int AREA_CHUNKS = 3;
    private static final int AREA_WIDTH = AREA_CHUNKS * 16;
    private static final int[][] FORWARD_NEIGHBORS = {{1, 0}, {0, 1}};

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
        assertEquals(0, ModernUnderworldTerrain.NETHER_PORTAL_MANTLE_Y);
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
                    boolean portalMantle = x >= 2 && x <= 13 && z >= 2 && z <= 13;
                    // The center patch belongs at Y=0 only, and the seams allow continuous descent.
                    for (int y = 115; y >= -64; y--) {
                        byte block = blocks[ModernUnderworldTerrain.index(x, y, z)];
                        if (y == 0 && portalMantle) {
                            assertEquals(R196UnderworldTerrain.MANTLE, block, "区块中央的夹心应位于 Y=0");
                            continue;
                        }
                        if (block == R196UnderworldTerrain.MANTLE) {
                            assertTrue(y >= -64 && y <= -60, "夹心以外的地幔只能位于现代底界范围");
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
                // The independent source oracle still fixes every original stone, water and cobweb.
                // The accessibility adaptation may only fill dry air above a supported stone floor.
                byte[] original = new R196UnderworldTerrain(seed).generate(chunkX, chunkZ);
                for (int legacyY = 120; legacyY <= 247; legacyY++) {
                    int start = ModernUnderworldTerrain.fromLegacyY(legacyY);
                    int end = ModernUnderworldTerrain.fromLegacyY(legacyY + 1);
                    for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                        byte expected = original[R196UnderworldTerrain.index(x, legacyY, z)];
                        for (int y = start; y < end; y++) {
                            byte actual = blocks[ModernUnderworldTerrain.index(x, y, z)];
                            if (expected == actual) continue;
                            assertEquals(R196UnderworldTerrain.AIR, expected, "台阶只能填入原有空气");
                            assertEquals(R196UnderworldTerrain.STONE, actual, "台阶必须由石头组成");
                            int floorY = y - 1;
                            while (original[R196UnderworldTerrain.index(x,
                                    ModernUnderworldTerrain.toLegacyY(floorY), z)] == R196UnderworldTerrain.AIR) {
                                floorY--;
                            }
                            assertTrue(y - floorY <= 2, "坡脚最多在原有地面上增加两层");
                            assertEquals(R196UnderworldTerrain.STONE, original[R196UnderworldTerrain.index(x,
                                    ModernUnderworldTerrain.toLegacyY(floorY), z)], "台阶必须落在原有石地面上");
                            int topY = y;
                            while (blocks[ModernUnderworldTerrain.index(x, topY + 1, z)] == R196UnderworldTerrain.STONE) {
                                topY++;
                            }
                            for (int dy = 1; dy <= 3; dy++) {
                                assertEquals(R196UnderworldTerrain.AIR,
                                        blocks[ModernUnderworldTerrain.index(x, topY + dy, z)], "台阶上方应保留三格净空");
                            }
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
    void dryTerracesGainSingleBlockRoutesAcrossPositiveAndNegativeChunks() {
        int beforeDoubleSteps = 0, afterDoubleSteps = 0;
        int restoredRoutes = 0, restoredBorderRoutes = 0;
        for (long seed : new long[]{0, 1, 196, 123456789L}) {
            TerrainSample sample = terrainSample(seed);
            FloorEdges before = floorEdges(sample.before());
            FloorEdges after = floorEdges(sample.after());
            assertTrue(after.singleSteps() > before.singleSteps(), "一格台阶应增加，种子 " + seed);
            assertTrue(after.doubleSteps() < before.doubleSteps(), "两格断层应减少，种子 " + seed);
            assertOriginalWalkableEdgesRemain(sample);
            RestoredRoutes routes = restoredDoubleLedgeRoutes(sample);
            assertTrue(routes.routes() > 0, "原有两格台地应得到可直接上去的路径，种子 " + seed);
            assertTrue(routes.borderRoutes() > 0, "跨区块边缘也应得到可走台阶，种子 " + seed);
            beforeDoubleSteps += before.doubleSteps();
            afterDoubleSteps += after.doubleSteps();
            restoredRoutes += routes.routes();
            restoredBorderRoutes += routes.borderRoutes();
        }
        assertTrue(afterDoubleSteps * 5 < beforeDoubleSteps * 3, "固定样本的两格断层应减少超过四成");
        assertTrue(restoredRoutes > 400, "实际两格高台的端点之间应新增足够的一格通路");
        assertTrue(restoredBorderRoutes > 10, "区块接缝不能遗漏台阶调整");
    }

    @Test
    void neighboringChunksProduceTheSameStepsInEitherGenerationOrder() {
        int[][] positions = {{-1, -1}, {-1, 0}, {0, -1}, {0, 0}};
        ModernUnderworldTerrain forward = new ModernUnderworldTerrain(196);
        byte[][] expected = new byte[positions.length][];
        for (int i = 0; i < positions.length; i++) {
            expected[i] = forward.generate(positions[i][0], positions[i][1]);
        }
        ModernUnderworldTerrain reverse = new ModernUnderworldTerrain(196);
        for (int i = positions.length - 1; i >= 0; i--) {
            assertArrayEquals(expected[i], reverse.generate(positions[i][0], positions[i][1]),
                    "接缝台阶不能依赖相邻区块的生成顺序");
        }
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

    private static TerrainSample terrainSample(long seed) {
        R196UnderworldTerrain source = new R196UnderworldTerrain(seed);
        ModernUnderworldTerrain modern = new ModernUnderworldTerrain(seed);
        byte[][] before = new byte[AREA_CHUNKS * AREA_CHUNKS][];
        byte[][] after = new byte[before.length][];
        // The sample spans both sides of zero and every internal X/Z chunk boundary.
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                int chunk = (chunkX + 1) * AREA_CHUNKS + chunkZ + 1;
                byte[] original = source.generate(chunkX, chunkZ);
                before[chunk] = new byte[16 * 16 * ModernUnderworldTerrain.HEIGHT];
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    for (int y = ModernUnderworldTerrain.CAVERN_OFFSET;
                         y < ModernUnderworldTerrain.CAVERN_TOP_EXCLUSIVE; y++) {
                        before[chunk][ModernUnderworldTerrain.index(x, y, z)] = original[R196UnderworldTerrain.index(
                                x, ModernUnderworldTerrain.toLegacyY(y), z)];
                    }
                }
                after[chunk] = modern.generate(chunkX, chunkZ);
            }
        }
        return new TerrainSample(before, after);
    }

    private static FloorEdges floorEdges(byte[][] chunks) {
        int singleSteps = 0, doubleSteps = 0;
        for (int x = 0; x < AREA_WIDTH; x++) for (int z = 0; z < AREA_WIDTH; z++) {
            for (int y = ModernUnderworldTerrain.CAVERN_OFFSET + 2;
                 y < ModernUnderworldTerrain.CAVERN_TOP_EXCLUSIVE - 5; y++) {
                if (!floor(chunks, x, y, z, 3)) continue;
                for (int[] direction : FORWARD_NEIGHBORS) {
                    int nx = x + direction[0], nz = z + direction[1];
                    if (nx >= AREA_WIDTH || nz >= AREA_WIDTH) continue;
                    for (int dy : new int[]{-2, -1, 1, 2}) {
                        if (!floor(chunks, nx, y + dy, nz, 3)) continue;
                        if (Math.abs(dy) == 1) singleSteps++;
                        else doubleSteps++;
                    }
                }
            }
        }
        return new FloorEdges(singleSteps, doubleSteps);
    }

    private static void assertOriginalWalkableEdgesRemain(TerrainSample sample) {
        for (int x = 0; x < AREA_WIDTH; x++) for (int z = 0; z < AREA_WIDTH; z++) {
            for (int y = ModernUnderworldTerrain.CAVERN_OFFSET + 2;
                 y < ModernUnderworldTerrain.CAVERN_TOP_EXCLUSIVE - 5; y++) {
                if (!floor(sample.before(), x, y, z, 2)) continue;
                int newY = raisedFloor(sample.after(), x, y, z);
                assertTrue(floor(sample.after(), x, newY, z, 2), "原有站立位置应保留双格净空");
                for (int[] direction : FORWARD_NEIGHBORS) {
                    int nx = x + direction[0], nz = z + direction[1];
                    if (nx >= AREA_WIDTH || nz >= AREA_WIDTH) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        if (!floor(sample.before(), nx, y + dy, nz, 2)) continue;
                        int neighborY = raisedFloor(sample.after(), nx, y + dy, nz);
                        assertTrue(Math.abs(newY - neighborY) <= 1, "填台阶不能把原有一格通路变成断层");
                        assertTrue(floor(sample.after(), nx, neighborY, nz, 2), "邻接通路应保留双格净空");
                    }
                }
            }
        }
    }

    private static RestoredRoutes restoredDoubleLedgeRoutes(TerrainSample sample) {
        int routes = 0, borderRoutes = 0;
        for (int x = 0; x < AREA_WIDTH; x++) for (int z = 0; z < AREA_WIDTH; z++) {
            for (int y = ModernUnderworldTerrain.CAVERN_OFFSET + 2;
                 y < ModernUnderworldTerrain.CAVERN_TOP_EXCLUSIVE - 5; y++) {
                if (!floor(sample.before(), x, y, z, 3)) continue;
                for (int[] direction : FORWARD_NEIGHBORS) {
                    int nx = x + direction[0], nz = z + direction[1];
                    if (nx >= AREA_WIDTH || nz >= AREA_WIDTH) continue;
                    for (int dy : new int[]{-2, 2}) {
                        if (!floor(sample.before(), nx, y + dy, nz, 3)) continue;
                        int newY = raisedFloor(sample.after(), x, y, z);
                        int neighborY = raisedFloor(sample.after(), nx, y + dy, nz);
                        if (Math.abs(newY - neighborY) > 1) continue;
                        if (!floor(sample.after(), x, newY, z, 3)
                                || !floor(sample.after(), nx, neighborY, nz, 3)) continue;
                        routes++;
                        if (x / 16 != nx / 16 || z / 16 != nz / 16) borderRoutes++;
                    }
                }
            }
        }
        return new RestoredRoutes(routes, borderRoutes);
    }

    private static boolean floor(byte[][] chunks, int x, int y, int z, int headroom) {
        if (block(chunks, x, y, z) != R196UnderworldTerrain.STONE) return false;
        for (int dy = 1; dy <= headroom; dy++) {
            if (block(chunks, x, y + dy, z) != R196UnderworldTerrain.AIR) return false;
        }
        return true;
    }

    private static int raisedFloor(byte[][] chunks, int x, int y, int z) {
        int top = y;
        while (top < y + 2 && block(chunks, x, top + 1, z) == R196UnderworldTerrain.STONE) top++;
        return top;
    }

    private static byte block(byte[][] chunks, int x, int y, int z) {
        return chunks[x / 16 * AREA_CHUNKS + z / 16][ModernUnderworldTerrain.index(x % 16, y, z % 16)];
    }

    private record TerrainSample(byte[][] before, byte[][] after) {}
    private record FloorEdges(int singleSteps, int doubleSteps) {}
    private record RestoredRoutes(int routes, int borderRoutes) {}

    private static String digest(byte[] values) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(values));
    }
}
