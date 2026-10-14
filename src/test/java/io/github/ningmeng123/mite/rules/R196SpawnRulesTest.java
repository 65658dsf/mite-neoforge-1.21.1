package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Literal aci/js/tm arithmetic and independent guarded table evidence. @author NingMeng123 */
class R196SpawnRulesTest {
    @Test void negativeOverworldAndScaledUnderworldHaveSeparateHeightMappings() {
        assertEquals(0, R196SpawnRules.sourceY(-64, false)); assertEquals(0, R196SpawnRules.sourceY(0, false));
        assertEquals(60, R196SpawnRules.sourceY(60, false)); assertEquals(319, R196SpawnRules.sourceY(319, false));
        assertEquals(0, R196SpawnRules.sourceY(-64, true)); assertEquals(1, R196SpawnRules.sourceY(-62, true));
        assertEquals(42, R196SpawnRules.sourceY(0, true)); assertEquals(120, R196SpawnRules.sourceY(116, true));
        assertEquals(255, R196SpawnRules.sourceY(319, true));
    }
    @Test void sourceDaytimeHasItsOwnSixThousandTickOffsetAndStrictEndpoints() {
        assertTrue(R196SpawnRules.daytime(0)); assertFalse(R196SpawnRules.daytime(13000));
        assertFalse(R196SpawnRules.daytime(23000)); assertTrue(R196SpawnRules.daytime(23001));
        assertFalse(R196SpawnRules.daytime(18000)); assertTrue(R196SpawnRules.daytime(12000));
    }
    @Test void fullNewBloodAndBlueMoonUseSourceDayPlusOne() {
        assertEquals(1, R196SpawnRules.moon(0, true).phase());
        assertTrue(R196SpawnRules.moon(7L * 24000, true).full());
        assertTrue(R196SpawnRules.moon(3L * 24000, true).fresh());
        assertTrue(R196SpawnRules.moon(31L * 24000, true).blood());
        var blue = R196SpawnRules.moon(127L * 24000, true);
        assertTrue(blue.blue()); assertFalse(blue.blood());
        assertFalse(R196SpawnRules.moon(127L * 24000, false).blue());
        assertFalse(R196SpawnRules.moon(31L * 24000, false).blood());
    }
    @Test void sourceDensityHasHeightLunarFloorsAndStrongholdGain() {
        var ordinary = R196SpawnRules.moon(0, true);
        assertEquals(24, R196SpawnRules.density(0, true, true, ordinary, 0));
        assertEquals(16, R196SpawnRules.density(32, true, true, ordinary, 0));
        assertEquals(8, R196SpawnRules.density(64, true, true, ordinary, 0));
        assertEquals(8, R196SpawnRules.density(200, true, true, ordinary, 0));
        assertEquals(10, R196SpawnRules.density(200, true, false, R196SpawnRules.moon(7L * 24000, true), 0));
        assertEquals(12, R196SpawnRules.density(200, true, false, R196SpawnRules.moon(31L * 24000, true), 0));
        assertEquals(48, R196SpawnRules.density(0, true, true, ordinary, 1));
        assertEquals(8, R196SpawnRules.density(0, false, false, ordinary, 0));
    }
    @Test void edgeChunksCountTowardsEachIndependentSourceCap() {
        assertEquals(56, R196SpawnRules.cap(R196SpawnRules.Category.MONSTER, 289, false, false));
        assertEquals(112, R196SpawnRules.cap(R196SpawnRules.Category.MONSTER, 289, true, false));
        assertEquals(11, R196SpawnRules.cap(R196SpawnRules.Category.ANIMAL, 289, false, false));
        assertEquals(22, R196SpawnRules.cap(R196SpawnRules.Category.ANIMAL, 289, false, true));
        assertEquals(5, R196SpawnRules.cap(R196SpawnRules.Category.AMBIENT, 289, false, false));
        assertEquals(5, R196SpawnRules.cap(R196SpawnRules.Category.AQUATIC, 289, false, false));
        assertEquals(0, R196SpawnRules.cap(R196SpawnRules.Category.MONSTER, 0, true, false));
    }
    @Test void sourceOutdoorSkipRatesPrioritizeBlueThenBlood() {
        assertEquals(54, R196SpawnRules.outdoorNightDenominator(R196SpawnRules.moon(127L * 24000, true)));
        assertEquals(2, R196SpawnRules.outdoorNightDenominator(R196SpawnRules.moon(31L * 24000, true)));
        assertEquals(3, R196SpawnRules.outdoorNightDenominator(R196SpawnRules.moon(7L * 24000, true)));
        assertEquals(6, R196SpawnRules.outdoorNightDenominator(R196SpawnRules.moon(3L * 24000, true)));
        assertEquals(4, R196SpawnRules.outdoorNightDenominator(R196SpawnRules.moon(0, true)));
    }
    @Test void bloodNightSurfaceExceptionsKeepTheirFreezingAndDesertRequirements() {
        assertTrue(R196SpawnRules.depthAllows("EntityGhoul", 56, true, false, false, false));
        assertFalse(R196SpawnRules.depthAllows("EntityGhoul", 57, true, false, false, false));
        assertTrue(R196SpawnRules.depthAllows("EntityGhoul", 100, true, true, false, false));
        assertFalse(R196SpawnRules.depthAllows("EntityWight", 100, true, true, false, false));
        assertTrue(R196SpawnRules.depthAllows("EntityWight", 100, true, true, true, false));
        assertFalse(R196SpawnRules.depthAllows("EntityShadow", 100, true, true, true, false));
        assertTrue(R196SpawnRules.depthAllows("EntityShadow", 100, true, true, false, true));
        assertFalse(R196SpawnRules.depthAllows("EntityPudding", 25, true, true, false, false));
        assertTrue(R196SpawnRules.depthAllows("EntityPudding", 200, false, false, false, false));
    }
    @Test void lightComparisonIsProbabilisticAndNotAUniversalZeroRule() {
        assertTrue(R196SpawnRules.lightAllows(15, 4, 15, 4));
        assertFalse(R196SpawnRules.lightAllows(15, 4, 14, 4));
        assertFalse(R196SpawnRules.lightAllows(0, 1, 0, 0));
        assertTrue(R196SpawnRules.lightAllows(0, 0, 0, 0));
    }
    @Test void distanceRulesUseSquaredWorldSpawnAndDarkEightBlockException() {
        assertTrue(R196SpawnRules.distanceAllows(576, 576, false));
        assertFalse(R196SpawnRules.distanceAllows(575, 576, false));
        assertTrue(R196SpawnRules.distanceAllows(64, 576, true));
        assertFalse(R196SpawnRules.distanceAllows(63, 576, true));
        assertFalse(R196SpawnRules.distanceAllows(576, 575, true));
    }
    @Test void infernalCreeperProbabilityHasStrictCoinFlipAndIndependentDepthRoll() {
        assertTrue(R196SpawnRules.infernalCreeper(0, 0, .499F));
        assertFalse(R196SpawnRules.infernalCreeper(0, 39, .5F));
        assertTrue(R196SpawnRules.infernalCreeper(39, 39, .1F));
        assertFalse(R196SpawnRules.infernalCreeper(40, 39, .1F));
    }
    @Test void activeRateCountersApplyBeforeReachingZero() {
        var step = R196SpawnRules.advanceRate(new R196SpawnRules.RateState(1, 1, 1), true, false, bound -> bound - 1);
        assertEquals(new R196SpawnRules.RateState(0, 0, 0), step.state()); assertEquals(0F, step.modifier());
        var night = R196SpawnRules.advanceRate(new R196SpawnRules.RateState(1, 0, 1), true, true, bound -> bound - 1);
        assertEquals(1F, night.modifier());
    }
    @Test void newlyDrawnRatePeriodStartsOnFollowingCall() {
        ArrayDeque<Integer> draws = new ArrayDeque<>(List.of(0, 3999, 23999, 23999));
        var first = R196SpawnRules.advanceRate(new R196SpawnRules.RateState(0, 0, 0), true, false, bound -> draws.removeFirst());
        assertEquals(new R196SpawnRules.RateState(4000, 0, 0), first.state()); assertEquals(1F, first.modifier());
        var next = R196SpawnRules.advanceRate(first.state(), true, false, bound -> bound - 1);
        assertEquals(3999, next.state().decreased()); assertEquals(.5F, next.modifier());
    }
    @Test void nonOverworldRateNeverTouchesSourceCountersOrRandomStream() {
        var previous = new R196SpawnRules.RateState(3, 4, 5);
        var value = R196SpawnRules.advanceRate(previous, false, true, ignored -> { fail("No random draw outside overworld"); return 0; });
        assertEquals(previous, value.state()); assertEquals(.25F, value.modifier());
    }
    @Test void frozenInitialProbabilityKeepsSourceHalfRate() {
        assertEquals(.05F, R196SpawnRules.initialProbability(true)); assertEquals(.1F, R196SpawnRules.initialProbability(false));
    }
    @Test void sourceBrightnessPathWeightKeepsNetherAndOverworldTablesSeparate() {
        assertTrue(R196SpawnRules.mobPathAllows(12, false, false));
        assertFalse(R196SpawnRules.mobPathAllows(13, false, false));
        assertTrue(R196SpawnRules.mobPathAllows(11, false, true));
        assertFalse(R196SpawnRules.mobPathAllows(12, false, true));
        for (boolean nether : List.of(false, true)) {
            assertTrue(R196SpawnRules.mobPathAllows(13, true, nether));
            assertFalse(R196SpawnRules.mobPathAllows(14, true, nether));
        }
        assertEquals(0F, R196SpawnRules.brightness(0, false));
        assertEquals(.1F, R196SpawnRules.brightness(0, true));
        assertEquals(1F, R196SpawnRules.brightness(15, false));
    }
    @Test void completeGuardedTablesHaveExplicitExceptionsAndInactiveSiege() throws Exception {
        var data = JsonParser.parseString(Files.readString(Path.of("spec/spawning-source-031.json"))).getAsJsonObject();
        assertEquals(25, data.getAsJsonArray("biomes").size()); assertEquals(60, data.getAsJsonArray("living_entities").size());
        assertEquals(221, data.getAsJsonArray("grounds").size()); assertEquals(2, data.getAsJsonArray("evidence_gaps").size());
        assertFalse(data.getAsJsonObject("village_rules").get("siege_actually_generates").getAsBoolean());
        assertFalse(data.get("world_created").getAsBoolean()); assertFalse(data.get("entity_constructed").getAsBoolean());
        assertEquals(7, data.getAsJsonArray("guard_checks_passed").size());
    }
}
