package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Literal player-approved passive weapon values, independent of the material-melee balance curve. @author NingMeng123 */
final class PhantomBladeRulesTest {
    @Test void requestedDamageAndIntervalsAreExact() {
        String[] materials={"copper","silver","iron","mithril","adamantium","vibranium"};
        double[] damage={.5,.5,1,1.5,2,3};
        int[] ticks={60,60,50,40,30,20};
        for(int i=0;i<materials.length;i++) {
            var profile=PhantomBladeRules.profile(materials[i]);
            assertNotNull(profile); assertEquals(damage[i],profile.damage()); assertEquals(ticks[i],profile.intervalTicks());
        }
        assertEquals(PhantomBladeRules.profile("copper").rank(),PhantomBladeRules.profile("silver").rank());
        assertNull(PhantomBladeRules.profile("missing"));
        assertEquals(8,PhantomBladeRules.ORBIT_BLADES);
        assertEquals(10D,PhantomBladeRules.TARGET_RANGE);
        assertEquals(600,PhantomBladeRules.profile("copper").rechargeTicks());
        assertEquals(600,PhantomBladeRules.profile("silver").rechargeTicks());
        assertEquals(540,PhantomBladeRules.profile("iron").rechargeTicks());
        assertEquals(460,PhantomBladeRules.profile("mithril").rechargeTicks());
        assertEquals(380,PhantomBladeRules.profile("adamantium").rechargeTicks());
        assertEquals(300,PhantomBladeRules.profile("vibranium").rechargeTicks());
    }
}
