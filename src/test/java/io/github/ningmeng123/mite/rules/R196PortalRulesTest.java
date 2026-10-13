package io.github.ningmeng123.mite.rules;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Independent aps.java/acj.java boundary examples, including modern minY adaptation. @author NingMeng123 */
class R196PortalRulesTest {
    @ParameterizedTest
    @CsvSource({"0,false,0", "0,true,1", "1,false,0", "1,true,2", "2,false,1", "2,true,1"})
    void routesSourceDimensions(int source, boolean bottom, int expected) {
        assertEquals(expected, R196PortalRules.ordinaryDestination(source, bottom));
    }

    @ParameterizedTest
    @CsvSource({"-64,-64,false", "-63,-64,true", "-56,-64,true", "-55,-64,false", "0,0,false", "1,0,true", "8,0,true", "9,0,false"})
    void acceptsOnlyTheEightBlockFloorBand(int frame, int minimum, boolean expected) {
        assertEquals(expected, R196PortalRules.canTouchBottom(frame, minimum));
    }

    @ParameterizedTest
    @CsvSource({"0,1,1", "1,0,1", "1,2,0.125", "2,1,8", "0,2,0.125", "2,0,8", "0,0,1"})
    void scalesOnlyTripsInvolvingTheNether(int from, int to, double expected) {
        assertEquals(expected, R196PortalRules.coordinateScale(from, to));
    }

    @Test void cornersUseLowerLeftLowerRightUpperLeftUpperRightNibbleOrder() {
        assertEquals(0x4321, R196PortalRules.runeSeed(1, 2, 3, 4));
        assertEquals(65535, R196PortalRules.runeSeed(15, 15, 15, 15));
        assertEquals(0, R196PortalRules.runeSeed(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> R196PortalRules.runeSeed(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> R196PortalRules.runeSeed(0, 0, 16, 0));
    }

    @Test void sourceDomainIsHalfWorldRadiusCappedAtFortyThousandThenDividedForMithril() {
        assertEquals(5000, R196PortalRules.runeRadius(false, 30_000_000));
        assertEquals(40000, R196PortalRules.runeRadius(true, 30_000_000));
        assertEquals(625, R196PortalRules.runeRadius(false, 10000));
        assertEquals(5000, R196PortalRules.runeRadius(true, 10000));
    }

    @Test void zeroRuneGoesToOriginWithoutOceanQueries() {
        var result = R196PortalRules.runeCoordinates(0, true, 40000, (x, z) -> fail("Zero seed must not query oceans"));
        assertEquals(new R196PortalRules.Coordinates(0, 0), result);
    }

    @Test void eachSeedIsStableAndAdamantiumExcludesItsInnerHalf() {
        for (int seed : new int[]{1, 0x4321, 0xffff}) {
            var first = R196PortalRules.runeCoordinates(seed, true, 40000, (x, z) -> false);
            assertEquals(first, R196PortalRules.runeCoordinates(seed, true, 40000, (x, z) -> false));
            assertTrue(Math.abs(first.x()) <= 40000 && Math.abs(first.z()) <= 40000);
            assertTrue(Math.hypot(first.x(), first.z()) >= 20000);
        }
    }

    @Test void oceanRetryStopsAtFourAndStopsOnTheFirstDryCandidate() {
        AtomicInteger calls = new AtomicInteger();
        R196PortalRules.runeCoordinates(1, false, 5000, (x, z) -> { calls.incrementAndGet(); return true; });
        assertEquals(4, calls.get());
        calls.set(0);
        R196PortalRules.runeCoordinates(1, false, 5000, (x, z) -> calls.incrementAndGet() < 2);
        assertEquals(2, calls.get());
    }

    @Test void seedOneMatchesIndependentlyCalculatedJavaLcgCoordinates() {
        // Independent 48-bit LCG audit of R196 java.util.Random, not an implementation-derived expectation.
        assertEquals(new R196PortalRules.Coordinates(3985, -412),
                R196PortalRules.runeCoordinates(1, false, 5000, (x, z) -> false));
        assertEquals(new R196PortalRules.Coordinates(-11015, -35412),
                R196PortalRules.runeCoordinates(1, true, 40000, (x, z) -> false));
        assertEquals(new R196PortalRules.Coordinates(-566, 1606),
                R196PortalRules.runeCoordinates(1, false, 5000, (x, z) -> true));
    }
}
