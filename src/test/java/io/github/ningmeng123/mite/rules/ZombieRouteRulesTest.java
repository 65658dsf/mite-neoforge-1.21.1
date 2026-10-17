package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent pursuit timing examples preserve staged mining and favour faster travel. @author NingMeng123 */
class ZombieRouteRulesTest {
    @Test void walkingUsesActualDistanceAndMovementSpeed() {
        assertEquals(80D, ZombieRouteRules.walkingTicks(20D, .25D, 0D, 0D, 0D));
        assertEquals(160D, ZombieRouteRules.walkingTicks(20D, .125D, 0D, 0D, 0D));
    }
    @Test void WaterAndClimbingCostMoreThanDryLevelGround() {
        assertTrue(ZombieRouteRules.walkingTicks(20D, .25D, 2D, 8D, 0D) > 80D);
        assertTrue(ZombieRouteRules.walkingTicks(20D, .25D, 0D, 0D, 8D) > 80D);
    }
    @Test void freshDirtRetainsFortyTickOpeningAndTenFurtherStages() {
        assertEquals(1540L, ZombieRouteRules.remainingBreakTicks(40, -1, 150));
        assertEquals(751L, ZombieRouteRules.remainingBreakTicks(1, 4, 150));
        assertEquals(1L, ZombieRouteRules.remainingBreakTicks(1, 9, 150));
    }
    @Test void zeroHardnessStillNeedsAllTickActions() {
        assertEquals(50L, ZombieRouteRules.remainingBreakTicks(40, -1, 0));
    }
    @Test void shortDetourBeatsSlowExcavationButAQuickOpeningWinsLongDetour() {
        assertFalse(ZombieRouteRules.preferBreaking(100D, 1540D + 20D));
        assertTrue(ZombieRouteRules.preferBreaking(400D, 20D + 150D));
        assertTrue(ZombieRouteRules.preferBreaking(Double.POSITIVE_INFINITY, 1540D));
    }
    @Test void tiesAndInvalidEstimatesNeverJustifyDestruction() {
        assertFalse(ZombieRouteRules.preferBreaking(100D, 100D));
        assertFalse(ZombieRouteRules.preferBreaking(100D, 99D));
        assertFalse(ZombieRouteRules.preferBreaking(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertFalse(ZombieRouteRules.preferBreaking(100D, Double.NaN));
        assertEquals(Double.POSITIVE_INFINITY, ZombieRouteRules.walkingTicks(20D, 0D, 0D, 0D, 0D));
    }
}
