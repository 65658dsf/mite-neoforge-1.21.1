package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static io.github.ningmeng123.mite.rules.R196DifficultyRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Numeric fixtures transcribed independently from abw.I/aei bytecode. @author NingMeng123 */
class R196DifficultyRulesTest {
    @Test void specificationPreservesSourceProvenanceAndIndependentNumericExamples() throws Exception {
        var spec = JsonParser.parseString(Files.readString(Path.of("spec/difficulty-system.json"))).getAsJsonObject();
        assertEquals("NingMeng123", spec.get("author").getAsString());
        assertEquals("fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3",
                spec.get("archive_sha256").getAsString());
        assertEquals(3, spec.getAsJsonObject("fixed_world_difficulty").get("id").getAsInt());
        assertFalse(spec.getAsJsonObject("fixed_world_difficulty").get("extra_difficulty_id").getAsBoolean());
        var regional = spec.getAsJsonObject("regional_tension");
        assertFalse(regional.get("world_day_growth_term").getAsBoolean());
        assertFalse(regional.get("player_level_scaler").getAsBoolean());
        for (var element : regional.getAsJsonArray("numeric_fixtures")) {
            var row = element.getAsJsonObject();
            assertEquals(row.get("expected").getAsFloat(), tension(row.get("difficulty").getAsInt(),
                    row.get("inhabited_ticks").getAsLong(), row.get("phase").getAsFloat(),
                    row.get("valid").getAsBoolean()), row.toString());
        }
        var classes = spec.getAsJsonObject("source_classes");
        assertEquals(20, classes.size());
        for (var entry : classes.entrySet()) {
            assertTrue(entry.getValue().getAsJsonObject().get("sha256").getAsString().matches("[0-9a-f]{64}"));
        }
        // The original analysis archive is local evidence and is not redistributed with the project.
        if (Files.exists(Path.of("MITE 1.6.4 R196.zip"))) try (var archive = new ZipFile("MITE 1.6.4 R196.zip")) {
            for (var entry : classes.entrySet()) {
                var archiveEntry = archive.getEntry("MITE 1.6.4 Installation Files/1.6.4-MITE/class/"
                        + entry.getKey() + ".class");
                assertNotNull(archiveEntry, entry.getKey());
                try (var input = archive.getInputStream(archiveEntry)) {
                    assertEquals(entry.getValue().getAsJsonObject().get("sha256").getAsString(),
                            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())),
                            entry.getKey());
                }
            }
        }
    }

    @Test void sourceExtremeIsTheExistingHardIdRatherThanAnotherDifficulty() {
        assertEquals(3, FIXED_DIFFICULTY);
        assertEquals(3_600_000L, INHABITED_TIME_TO_MAXIMUM);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0,0", "0,3600000,1,0", "1,0,0,0", "1,0,1,0.125",
            "1,1800000,0,0.1875", "1,3600000,1,0.5",
            "2,0,0,0", "2,0,1,0.25", "2,1800000,0,0.375", "2,3600000,1,1",
            "3,0,0,0", "3,0,1,0.25", "3,1800000,0,0.5", "3,1800000,1,0.75",
            "3,3600000,0,1", "3,3600000,1,1.25", "3,7200000,1,1.25"
    })
    void sourceDifficultyBranchesUseIndependentNumericFixtures(int difficulty, long inhabited, float phase,
            float expected) {
        assertEquals(expected, tension(difficulty, inhabited, phase, true));
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "2", "3"})
    void unloadedOrOutOfHeightPositionsHaveNoMoonOrInhabitationContribution(int difficulty) {
        assertEquals(0F, tension(difficulty, Long.MAX_VALUE, 1F, false));
        assertEquals(0F, tension(difficulty, 0L, 1F, false));
    }

    @Test void twoTicksUseFloatingPointDivisionRatherThanIntegerDivision() {
        assertEquals(Float.intBitsToFloat(0x3515217d), tension(3, 2L, 0F, true));
        assertTrue(tension(3, 2L, 0F, true) > 0F);
    }

    @Test void inhabitedTimeConvertsToFloatBeforeTheSourceDivisionAndThenClamps() {
        assertEquals(Float.intBitsToFloat(0x3efffff7), tension(3, 1_799_999L, 0F, true));
        assertEquals(.5F, tension(3, 1_800_000L, 0F, true));
        assertEquals(Float.intBitsToFloat(0x3f7ffffb), tension(3, 3_599_999L, 0F, true));
        assertEquals(1F, tension(3, 3_600_000L, 0F, true));
        assertEquals(1F, tension(3, 3_600_001L, 0F, true));
        assertEquals(1F, tension(3, Long.MAX_VALUE, 0F, true));
        assertEquals(0F, tension(3, Long.MIN_VALUE, 0F, true));
    }

    @Test void finalClampIsOnePointFiveButOrdinarySourcePhasesOnlyReachOnePointTwoFive() {
        assertEquals(1.25F, tension(3, 3_600_000L, 1F, true));
        assertEquals(1.5F, tension(3, 3_600_000L, 2F, true));
        assertEquals(1.5F, tension(3, 3_600_000L, 10F, true));
        assertEquals(1F, tension(2, 3_600_000L, 10F, true));
        assertEquals(0F, tension(3, 0L, -1F, true));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0.75", "23999,0.75", "24000,0.5", "47999,0.5", "48000,0.25",
            "72000,0", "96000,0.25", "120000,0.5", "144000,0.75", "168000,1",
            "191999,1", "192000,0.75", "744000,1", "3048000,1"
    })
    void sourcePhaseHasThePlusOneOffsetAndUsesRawFraction(long time, float expected) {
        assertEquals(expected, phaseFraction(time));
    }

    @Test void regionalTensionHasNoGrowingWorldDayOrPlayerLevelMultiplier() {
        // Equal moon phases at world days 1, 9 and 1,000,001 retain the same result.
        assertEquals(.1875F, tension(3, 0L, phaseFraction(0L), true));
        assertEquals(.1875F, tension(3, 0L, phaseFraction(192_000L), true));
        assertEquals(.1875F, tension(3, 0L, phaseFraction(24_000_000_000L), true));
        // These source inputs describe the location; no actor or experience is a formula input.
        assertEquals(.6875F, tension(3, 1_800_000L, phaseFraction(0L), true));
    }

    @ParameterizedTest
    @CsvSource({"-1,1", "-24000,1", "-24001,0.75", "-168000,0.5", "-192000,0.75"})
    void modernNegativeTimeExtensionUsesFloorDivisionAndNeverANegativeArrayIndex(long time, float expected) {
        assertEquals(expected, phaseFraction(time));
    }

    @Test void modernLongTimeBoundsDoNotOverflowTheSourceEightPhaseCycle() {
        assertEquals(.25F, phaseFraction(Long.MAX_VALUE));
        assertEquals(.5F, phaseFraction(Long.MIN_VALUE));
        assertEquals(.75F, phaseFraction(51_539_607_552_000L),
                "Casting the source day to int also wraps by a multiple of eight");
    }
}
