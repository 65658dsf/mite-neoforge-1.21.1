package io.github.ningmeng123.mite.rules;

import java.util.List;
import org.junit.jupiter.api.Test;
import static io.github.ningmeng123.mite.rules.R196OreRules.Dimension.*;
import static io.github.ningmeng123.mite.rules.R196OreRules.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit modern additions preserve the archived R196 tables and the existing underworld pass order. @author NingMeng123 */
final class ModernUnderworldOreRulesTest {
    @Test void coalAndEmeraldAreModernAdditionsInsteadOfInventedR196Entries() {
        assertFalse(R196OreRules.UNDERWORLD.contains(COAL));
        assertFalse(R196OreRules.UNDERWORLD.contains(EMERALD));
        assertEquals(0, R196OreRules.candidateCount(COAL, UNDERWORLD));
        assertEquals(0, R196OreRules.candidateCount(EMERALD, UNDERWORLD));
        assertTrue(R196OreRules.configurableKinds(UNDERWORLD).containsAll(List.of(COAL, EMERALD)));
        assertEquals(13, R196OreRules.generationKinds(UNDERWORLD).size());
        assertEquals(R196OreRules.UNDERWORLD,
                R196OreRules.generationKinds(UNDERWORLD).subList(0, R196OreRules.UNDERWORLD.size()));
        assertEquals(List.of(COAL, EMERALD), R196OreRules.generationKinds(UNDERWORLD).subList(11, 13));
    }

    @Test void redDiamondIsConfigurableOnlyInTheUnderworldAndKeepsItsSeparateFeatureDefaults() {
        assertEquals(14, R196OreRules.configurableKinds(UNDERWORLD).size());
        assertTrue(R196OreRules.configurableKinds(UNDERWORLD).contains(RED_DIAMOND));
        assertFalse(R196OreRules.configurableKinds(OVERWORLD).contains(RED_DIAMOND));
        assertFalse(R196OreRules.configurableKinds(NETHER).contains(RED_DIAMOND));
        assertFalse(R196OreRules.generationKinds(UNDERWORLD).contains(RED_DIAMOND));
        assertFalse(R196OreRules.hasDeepslateOre(RED_DIAMOND));
        assertEquals(1, R196OreRules.configuredCandidateCount(RED_DIAMOND, UNDERWORLD));
        assertEquals(1D, R196OreRules.configuredCandidateChance(RED_DIAMOND, UNDERWORLD));
        assertEquals(new OreHeightRange(131, 274), OreHeightRange.defaults(RED_DIAMOND, UNDERWORLD));
    }
    @Test void defaultBudgetsDoNotAccidentallyApplyTheUnderworldEightfoldOrTenfoldFilter() {
        assertEquals(50, R196OreRules.configuredCandidateCount(COAL, UNDERWORLD));
        assertEquals(.1D, R196OreRules.configuredCandidateChance(COAL, UNDERWORLD));
        assertEquals(16, R196OreRules.baseSize(COAL));
        assertEquals(3, R196OreRules.configuredCandidateCount(EMERALD, UNDERWORLD));
        assertEquals(1D, R196OreRules.configuredCandidateChance(EMERALD, UNDERWORLD));
        assertEquals(1, R196OreRules.baseSize(EMERALD));
        for (var kind : List.of(COAL, EMERALD)) {
            assertTrue(R196OreRules.hasDeepslateOre(kind));
            assertEquals(0, R196OreRules.minHeight(kind, UNDERWORLD));
            assertEquals(255, R196OreRules.maxHeight(kind, UNDERWORLD));
        }
    }
}
