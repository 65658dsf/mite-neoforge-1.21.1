package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Independent wr.java metal-specific lava melting boundaries. @author NingMeng123 */
class LavaBucketRulesTest {
    @ParameterizedTest
    @CsvSource({"copper,0.16", "silver,0.16", "gold,0.2", "iron,0.08", "ancient_metal,0.04", "mithril,0.01", "adamantium,0"})
    void sourceSevenBucketMetalsKeepTheirExactSingleFillProbabilities(String metal, float chance) {
        assertEquals(chance, LavaBucketRules.meltingChance(metal));
    }
    @Test void unrelatedVesselsDoNotInventAnOriginalSourceMeltingProbability() {
        assertEquals(0F, LavaBucketRules.meltingChance("external_metal"));
    }
}
