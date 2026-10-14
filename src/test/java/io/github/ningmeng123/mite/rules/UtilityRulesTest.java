package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Expected values are literal ItemCoin.java facts, independent of extracted configuration. @author NingMeng123 */
class UtilityRulesTest {
    static Stream<Arguments> values() {
        return Stream.of(Arguments.of("copper", 5), Arguments.of("silver", 25), Arguments.of("gold", 100),
                Arguments.of("ancient_metal", 500), Arguments.of("mithril", 2500), Arguments.of("adamantium", 10000));
    }
    @ParameterizedTest @MethodSource("values")
    void sourceCoinsRetainTheirExactExperience(String material, int xp) {
        assertEquals(xp, UtilityRules.coinExperience(material));
        assertEquals(xp * 9, UtilityRules.totalValue(xp, 9));
        assertEquals(xp * 64, UtilityRules.totalValue(xp, 64));
    }
    @Test void noSourceIronCoinIsInvented() { assertThrows(IllegalArgumentException.class, () -> UtilityRules.coinExperience("iron")); }
    @Test void invalidAmountsCannotMintNegativeExperience() {
        assertThrows(IllegalArgumentException.class, () -> UtilityRules.totalValue(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> UtilityRules.totalValue(5, -1));
        assertEquals(0, UtilityRules.totalValue(10000, 0));
    }
    @Test void oversizedExternalStacksCannotOverflowThePaymentIntoACredit() {
        assertEquals(Integer.MAX_VALUE, UtilityRules.totalValue(10000, Integer.MAX_VALUE));
    }
}
