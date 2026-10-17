package io.github.ningmeng123.mite.rules;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source constants and independent edge vectors, without loading Minecraft. @author NingMeng123 */
class SourceFireballRulesTest {
    @Test void sourceBlazeBurstIsTwentySixSixSixTwentyTwenty() {
        var cycle = new SourceFireballRules.BlazeCycle();
        List<Integer> shots = new ArrayList<>();
        for (int tick = 0; tick <= 78; tick++) if (cycle.advance(tick)) shots.add(tick);
        assertEquals(List.of(20, 26, 32, 78), shots);
    }
    @Test void blazeSharesMeleeCooldownAndElapsedIdleTicksDoNotFreezeIt() {
        var cycle = new SourceFireballRules.BlazeCycle();
        cycle.melee(10);
        assertFalse(cycle.ready(29)); assertTrue(cycle.ready(30));
        assertFalse(cycle.advance(100)); assertTrue(cycle.charged());
        assertFalse(cycle.advance(119)); assertTrue(cycle.advance(120));
    }
    @Test void blazeLeadUsesCentreDistanceAndClampsOnlyItsUpperRandomFactor() {
        assertEquals(1F, SourceFireballRules.blazeLead(1D, 0F));
        assertEquals(2F, SourceFireballRules.blazeLead(1D, .5F));
        assertEquals(2F, SourceFireballRules.blazeLead(1D, .99F));
        assertEquals(24.768243F, SourceFireballRules.blazeLead(400D, .5F), 1E-5F);
        assertEquals(1F, SourceFireballRules.blazeSpread(16F));
    }
    @Test void blazeHasHorizontalDistanceSpreadAndIndependentThreeAxisConstructorNoise() {
        var aim = SourceFireballRules.blazeDirection(10, 2, -4, 16F, 1, -1, -2, 3, 2);
        double length = Math.sqrt(10.2 * 10.2 + 3.2 * 3.2 + -4.2 * -4.2);
        assertEquals(10.2 / length, aim.x(), 1E-12);
        assertEquals(3.2 / length, aim.y(), 1E-12);
        assertEquals(-4.2 / length, aim.z(), 1E-12);
    }
    @Test void ghastRandomLeadCanExceedOneAndNeverPredictsVerticalVelocity() {
        assertEquals(.5F, SourceFireballRules.ghastLead(1D, 0F));
        assertEquals(1.49F, SourceFireballRules.ghastLead(1D, .99F));
        assertEquals(13.960674F, SourceFireballRules.ghastLead(400D, .5F), 1E-5F);
    }
    @Test void ghastUsesMultiplicativeWanderAndPreservesZeroAxes() {
        var aim = SourceFireballRules.ghastDirection(10, 0, -10, 1, 0, 1, 0, 0, 1);
        double length = Math.sqrt(11F * 11F + 9F * 9F);
        assertEquals(11D / length, aim.x(), 1E-7);
        assertEquals(0, aim.y()); assertEquals(-9D / length, aim.z(), 1E-7);
    }
    @Test void ghastFirstShotAtTwentyThenSixtyTickPeriod() {
        var cycle = new SourceFireballRules.GhastCycle();
        List<Integer> shots = new ArrayList<>();
        for (int tick = 1; tick <= 140; tick++) if (cycle.advance(true)) shots.add(tick);
        assertEquals(List.of(20, 80, 140), shots);
    }
    @Test void ghastObstructionDecaysPositiveChargeAndPausesNegativeRecovery() {
        var cycle = new SourceFireballRules.GhastCycle();
        for (int tick = 0; tick < 12; tick++) cycle.advance(true);
        assertTrue(cycle.charging());
        cycle.advance(false); assertEquals(11, cycle.charge());
        cycle.advance(false); assertEquals(10, cycle.charge()); assertFalse(cycle.charging());
        for (int tick = 0; tick < 10; tick++) cycle.advance(true);
        assertEquals(-40, cycle.charge());
        for (int tick = 0; tick < 100; tick++) cycle.advance(false);
        assertEquals(-40, cycle.charge());
    }
    @Test void sourceRangesAreDistinctFromModernBlazeFollowRange() {
        assertEquals(30D, SourceFireballRules.BLAZE_RANGE);
        assertEquals(64D, SourceFireballRules.GHAST_RANGE);
        assertEquals(100D, SourceFireballRules.GHAST_TARGET_RANGE);
    }
}
