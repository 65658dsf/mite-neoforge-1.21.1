package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Literal vectors transcribed from uf/og, independent of game-side hooks. @author NingMeng123 */
class SourcePlayerCombatRulesTest {
    @ParameterizedTest
    @CsvSource({"-100,-1", "-50,0", "-25,.5", "-1,.98", "0,1", "1,1.005", "100,1.5", "200,2", "201,2.005", "1000,6"})
    void levelDamageHasDifferentPositiveAndNegativeSlopesAndNo200Cap(int level, float factor) {
        assertEquals(factor, SourcePlayerCombatRules.meleeLevelFactor(level), .000001F);
    }
    @Test void sourceOrderDoesNotMultiplyEnchantmentsByLevelCriticalOrMaterials() {
        // 10*1.5 critical, *1.5 level100, *1.25 silver, *2 blunt, +4 enchant =60.25; liquid ->30.625.
        assertEquals(60.25F, SourcePlayerCombatRules.primaryDamage(15F,100,true,true,4F,false));
        assertEquals(30.625F, SourcePlayerCombatRules.primaryDamage(15F,100,true,true,4F,true));
        assertEquals(8.5F, SourcePlayerCombatRules.primaryDamage(15F,-10,false,false,4F,true));
        assertEquals(4F, SourcePlayerCombatRules.primaryDamage(15F,-50,true,true,4F,false));
        assertEquals(.5F, SourcePlayerCombatRules.suspendedDamage(.5F,true));
        assertEquals(1F, SourcePlayerCombatRules.suspendedDamage(1F,true));
    }
    @Test void onlyUnprotectedHandsCanBecomeWeakAndThresholdsAreStrict() {
        assertFalse(SourcePlayerCombatRules.weakStrike(false,2F,true,1D));
        assertTrue(SourcePlayerCombatRules.weakStrike(false,1.999F,true,1D));
        assertTrue(SourcePlayerCombatRules.weakStrike(false,2F,false,1D));
        assertTrue(SourcePlayerCombatRules.weakStrike(false,2F,true,.999D));
        assertFalse(SourcePlayerCombatRules.weakStrike(true,1F,false,.1D));
    }
    @ParameterizedTest
    @CsvSource({"-20,-1", "-2.5,-1", "-1.5,-.5", "-.5,0", "0,0", ".5,0", "1.5,.5", "2.5,1", "20,1"})
    void heightDeadZoneSlopeAndCapsMatchSource(double difference, double expected) {
        assertEquals(expected, SourcePlayerCombatRules.heightAdvantage(difference));
    }
    @Test void distinctReachContextsAndTargetOverrides() {
        assertEquals(2.75D, SourcePlayerCombatRules.blockReach(false,0D));
        assertEquals(3.5D, SourcePlayerCombatRules.blockReach(false,.75D));
        assertEquals(1.5D, SourcePlayerCombatRules.meleeReach(false,0,0,false));
        assertEquals(2.25D, SourcePlayerCombatRules.meleeReach(false,0,.75,false));
        assertEquals(4.25D, SourcePlayerCombatRules.meleeReach(false,0,.75,true));
        assertEquals(3.5D, SourcePlayerCombatRules.interactionReach(false,2.5,false));
        assertEquals(2.5D, SourcePlayerCombatRules.interactionReach(false,2.5,true));
        assertEquals(5D, SourcePlayerCombatRules.meleeReach(true,20,100,true));
        assertEquals(5D, SourcePlayerCombatRules.blockReach(true,100D));
        assertEquals(5D, SourcePlayerCombatRules.interactionReach(true,-20,true));
    }
    @ParameterizedTest
    @CsvSource({"false,false,false,false,0", "false,false,true,false,.125", "false,false,true,true,0",
            "true,false,false,false,1", "true,false,true,false,.125", "true,false,true,true,1",
            "false,true,false,true,1", "false,true,true,false,1", "true,true,true,false,1"})
    void targetHandInjuryRollIncludesTheSourceCubeOverwrite(boolean cube, boolean fire, boolean targets, boolean weak, float chance) {
        assertEquals(chance, SourcePlayerCombatRules.handInjuryChance(cube,fire,targets,weak));
    }
}
