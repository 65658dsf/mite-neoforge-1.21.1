package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ShieldProfile;
import org.junit.jupiter.api.Test;
import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Modern shield baseline and source material scaling remain independent of mining tools. @author NingMeng123 */
class ShieldDurabilityRulesTest {
    @Test void shieldBaselinesScaleFrom336DurabilityForIron() {
        assertEquals(168, baseShieldDurability(new ShieldProfile(4F, Quality.EXCELLENT)));
        assertEquals(336, baseShieldDurability(new ShieldProfile(8F, Quality.MASTERWORK)));
        assertEquals(672, baseShieldDurability(new ShieldProfile(16F, Quality.MASTERWORK)));
        assertEquals(2688, baseShieldDurability(new ShieldProfile(64F, Quality.LEGENDARY)));
        assertEquals(10752, baseShieldDurability(new ShieldProfile(256F, Quality.LEGENDARY)));
    }

    @Test void qualityScalesShieldMaximumWithoutTheToolHundredfoldMultiplier() {
        int base = baseShieldDurability(new ShieldProfile(8F, Quality.MASTERWORK));
        assertEquals(168, maxShieldDurability(base, Quality.WRETCHED));
        assertEquals(252, maxShieldDurability(base, Quality.POOR));
        assertEquals(336, maxShieldDurability(base, Quality.AVERAGE));
        assertEquals(378, maxShieldDurability(base, Quality.FINE));
        assertEquals(504, maxShieldDurability(base, Quality.EXCELLENT));
        assertEquals(630, maxShieldDurability(base, Quality.SUPERB));
        assertEquals(756, maxShieldDurability(base, Quality.MASTERWORK));
        assertEquals(882, maxShieldDurability(base, Quality.LEGENDARY));
        assertEquals(1008, maxDurability(base, Quality.MASTERWORK));
    }

    @Test void shieldProfilesRejectInvalidMaterialValuesAndMissingQuality() {
        for (float material : new float[]{0F, -1F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new ShieldProfile(material, Quality.AVERAGE));
        assertThrows(NullPointerException.class, () -> new ShieldProfile(8F, null));
        assertEquals(1, baseShieldDurability(new ShieldProfile(Float.MIN_VALUE, Quality.AVERAGE)));
    }
}
