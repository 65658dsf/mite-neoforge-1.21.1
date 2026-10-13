package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** R196 original bytecode fixtures, independently exported before the new feature implementation. @author NingMeng123 */
class R196OreOracleTest {
    @TestFactory Stream<DynamicTest> heightSamplingMatchesOriginalBytecodeAndRandomState() throws IOException {
        return oracle().getAsJsonArray("height_cases").asList().stream().map(element -> {
            JsonObject row = element.getAsJsonObject();
            return DynamicTest.dynamicTest(row.get("kind").getAsString() + "/" + row.get("dimension").getAsString(), () -> {
                Random random = new Random(Long.parseLong(row.get("random_seed").getAsString()));
                for (var height : row.getAsJsonArray("heights")) {
                    assertEquals(height.getAsInt(), R196OreRules.randomHeight(kind(row), dimension(row), random));
                }
                assertEquals(Long.parseLong(row.get("next_long").getAsString()), random.nextLong(),
                        "The height batch must consume exactly the source random sequence");
            });
        });
    }

    @TestFactory Stream<DynamicTest> veinBudgetsMatchOriginalBytecodeIncludingFloatRoundingAndEmptyVeins() throws IOException {
        return oracle().getAsJsonArray("size_cases").asList().stream().map(element -> {
            JsonObject row = element.getAsJsonObject();
            return DynamicTest.dynamicTest(row.get("kind").getAsString() + "/" + row.get("dimension").getAsString()
                    + "/y=" + row.get("height").getAsInt() + "/depth=" + row.get("depth").getAsBoolean(), () -> {
                for (var elementSample : row.getAsJsonArray("samples")) {
                    JsonObject sample = elementSample.getAsJsonObject();
                    Random random = new Random(Long.parseLong(sample.get("seed").getAsString()));
                    int budget = R196OreRules.veinSize(kind(row), dimension(row), row.get("height").getAsInt(), random, row.get("depth").getAsBoolean());
                    assertEquals(sample.get("budget").getAsInt(), budget, "Source vein budget differs at seed " + sample.get("seed").getAsString());
                    assertEquals(Long.parseLong(sample.get("next_long").getAsString()), random.nextLong(),
                            "The growth budget must consume the source random sequence even when zero blocks result");
                }
            });
        });
    }

    @TestFactory Stream<DynamicTest> recursiveConnectedGrowthMatchesEveryOriginalCoordinateAndRandomState() throws IOException {
        return oracle().getAsJsonArray("growth_cases").asList().stream().map(element -> {
            JsonObject row = element.getAsJsonObject();
            return DynamicTest.dynamicTest("budget=" + row.get("budget").getAsInt() + "/seed=" + row.get("seed").getAsString(), () -> {
                Random random = new Random(Long.parseLong(row.get("seed").getAsString()));
                Set<Pos> placed = new HashSet<>();
                R196OreRules.VeinAccess sourceWorld = new R196OreRules.VeinAccess() {
                    @Override public boolean canReplace(int x, int y, int z) {
                        return x >= -16 && x < 32 && y >= 0 && y < 256 && z >= -16 && z < 32 && !placed.contains(new Pos(x, y, z));
                    }
                    @Override public boolean supported(int x, int y, int z) { return y > 0; }
                    @Override public void replace(int x, int y, int z) { assertTrue(placed.add(new Pos(x, y, z))); }
                };
                JsonArray start = row.getAsJsonArray("start");
                int grown = R196OreRules.growVein(sourceWorld, row.get("budget").getAsInt(), start.get(0).getAsInt(), start.get(1).getAsInt(),
                        start.get(2).getAsInt(), false, random);
                Set<Pos> original = new HashSet<>();
                for (var coordinate : row.getAsJsonArray("positions")) {
                    JsonArray pos = coordinate.getAsJsonArray();
                    original.add(new Pos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()));
                }
                assertEquals(row.get("grown").getAsInt(), grown);
                assertEquals(original, placed, "Every connected source growth coordinate must match, not just the total count");
                assertEquals(Long.parseLong(row.get("next_long").getAsString()), random.nextLong());
            });
        });
    }

    private static JsonObject oracle() throws IOException {
        JsonObject spec = JsonParser.parseString(Files.readString(Path.of("spec/ores-023.json"))).getAsJsonObject();
        assertEquals("fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3", spec.get("archive_sha256").getAsString());
        JsonObject oracle = spec.getAsJsonObject("source_oracle");
        assertEquals("guarded_reference_bytecode_ore_sampling_and_growth", oracle.get("mode").getAsString());
        assertFalse(oracle.get("game_main_invoked").getAsBoolean(), "Fixtures must not masquerade as a legacy client playtest");
        return oracle;
    }
    private static R196OreRules.Kind kind(JsonObject row) { return R196OreRules.Kind.valueOf(row.get("kind").getAsString().toUpperCase(Locale.ROOT)); }
    private static R196OreRules.Dimension dimension(JsonObject row) { return R196OreRules.Dimension.valueOf(row.get("dimension").getAsString().toUpperCase(Locale.ROOT)); }
    private record Pos(int x, int y, int z) {}
}
