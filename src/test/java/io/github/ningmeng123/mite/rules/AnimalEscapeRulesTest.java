package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static io.github.ningmeng123.mite.rules.AnimalEscapeRules.score;
import static org.junit.jupiter.api.Assertions.*;

/** Route comparisons cover detours, early danger, slow terrain and small escape steps. @author NingMeng123 */
class AnimalEscapeRulesTest {
    @Test void efficientRouteBeatsFartherEndpointWithLongDetour() {
        assertTrue(score(16, 16, 4, 20, 6, 6, 4) > score(40, 40, 4, 28, 6, 1, 4));
    }

    @Test void earlySeparationWinsWhenTotalEfficiencyIsEqual() {
        assertTrue(score(16, 16, 4, 16, 6, 5, 4) > score(16, 16, 4, 16, 6, 1, 4));
    }

    @Test void slowTerrainLosesToEquallyDirectDryRoute() {
        assertTrue(score(16, 16, 4, 20, 6, 6, 4) > score(16, 32, 4, 20, 6, 6, 4));
    }

    @Test void chargingTowardAttackerIsRejectedEvenForFartherEndpoint() {
        assertEquals(Double.NEGATIVE_INFINITY, score(24, 24, 4, 24, 6, 1, 2));
    }

    @Test void smallSafeStepCanEscapeCornerWithoutSixteenBlockMinimum() {
        assertTrue(Double.isFinite(score(2, 2, 4, 5, 2, 1, 4)));
    }

    @Test void smallApproachAroundBlockIsPermittedButPenalized() {
        assertTrue(Double.isFinite(score(8, 8, 8, 14, 6, 4, 7.5)));
        assertTrue(score(8, 8, 8, 14, 6, 4, 8) > score(8, 8, 8, 14, 6, 4, 7.5));
    }

    @Test void noProgressAndInvalidMetricsAreRejected() {
        assertEquals(Double.NEGATIVE_INFINITY, score(8, 8, 4, 4, 6, 1, 4));
        assertEquals(Double.NEGATIVE_INFINITY, score(8, Double.NaN, 4, 12, 6, 6, 4));
        assertEquals(Double.NEGATIVE_INFINITY, score(8, 7, 4, 12, 6, 6, 4));
    }
}
