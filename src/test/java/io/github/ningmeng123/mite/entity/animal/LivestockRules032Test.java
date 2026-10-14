package io.github.ningmeng123.mite.entity.animal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Literal source arithmetic, kept independent from entity state and modern block mappings. @author NingMeng123 */
class LivestockRules032Test {
    @Test void exactQuarterResourcesRemainWell() { assertTrue(new LivestockRules032.Needs(.25F,.25F,.25F).well()); }
    @Test void eachResourceSeparatelyCanPreventWellness() {
        assertFalse(new LivestockRules032.Needs(.249F,1,1).well()); assertFalse(new LivestockRules032.Needs(1,.249F,1).well()); assertFalse(new LivestockRules032.Needs(1,1,.249F).well());
    }
    @Test void deprivationStepIsFiveThousandths() { var n=new LivestockRules032.Needs(.5F,.5F,.5F).update(false,false,false,true); assertEquals(.495F,n.food(),1e-6); assertEquals(.495F,n.water(),1e-6); assertEquals(.495F,n.freedom(),1e-6); }
    @Test void sourceBenefitsAreOneTenth() { var n=new LivestockRules032.Needs(.5F,.5F,.5F).update(true,true,false,false); assertEquals(.6F,n.food(),1e-6); assertEquals(.6F,n.water(),1e-6); assertEquals(.6F,n.freedom(),1e-6); }
    @Test void rainRestoresOneHundredthAndWaterWinsOverRain() {
        assertEquals(.51F,new LivestockRules032.Needs(.5F,.5F,.5F).update(false,false,true,true).water(),1e-6);
        assertEquals(.6F,new LivestockRules032.Needs(.5F,.5F,.5F).update(false,true,true,true).water(),1e-6);
    }
    @Test void allResourceUpdatesClampAtZeroAndOne() { var empty=new LivestockRules032.Needs(0,0,0).update(false,false,false,true); assertEquals(new LivestockRules032.Needs(0,0,0),empty); assertEquals(new LivestockRules032.Needs(1,1,1),new LivestockRules032.Needs(.95F,.95F,.95F).update(true,true,false,false)); }
    @Test void oneMealAddsHalfFoodOnly() { assertEquals(new LivestockRules032.Needs(.7F,.2F,.3F),new LivestockRules032.Needs(.2F,.2F,.3F).feed()); }
    @Test void vesselWaterUsesOneOrFourQuarterVolumes() { assertEquals(.45F,new LivestockRules032.Needs(.4F,.2F,.6F).drink(1).water(),1e-6); assertEquals(1F,new LivestockRules032.Needs(.4F,.2F,.6F).drink(4).water()); }
    @Test void babyInheritsEachParentalMinimum() { assertEquals(new LivestockRules032.Needs(.2F,.3F,.1F),new LivestockRules032.Needs(.2F,.8F,.1F).inherit(new LivestockRules032.Needs(.6F,.3F,.9F))); }
    @Test void hungerUrgencyHasStrictQuarterAndTwentiethBoundaries() { assertEquals(120,LivestockRules032.searchRoll(.25F)); assertEquals(40,LivestockRules032.searchRoll(.249F)); assertEquals(40,LivestockRules032.searchRoll(.05F)); assertEquals(10,LivestockRules032.searchRoll(.049F)); }
    @Test void urgencyExpandsCandidateAndSearchRanges() { assertEquals(16,LivestockRules032.searchDistance(.5F)); assertEquals(32,LivestockRules032.searchDistance(.1F)); assertEquals(48,LivestockRules032.searchDistance(.01F)); assertEquals(8,LivestockRules032.searchCandidates(.5F)); assertEquals(16,LivestockRules032.searchCandidates(.1F)); assertEquals(24,LivestockRules032.searchCandidates(.01F)); }
    @Test void chickenHasSixteenDayManurePeriodPigAndSheepHaveTwoDays() { assertEquals(384000,LivestockRules032.manurePeriod(true,false)); assertEquals(48000,LivestockRules032.manurePeriod(false,true)); assertEquals(24000,LivestockRules032.manurePeriod(false,false)); }
    @Test void recentGrassTramplingsStartAtFourthAndSaturateAtThirteenth() { assertEquals(0F,LivestockRules032.tramplingEffect(3)); assertEquals(.05F,LivestockRules032.tramplingEffect(4)); assertEquals(.2F,LivestockRules032.tramplingEffect(7)); assertEquals(.5F,LivestockRules032.tramplingEffect(13)); assertEquals(.5F,LivestockRules032.tramplingEffect(15)); }
    @Test void newbornAndBreedingCooldownUseR196Ticks() { assertEquals(-64000,LivestockRules032.NEWBORN_AGE); assertEquals(16000,LivestockRules032.BREEDING_COOLDOWN); }
}
