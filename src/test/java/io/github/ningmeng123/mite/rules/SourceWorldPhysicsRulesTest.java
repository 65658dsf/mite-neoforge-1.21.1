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
    private static final class Stub extends Random {
        final int[] values;final float draw;int cursor,floats;final List<Integer> bounds=new ArrayList<>();
        Stub(int[] values,float draw) { this.values=values;this.draw=draw; }
        @Override public int nextInt(int bound) { bounds.add(bound);int value=values[cursor++];assertTrue(value>=0&&value<bound);return value; }
        @Override public float nextFloat() { floats++;return draw; }
    }
}
