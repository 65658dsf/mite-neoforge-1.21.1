package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent source numeric boundaries rather than Minecraft's modern temperature values. @author NingMeng123 */
class SourceIceMeltingRulesTest {
    @Test void legacyJungleDesertAndDimensionsUseThePublishedTemperatures() {
        assertEquals(.5F, SourceIceMeltingRules.temperature(0));
        assertEquals(1.6F, SourceIceMeltingRules.temperature(2));
        assertEquals(1.2F, SourceIceMeltingRules.temperature(21));
        assertEquals(1.4F, SourceIceMeltingRules.temperature(23));
        assertEquals(1F, SourceIceMeltingRules.temperature(26));
        assertEquals(2F, SourceIceMeltingRules.temperature(8));
        assertEquals(0F, SourceIceMeltingRules.chance(1F));
        assertEquals(.00006F, SourceIceMeltingRules.chance(1.6F), 1E-10F);
        assertEquals(.00002F, SourceIceMeltingRules.chance(1.2F), 1E-10F);
        assertEquals(.0019F, SourceIceMeltingRules.chance(20F), 1E-9F);
        assertEquals(0F, SourceIceMeltingRules.chance(Float.NaN));
    }

    @Test void everyOriginalItemDrawsOnceEvenWhenEarlierItemsMelt() {
        double[] draws = {.1D, .7D, .2D, .5D};
        int[] cursor = {0};
        assertEquals(2, SourceIceMeltingRules.remaining(4, .5F, () -> draws[cursor[0]++]));
        assertEquals(4, cursor[0]);
        assertEquals(4, SourceIceMeltingRules.remaining(4, 0F, () -> { fail("A cold stack does not draw RNG"); return 0; }));
    }
}
