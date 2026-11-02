package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Literal source boundaries are independent of the modern world implementation. @author NingMeng123 */
final class CropGrowthRulesTest {
    @Test void legacyFruitAttemptCalibrationRetainsItsReferenceWhileFoodUsesThePersistentClock() {
        for(float rate:new float[]{2F,2.5F}) {
            // Seven stem stages + 1/(1/2 fruit gate * 2/4 open directions) = eleven.
            int threshold=CropGrowthRules.growthThreshold(rate,rate,11);
            assertEquals(60D,11D*4096D/(3D*20D*60D*(threshold/1_000_000D)*(12081D/24000D)),.001D);
        }
    }
    @Test void legacyFruitRollPreservesEnvironmentAndCannotAcceptMoreThanOneAttemptPerRandomTick() {
        int normal=CropGrowthRules.growthThreshold(1.25F,1.25F,7);
        assertEquals(normal*.5,CropGrowthRules.growthThreshold(.625F,1.25F,7),1D);
        assertEquals(normal*1.5,CropGrowthRules.growthThreshold(1.875F,1.25F,7),1D);
        assertEquals(0,CropGrowthRules.growthThreshold(0F,1.25F,7));
        assertEquals(0,CropGrowthRules.growthThreshold(-1F,1.25F,7));
        assertEquals(1_000_000,CropGrowthRules.growthThreshold(Float.MAX_VALUE,1.25F,7));
    }
    @Test void diseaseTemperatureHelperRetainsItsLinearTolerance() {
        assertEquals(1F,CropGrowthRules.temperatureModifier(.8F,.8F,1.2F));
        assertEquals(1F,CropGrowthRules.temperatureModifier(1F,.8F,1.2F));
        assertEquals(1F,CropGrowthRules.temperatureModifier(1.2F,.8F,1.2F));
        assertEquals(.2F,CropGrowthRules.temperatureModifier(0F,.8F,1.2F),.000001F);
        assertEquals(.2F,CropGrowthRules.temperatureModifier(2F,.8F,1.2F),.000001F);
        assertEquals(0F,CropGrowthRules.temperatureModifier(-.3F,.8F,1.2F));
        assertEquals(0F,CropGrowthRules.temperatureModifier(2.3F,.8F,1.2F));
    }
    @Test void potatoBlightDoublesTheOrdinaryChanceAndHumidityMultipliesByOnePointFive() {
        assertEquals(.00003125F,CropGrowthRules.blightChance(1F,false,15,false),.000000001F);
        assertEquals(.0000625F,CropGrowthRules.blightChance(1F,false,15,true),.000000001F);
        assertEquals(.000046875F,CropGrowthRules.blightChance(1F,true,15,false),.000000001F);
        assertEquals(.0005F,CropGrowthRules.blightChance(1.2F,false,0,false),.000000001F);
        assertEquals(.000125F,CropGrowthRules.blightChance(.5F,false,8,false),.000000001F);
        assertEquals(0F,CropGrowthRules.blightChance(0F,true,0,true));
        assertEquals(0F,CropGrowthRules.blightChance(2.3F,true,0,true));
    }
    @Test void growthRollUsesTruncationBeforeAddingOne() {
        assertEquals(51,CropGrowthRules.growthDenominator(.5F));
        assertEquals(26,CropGrowthRules.growthDenominator(1F));
        assertEquals(21,CropGrowthRules.growthDenominator(1.25F));
        assertEquals(14,CropGrowthRules.growthDenominator(1.875F));
        assertEquals(11,CropGrowthRules.growthDenominator(2.5F));
    }
    @Test void agriculturalEnchantmentsKeepTheSourceFiveLevelFractions() {
        float[] harvest={0F,.1F,.2F,.3F,.4F,.5F};
        float[] fertility={0F,.2F,.4F,.6F,.8F,1F};
        for(int level=0;level<=5;level++) {
            assertEquals(harvest[level],CropGrowthRules.harvestingBonus(level),.000001F);
            assertEquals(fertility[level],CropGrowthRules.fertilityChance(level),.000001F);
        }
        assertEquals(0F,CropGrowthRules.harvestingBonus(-1));
        assertEquals(.5F,CropGrowthRules.harvestingBonus(200));
        assertEquals(0F,CropGrowthRules.fertilityChance(-1));
        assertEquals(1F,CropGrowthRules.fertilityChance(200));
    }
}
