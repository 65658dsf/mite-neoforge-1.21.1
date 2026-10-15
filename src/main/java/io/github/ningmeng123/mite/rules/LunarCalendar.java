package io.github.ningmeng123.mite.rules;

/** R196 abw/aei calendar; world day starts at unadjusted tick zero. @author NingMeng123 */
public final class LunarCalendar {
    public enum Event { ORDINARY, HARVEST, BLOOD, BLUE, MOON_DOG }
    private static final float[] PHASE_BRIGHTNESS={1F,.75F,.5F,.25F,0F,.25F,.5F,.75F};
    public record State(long day,int phase,boolean overworld,boolean night,Event event) {
        public boolean bloodPeriod() { return event==Event.BLOOD; }
        public boolean bluePeriod() { return event==Event.BLUE; }
        public boolean harvestPeriod() { return event==Event.HARVEST; }
        public boolean moonDogPeriod() { return event==Event.MOON_DOG; }
        public boolean bloodNight() { return bloodPeriod()&&night; }
        public boolean blueNight() { return bluePeriod()&&night; }
        public boolean harvestNight() { return harvestPeriod()&&night; }
        public boolean moonDogNight() { return moonDogPeriod()&&night; }
    }
    private LunarCalendar() {}
    public static boolean daytime(int timeOfDay) {
        int adjusted=Math.floorMod(Math.floorMod(timeOfDay,24000)+6000,24000);
        return adjusted>5000&&adjusted<19000;
    }
    public static State state(long dayTime,boolean overworld) {
        long time=Math.max(0L,dayTime),day=time/24000L+1L;
        Event event=Event.ORDINARY;
        if(overworld) {
            if(day%128L==0L) event=Event.BLUE;
            else if(day%32L==0L) event=Event.BLOOD;
            else if((day+8L)%128L==0L) event=Event.MOON_DOG;
            else if((day+8L)%32L==0L) event=Event.HARVEST;
        }
        return new State(day,(int)(day%8L),overworld,!daytime((int)(time%24000L)),event);
    }
    /** Raw phase fraction remains distinct from the stronger visual moonlight formula. */
    public static float phaseBrightness(State state) { return PHASE_BRIGHTNESS[state.phase()]; }
    /** abw.getMoonBrightness: dim blood moon is not brightened by rain. */
    public static float moonBrightness(State state,float rain) {
        float light=state.bloodPeriod()?.6F:state.harvestPeriod()?1F:state.bluePeriod()?1.1F:phaseBrightness(state)*.5F+.75F;
        float wet=Math.clamp(rain,0F,1F);
        return light>.75F?light*(1F-wet)+.75F*wet:light;
    }
}
