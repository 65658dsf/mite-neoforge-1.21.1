package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** R196 source coefficients and branch priority, independent of modern movement statistics. @author NingMeng123 */
class MovementHungerRulesTest {
    @Test void walkingCrouchingAndSprintingSpendTheSourceCostPerRoundedMeter() {
        assertEquals(.01F, effort(1, 0, 0, false, false, false, true, false, false));
        assertEquals(.05F, effort(1, 0, 0, false, false, false, true, false, true),1E-7F);
        assertEquals(0F, effort(.0049, 0, 0, false, false, false, true, false, false));
        assertEquals(.0001F, effort(.0051, 0, 0, false, false, false, true, false, false));
        assertEquals(0F, effort(1, 0, 0, false, false, false, true, true, true));
        assertEquals(0F, effort(1, 0, 0, false, false, false, false, false, true));
    }
    @Test void underwaterUsesThreeDimensionsAndSurfaceWaterUsesOnlyHorizontalDistance() {
        assertEquals(.075F, effort(3, 4, 0, true, true, true, true, false, true),1E-7F);
        assertEquals(.045F, effort(3, 4, 0, false, true, true, true, false, true),1E-7F);
        assertEquals(.015F, effort(0, 1, 0, true, true, false, false, false, false));
        assertEquals(0F, effort(0, 1, 0, false, true, false, false, false, false));
    }
    @Test void climbingUsesActualPositiveVerticalDistanceAndDoesNotRoundToCentimeters() {
        assertEquals(.0025F, effort(10, .025, 0, false, false, true, true, false, true));
        assertEquals(0F, effort(0, -.5, 0, false, false, true, false, false, false));
        assertEquals(0F, effort(1, 0, 0, false, false, true, true, false, true));
    }
    @Test void rowingChargesForwardInputNotBoatDistanceOrOtherPassengerMovement() {
        assertEquals(.01F, MovementHungerRules.rowing(1F));
        assertEquals(.01F, MovementHungerRules.rowing(-1F));
        assertEquals(.0025F, MovementHungerRules.rowing(.25F));
        assertEquals(0F, MovementHungerRules.rowing(0F));
        assertEquals(0F, MovementHungerRules.rowing(Float.NaN));
    }
    private static float effort(double x,double y,double z,boolean eye,boolean wet,boolean climb,boolean ground,boolean bed,boolean sprint) {
        return MovementHungerRules.exhaustion(x,y,z,eye,wet,climb,ground,bed,sprint);
    }
}
