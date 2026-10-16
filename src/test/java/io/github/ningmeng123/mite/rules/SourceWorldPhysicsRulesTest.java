package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.world.SourceExplosionRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Oracle values transcribed from ss/abr/apd/acu, without calling implementation to make expectations. @author NingMeng123 */
class SourceWorldPhysicsRulesTest {
    @ParameterizedTest @CsvSource({"1,0,0,1","0,2,0,1","0,4,0,4","2,4,2,12"})
    void explosionVerticalDistanceIsHalvedBeforeSquaring(double x,double y,double z,double expected) {
        assertEquals(expected,SourceExplosionRules.flattenedDistanceSquared(x,y,z));
    }
    @ParameterizedTest @CsvSource({"2,1,2","2,16,0.25","3,16,0.375","0,16,0"})
    void itemForceUsesThreeQuarterPower(float power,double distance,float expected) {
        assertEquals(expected,SourceExplosionRules.force(power,distance));
    }
    @Test void droppedEquipmentUsesTheOriginalSeparateDurabilityScales() {
        assertEquals(1600,SourceExplosionRules.equipmentWear(2,16,100));
        assertEquals(32,SourceExplosionRules.equipmentWear(2,16,2));
        assertEquals(16,SourceExplosionRules.equipmentWear(2,16,1));
        assertEquals(Integer.MAX_VALUE,SourceExplosionRules.equipmentWear(3,0,100));
    }
    @ParameterizedTest @CsvSource({"-1,true","0,false","0.1,false","0.100001,true","1,true"})
    void preBlastOnlyClearsNonnegativeResistanceUpToOneTenth(float resistance,boolean stops) {
        assertEquals(stops,SourceExplosionRules.stopsPreBlast(resistance));
    }
    @Test void preliminaryRayBudgetIsExactlyTwoAndOneQuarterFactors() {
        assertEquals(5.25F,SourceExplosionRules.preBlastBudget(3,0),.000001F);
        assertEquals(7.5F,SourceExplosionRules.preBlastBudget(3,.5F),.000001F);
    }
    @Test void deepSpringCandidateDoesNotConsumeTheConditionalLavaDraw() {
        Stub random=new Stub(new int[]{15,119,126,0,31},.01F);
        assertEquals(new UnderworldSpringRules.Candidate(55,126,-8,true),UnderworldSpringRules.candidate(random,2,-1));
        assertEquals(List.of(16,120,127,16,32),random.bounds);
        assertEquals(0,random.floats);
    }
    @ParameterizedTest @CsvSource({"0.949999,false","0.95,true","0.999,true"})
    void shallowSpringFluidBoundaryIsNinetyFivePercentLava(float draw,boolean water) {
        Stub random=new Stub(new int[]{0,0,0,0,0},draw);
        assertEquals(water,UnderworldSpringRules.candidate(random,0,0).water());
        assertEquals(1,random.floats);
        assertEquals(List.of(16,120,8,16,32),random.bounds);
    }
    @ParameterizedTest @CsvSource({"true,false,8","true,true,8","false,false,48","false,true,16"})
    void fallingFluidDecayUsesSourceWorldDependentTimings(boolean water,boolean ultraWarm,int expected) {
        assertEquals(expected,UnderworldSpringRules.freefallDecayDelay(water,ultraWarm));
    }
    private static final class Stub extends Random {
        final int[] values;final float draw;int cursor,floats;final List<Integer> bounds=new ArrayList<>();
        Stub(int[] values,float draw) { this.values=values;this.draw=draw; }
        @Override public int nextInt(int bound) { bounds.add(bound);int value=values[cursor++];assertTrue(value>=0&&value<bound);return value; }
        @Override public float nextFloat() { floats++;return draw; }
    }
}
