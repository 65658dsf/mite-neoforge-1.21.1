package io.github.ningmeng123.mite.survival;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
/** Literal source clock and fade boundaries, independent of production Minecraft state. @author NingMeng123 */
class RestRulesTest {
    @ParameterizedTest @CsvSource({"0,false","14999,false","15000,true","17999,true","18000,true","21999,true","22000,false","23000,false","39000,true"})
    void sourceSleepingWindow(long time,boolean expected){assertEquals(expected,RestRules.sleepingTime(time));}
    @ParameterizedTest @CsvSource({"15000,8000","18000,5000","21999,1001","22000,1000","23000,0","23999,23001","0,23000"})
    void sourceSunrise(long time,int expected){assertEquals(expected,RestRules.untilSunrise(time));}
    @Test void sourceSlowWaking(){
        assertEquals(100,RestRules.consciousness(99,true));
        assertEquals(110,RestRules.consciousness(110,true));
        int counter=110;
        for(int i=0;i<109;i++)counter=RestRules.consciousness(counter,false);
        assertEquals(1,counter);assertEquals(0,RestRules.consciousness(counter,false));
    }
}
