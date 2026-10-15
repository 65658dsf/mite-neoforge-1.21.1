package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent native-biome examples and exact source thresholds. @author NingMeng123 */
class CropSoilRulesTest {
    @Test void freezingAndColdLandCannotEvaporateSoil() {
        for (float temperature : new float[]{-.5F, 0F, .15F}) {
            assertEquals(0F, CropSoilRules.dryingChance(temperature, false));
            assertEquals(0F, CropSoilRules.dryingChance(temperature, true));
        }
    }
    @Test void highHumiditySubtractsOneHalfRatherThanDisablingDrying() {
        assertEquals(.65F, CropSoilRules.dryingChance(.8F, false), .00001F);
        assertEquals(.15F, CropSoilRules.dryingChance(.8F, true), .00001F);
        assertEquals(0F, CropSoilRules.dryingChance(.65F, true), .00001F);
        assertEquals(.05F, CropSoilRules.dryingChance(.7F, true), .00001F);
    }
    @Test void hotDesertThresholdMayExceedOneAndThusDriesEveryTick() {
        assertEquals(1.85F, CropSoilRules.dryingChance(2F, false), .00001F);
        assertEquals(1.35F, CropSoilRules.dryingChance(2F, true), .00001F);
    }
}
