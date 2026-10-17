package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ShieldProfile;
import org.junit.jupiter.api.Test;
import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Modern shield baseline and source material scaling remain independent of mining tools. @author NingMeng123 */
class ShieldDurabilityRulesTest {
    @Test void shieldBaselinesAreHalvedWhileMetalsKeepSourceRatios() {
        assertEquals(84, baseShieldDurability(new ShieldProfile(4F, Quality.EXCELLENT)));
        assertEquals(168, baseShieldDurability(new ShieldProfile(8F, Quality.MASTERWORK)));
        assertEquals(336, baseShieldDurability(new ShieldProfile(16F, Quality.MASTERWORK)));
        assertEquals(1344, baseShieldDurability(new ShieldProfile(64F, Quality.LEGENDARY)));
        assertEquals(5376, baseShieldDurability(new ShieldProfile(256F, Quality.LEGENDARY)));
    }

    @Test void qualityScalesShieldMaximumWithoutTheToolHundredfoldMultiplier() {
        int base = baseShieldDurability(new ShieldProfile(8F, Quality.MASTERWORK));
        assertEquals(84, maxDurability(base, Quality.WRETCHED));
        assertEquals(126, maxDurability(base, Quality.POOR));
        assertEquals(168, maxDurability(base, Quality.AVERAGE));
        assertEquals(504, maxDurability(base, Quality.MASTERWORK));
        assertEquals(18816, maxDurability(baseShieldDurability(new ShieldProfile(256F, Quality.LEGENDARY)), Quality.LEGENDARY));
    }

    @Test void shieldProfilesRejectInvalidMaterialValuesAndMissingQuality() {
        for (float material : new float[]{0F, -1F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new ShieldProfile(material, Quality.AVERAGE));
        assertThrows(NullPointerException.class, () -> new ShieldProfile(8F, null));
        assertEquals(1, baseShieldDurability(new ShieldProfile(Float.MIN_VALUE, Quality.AVERAGE)));
    }
}
