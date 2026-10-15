package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent abw/aei date and light examples, including exact dawn/dusk boundaries. @author NingMeng123 */
class LunarCalendarTest {
    private static long night(long day) { return (day-1)*24000L+18000L; }
    @Test void originalDayOneIsPhaseOneAndDayEightIsFull() {
        assertEquals(1,LunarCalendar.state(0,true).phase());assertEquals(1,LunarCalendar.state(23999,true).day());
        assertEquals(2,LunarCalendar.state(24000,true).day());assertEquals(0,LunarCalendar.state(night(8),true).phase());
        assertEquals(4,LunarCalendar.state(night(4),true).phase());
    }
    @Test void fourCalendarEventsRemainExclusiveAcrossTwoFullCycles() {
        for(long day=1;day<=256;day++) {
            var actual=LunarCalendar.state(night(day),true);
            boolean blue=day==128||day==256;
            boolean blood=day==32||day==64||day==96||day==160||day==192||day==224;
            boolean harvest=day==24||day==56||day==88||day==152||day==184||day==216;
            boolean dog=day==120||day==248;
            assertEquals(blue,actual.blueNight(),"blue day "+day);assertEquals(blood,actual.bloodNight(),"blood day "+day);
            assertEquals(harvest,actual.harvestNight(),"harvest day "+day);assertEquals(dog,actual.moonDogNight(),"halo day "+day);
        }
    }
    @Test void sourceNightHasClosed13000And23000Endpoints() {
        for(long start:new long[]{0,31L*24000,127L*24000}) {
            assertFalse(LunarCalendar.state(start+12999,true).night());assertTrue(LunarCalendar.state(start+13000,true).night());
            assertTrue(LunarCalendar.state(start+23000,true).night());assertFalse(LunarCalendar.state(start+23001,true).night());
            assertFalse(LunarCalendar.state(start+23999,true).night());assertFalse(LunarCalendar.state(start,true).night());
        }
    }
    @Test void specialDatesCoverDaytimeButTheirNightEffectsDoNot() {
        var blood=LunarCalendar.state(31L*24000+6000,true);assertTrue(blood.bloodPeriod());assertFalse(blood.bloodNight());
        var blue=LunarCalendar.state(127L*24000+12000,true);assertTrue(blue.bluePeriod());assertFalse(blue.blueNight());
        assertFalse(LunarCalendar.state(night(32),false).bloodPeriod());assertFalse(LunarCalendar.state(night(128),false).bluePeriod());
        assertFalse(LunarCalendar.state(night(24),false).harvestPeriod());assertFalse(LunarCalendar.state(night(120),false).moonDogPeriod());
    }
    @Test void visualMoonlightAndRawPhaseFractionAreDifferentResources() {
        assertEquals(1F,LunarCalendar.phaseBrightness(LunarCalendar.state(night(8),true)));
        assertEquals(1.25F,LunarCalendar.moonBrightness(LunarCalendar.state(night(8),true),0));
        assertEquals(.75F,LunarCalendar.moonBrightness(LunarCalendar.state(night(4),true),0));
        assertEquals(.6F,LunarCalendar.moonBrightness(LunarCalendar.state(night(32),true),0));
        assertEquals(1F,LunarCalendar.moonBrightness(LunarCalendar.state(night(24),true),0));
        assertEquals(1.1F,LunarCalendar.moonBrightness(LunarCalendar.state(night(128),true),0));
        assertEquals(1.25F,LunarCalendar.moonBrightness(LunarCalendar.state(night(120),true),0));
    }
    @Test void rainOnlyBlendsMoonlightAboveThreeQuartersTowardThreeQuarters() {
        assertEquals(.6F,LunarCalendar.moonBrightness(LunarCalendar.state(night(32),true),1));
        assertEquals(.75F,LunarCalendar.moonBrightness(LunarCalendar.state(night(128),true),1));
        assertEquals(.875F,LunarCalendar.moonBrightness(LunarCalendar.state(night(24),true),.5F));
    }
    @Test void maximumSupportedTimeDoesNotOverflowTheEightDayOffset() {
        var state=LunarCalendar.state(Long.MAX_VALUE,true);assertTrue(state.day()>0);assertTrue(state.phase()>=0&&state.phase()<8);
        assertEquals(1,LunarCalendar.state(-1,true).day());
    }
}
