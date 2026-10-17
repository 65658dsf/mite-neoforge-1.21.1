package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Prediction improves a moving shot while retaining a deliberately imperfect estimate. @author NingMeng123 */
class SkeletonAimRulesTest {
    private static double displacement(double ticks) { return (1 - Math.pow(.99, ticks)) / .01; }
    private static double hitX(SkeletonAimRules.Aim aim) {
        double length = Math.sqrt(aim.x() * aim.x() + aim.y() * aim.y() + aim.z() * aim.z());
        return aim.x() / length * 1.6 * displacement(aim.flightTicks());
    }
    private static double hitZ(SkeletonAimRules.Aim aim) {
        double length = Math.sqrt(aim.x() * aim.x() + aim.y() * aim.y() + aim.z() * aim.z());
        return aim.z() / length * 1.6 * displacement(aim.flightTicks());
    }

    @Test void stationaryShotCompensatesGravityAndDrag() {
        var aim = SkeletonAimRules.aim(20, -.8, 0, 0, 0, 1.6);
        double length = Math.sqrt(aim.x() * aim.x() + aim.y() * aim.y());
        double y = aim.y() / length * 1.6 * displacement(aim.flightTicks())
                - .05 / .01 * (aim.flightTicks() - displacement(aim.flightTicks()));
        assertEquals(20, hitX(aim), .2);
        assertEquals(-.8, y, .1);
    }

    @Test void lateralMovementIsLedButNeverPerfectlyPredicted() {
        var predicted = SkeletonAimRules.aim(20, -.8, 0, 0, .25, 1.6);
        var fixed = SkeletonAimRules.aim(20, -.8, 0, 0, 0, 1.6);
        double trueZ = .25 * predicted.flightTicks();
        assertTrue(hitZ(predicted) > .5);
        assertTrue(Math.abs(trueZ - hitZ(predicted)) < Math.abs(trueZ - hitZ(fixed)));
        assertTrue(Math.abs(trueZ - hitZ(predicted)) > .2, "Partial lead still leaves room to dodge");
    }

    @Test void backwardAndDiagonalMovementUseTheirActualDirection() {
        var aim = SkeletonAimRules.aim(20, 0, 0, -.2, -.2, 1.6);
        assertTrue(hitX(aim) < 20);
        assertTrue(hitZ(aim) < 0);
    }

    @Test void ExcessiveVelocityHasABoundedLead() {
        var aim = SkeletonAimRules.aim(30, 0, 0, 0, 500, 1.6);
        assertTrue(Math.abs(aim.z() * displacement(aim.flightTicks())) <= SkeletonAimRules.MAX_LEAD + 1e-8);
    }

    @Test void everyDifficultyRetainsNonzeroRandomSpread() {
        assertEquals(5F, SkeletonAimRules.inaccuracy(1));
        assertEquals(4F, SkeletonAimRules.inaccuracy(2));
        assertEquals(3F, SkeletonAimRules.inaccuracy(3));
    }

    @Test void closeAndElevatedTargetsProduceFiniteDirections() {
        for (double[] target : new double[][]{{.01, 0, .01}, {8, 12, -4}, {8, -12, 4}}) {
            var aim = SkeletonAimRules.aim(target[0], target[1], target[2], 0, 0, 1.6);
            assertTrue(Double.isFinite(aim.x() + aim.y() + aim.z()));
            assertTrue(aim.flightTicks() >= 1 && aim.flightTicks() <= 40);
        }
        assertThrows(IllegalArgumentException.class, () -> SkeletonAimRules.aim(0, 0, 0, 0, 0, 0));
    }

    @Test void elevatedAndLowerTargetsAreReachedByNativeDiscretePhysics() {
        for (double[] target : new double[][]{{8, 12}, {16, 8}, {8, -12}, {1, 10}, {20, 6}}) {
            var aim = SkeletonAimRules.aim(target[0], target[1], 0, 0, 0, 1.6);
            double length = Math.hypot(aim.x(), aim.y());
            double vx = aim.x() / length * 1.6, vy = aim.y() / length * 1.6;
            double x = 0D, y = 0D;
            boolean reached = false;
            // AbstractArrow moves first, then applies air resistance and gravity each tick.
            for (int tick = 0; tick < 80; tick++) {
                if (x + vx >= target[0]) {
                    y += vy * (target[0] - x) / vx;
                    reached = true;
                    break;
                }
                x += vx; y += vy;
                vx *= .99F;
                vy = vy * .99F - .05D;
            }
            assertTrue(reached, "The native trajectory reaches the target's horizontal position");
            assertEquals(target[1], y, .1D, "The release direction reaches elevated and lower body points");
        }
    }
}
