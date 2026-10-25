package io.github.ningmeng123.mite.client;

import static org.junit.jupiter.api.Assertions.*;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import org.junit.jupiter.api.Test;

/** Literal bfl/bfe/abw values, checked without Minecraft, a render thread or OpenGL. @author NingMeng123 */
class LunarVisualsTest {
    private static final float EPSILON = .00001F;
    private static long time(int day, int tick) { return (day - 1L) * 24000L + tick; }
    private static LunarCalendar.State state(int day) { return LunarCalendar.state(time(day, 18000), true); }
    private static void color(LunarVisuals.Color actual, float red, float green, float blue) {
        assertEquals(red, actual.red(), EPSILON);
        assertEquals(green, actual.green(), EPSILON);
        assertEquals(blue, actual.blue(), EPSILON);
    }
    @Test void sourceDiskTintsUseEventPeriodsIncludingDaylight() {
        color(LunarVisuals.moonColor(state(1)), 1F, 1F, 1F);
        color(LunarVisuals.moonColor(state(32)), .6F, .2F, .1F);
        color(LunarVisuals.moonColor(state(24)), 1F, .8F, .45F);
        color(LunarVisuals.moonColor(state(128)), .66F, .74F, 1F);
        color(LunarVisuals.moonColor(state(120)), 1F, 1F, 1F);
        color(LunarVisuals.moonColor(LunarCalendar.state(time(32, 6000), true)), .6F, .2F, .1F);
        color(LunarVisuals.moonColor(LunarCalendar.state(time(32, 18000), false)), 1F, 1F, 1F);
    }
    @Test void sourceGeometryNeverEnlargesTheMoonDiskForSpecialEvents() {
        assertEquals(20F, LunarVisuals.MOON_RADIUS);
        assertEquals(160F, LunarVisuals.HALO_RADIUS);
        assertEquals(80F, LunarVisuals.RING_RADIUS);
    }
    @Test void bloodAndBlueHaloHaveTheirIndependentSourceColors() {
        var blood = LunarVisuals.glow(state(32), .5F, 0F);
        assertEquals(LunarVisuals.GlowTexture.HALO, blood.texture());
        assertEquals(160F, blood.radius());
        color(blood.color(), .6F, .2F, .1F);
        color(LunarVisuals.glow(state(128), .5F, 0F).color(), .33F, .37F, .8F);
        assertEquals(LunarVisuals.GlowTexture.NONE, LunarVisuals.glow(state(24), .5F, 0F).texture());
    }
    @Test void haloFadesAtTheActualCelestialAngleAndRainOnlyMultipliesOnce() {
        var blood = state(32);
        assertEquals(.25F, LunarVisuals.glow(blood, .77F, 0F).alpha(), EPSILON);
        assertEquals(.125F, LunarVisuals.glow(blood, .7775F, 0F).alpha(), EPSILON);
        assertEquals(0F, LunarVisuals.glow(blood, .785F, 0F).alpha(), EPSILON);
        assertEquals(0F, LunarVisuals.glow(blood, .9F, 0F).alpha());
        assertEquals(.125F, LunarVisuals.glow(blood, .5F, .5F).alpha(), EPSILON);
        assertEquals(0F, LunarVisuals.glow(blood, .5F, 1F).alpha());
    }
    @Test void moonDogRingUsesTheFourSourceFadeBoundaries() {
        var dog = state(120);
        var ring = LunarVisuals.glow(dog, .5F, 0F);
        assertEquals(LunarVisuals.GlowTexture.RING, ring.texture());
        assertEquals(80F, ring.radius());
        color(ring.color(), .4975F, .6275F, .85F);
        assertEquals(0F, LunarVisuals.glow(dog, .1799F, 0F).alpha());
        assertEquals(0F, LunarVisuals.glow(dog, .18F, 0F).alpha());
        assertEquals(.25F, LunarVisuals.glow(dog, .23F, 0F).alpha(), EPSILON);
        assertEquals(.5F, LunarVisuals.glow(dog, .28F, 0F).alpha(), EPSILON);
        assertEquals(.5F, LunarVisuals.glow(dog, .6F, 0F).alpha(), EPSILON);
        assertEquals(.25F, LunarVisuals.glow(dog, .6925F, 0F).alpha(), EPSILON);
        assertEquals(0F, LunarVisuals.glow(dog, .785F, 0F).alpha(), EPSILON);
        assertEquals(0F, LunarVisuals.glow(dog, .8F, 0F).alpha());
        assertEquals(.25F, LunarVisuals.glow(dog, .5F, .5F).alpha(), EPSILON);
    }
    @Test void moonAscensionUsesTheShiftedSourceClock() {
        assertEquals(0F, LunarVisuals.moonAscension(13000));
        assertEquals(.1F, LunarVisuals.moonAscension(13500), EPSILON);
        assertEquals(.2F, LunarVisuals.moonAscension(14000), EPSILON);
        assertEquals(1F, LunarVisuals.moonAscension(18000), EPSILON);
        assertEquals(.2F, LunarVisuals.moonAscension(22000), EPSILON);
        assertEquals(0F, LunarVisuals.moonAscension(23000));
        assertEquals(0F, LunarVisuals.moonAscension(6000));
        assertEquals(LunarVisuals.moonAscension(18000), LunarVisuals.moonAscension(time(128, 18000)));
    }
    @Test void lunarBrightnessModifiesOnlySkyExposureAndFadesAtTwilight() {
        assertEquals(1.25F, LunarVisuals.lightState(time(8, 18000), true, 0F).skyMultiplier(), EPSILON);
        assertEquals(.75F, LunarVisuals.lightState(time(4, 18000), true, 0F).skyMultiplier(), EPSILON);
        assertEquals(.6F, LunarVisuals.lightState(time(32, 18000), true, 0F).skyMultiplier(), EPSILON);
        assertEquals(1F, LunarVisuals.lightState(time(24, 18000), true, 0F).skyMultiplier(), EPSILON);
        assertEquals(1.1F, LunarVisuals.lightState(time(128, 18000), true, 0F).skyMultiplier(), EPSILON);
        assertEquals(.8F, LunarVisuals.lightState(time(32, 13500), true, 0F).skyMultiplier(), EPSILON);
        assertEquals(1F, LunarVisuals.lightState(time(32, 13000), true, 0F).skyMultiplier(), EPSILON);
        assertEquals(1F, LunarVisuals.lightState(time(32, 23000), true, 0F).skyMultiplier(), EPSILON);
        assertEquals(1F, LunarVisuals.lightState(time(32, 6000), true, 0F).skyMultiplier(), EPSILON);
    }
    @Test void rainDimsBrightMoonlightButCannotBrightenTheBloodMoon() {
        assertEquals(.75F, LunarVisuals.lightState(time(8, 18000), true, 1F).skyMultiplier(), EPSILON);
        assertEquals(.75F, LunarVisuals.lightState(time(128, 18000), true, 1F).skyMultiplier(), EPSILON);
        assertEquals(.6F, LunarVisuals.lightState(time(32, 18000), true, 1F).skyMultiplier(), EPSILON);
    }
    @Test void sourceNightTintsUseExactChannelMultipliers() {
        color(LunarVisuals.lightTint(LunarVisuals.lightState(time(32, 18000), true, 0F), 15, 0), 2F, 1.4F, 1F);
        color(LunarVisuals.lightTint(LunarVisuals.lightState(time(24, 18000), true, 0F), 15, 0), 1.48F, 1.32F, .84F);
        color(LunarVisuals.lightTint(LunarVisuals.lightState(time(128, 18000), true, 0F), 15, 0), 1F, 1.1F, 1.25F);
        color(LunarVisuals.lightTint(LunarVisuals.lightState(time(120, 18000), true, 0F), 15, 0), 1F, 1F, 1F);
    }
    @Test void torchesAndUndergroundSkyOcclusionAttenuateTheTint() {
        var blood = LunarVisuals.lightState(time(32, 18000), true, 0F);
        color(LunarVisuals.lightTint(blood, 0, 0), 1F, 1F, 1F);
        color(LunarVisuals.lightTint(blood, 15, 8), 1F, 1F, 1F);
        color(LunarVisuals.lightTint(blood, 15, 15), 1F, 1F, 1F);
        color(LunarVisuals.lightTint(blood, 15, 7), 1F + 1F / 15F, 1F + .4F / 15F, 1F);
        color(LunarVisuals.lightTint(blood, 3, 0), 1.2F, 1.08F, 1F);
    }
    @Test void tintDoesNotLeakIntoDaylightOrOtherDimensions() {
        color(LunarVisuals.lightTint(LunarVisuals.lightState(time(32, 6000), true, 0F), 15, 0), 1F, 1F, 1F);
        var other = LunarVisuals.lightState(time(32, 18000), false, 0F);
        assertEquals(1F, other.skyMultiplier());
        color(LunarVisuals.lightTint(other, 15, 0), 1F, 1F, 1F);
    }
    @Test void sourcePostGammaTintIsClampedBeforePixelConversion() {
        var blood = LunarVisuals.lightState(time(32, 18000), true, 0F);
        color(LunarVisuals.tintLightmap(blood, 15, 0, .75F, .5F, .5F), 1F, .7F, .5F);
        color(LunarVisuals.tintLightmap(blood, 15, 0, -.1F, -.1F, 2F), 0F, 0F, 1F);
    }
    @Test void requestedDaytimeBloodRainHasADarkRedSkyAtFullRain() {
        var original = new LunarVisuals.Color(.1F, .2F, .3F);
        var day = LunarCalendar.state(time(32, 6000), true);
        color(LunarVisuals.bloodRainAtmosphere(day, 1F, original), .16F, .024F, .036F);
        color(LunarVisuals.bloodRainAtmosphere(day, .5F, original), .13F, .112F, .168F);
        color(LunarVisuals.bloodRainAtmosphere(day, 2F, original), .16F, .024F, .036F);
        color(LunarVisuals.bloodRainAtmosphere(day, -1F, original), .1F, .2F, .3F);
    }
    @Test void bloodRainDropletsAreRedOnlyOnBloodDaysIncludingDawnAndDuskBoundaries() {
        for (int day : new int[]{32, 64, 96}) {
            for (int tick : new int[]{0, 6000, 12999, 23001, 23999})
                color(LunarVisuals.bloodRainTint(LunarCalendar.state(time(day, tick), true)), 1F, .08F, .08F);
            for (int tick : new int[]{13000, 18000, 23000})
                color(LunarVisuals.bloodRainTint(LunarCalendar.state(time(day, tick), true)), 1F, 1F, 1F);
        }
        for (int day : new int[]{1, 24, 120, 128})
            color(LunarVisuals.bloodRainTint(LunarCalendar.state(time(day, 6000), true)), 1F, 1F, 1F);
        color(LunarVisuals.bloodRainTint(LunarCalendar.state(time(32, 6000), false)), 1F, 1F, 1F);
    }
    @Test void daytimeAtmosphereExtensionDoesNotReplaceMoonNightsOtherEventsOrDimensions() {
        var original = new LunarVisuals.Color(.1F, .2F, .3F);
        for (int day : new int[]{1, 24, 120, 128})
            assertSame(original, LunarVisuals.bloodRainAtmosphere(LunarCalendar.state(time(day, 6000), true), 1F, original));
        assertSame(original, LunarVisuals.bloodRainAtmosphere(LunarCalendar.state(time(32, 18000), true), 1F, original));
        assertSame(original, LunarVisuals.bloodRainAtmosphere(LunarCalendar.state(time(32, 6000), false), 1F, original));
        assertSame(original, LunarVisuals.bloodRainAtmosphere(LunarCalendar.state(time(32, 6000), true), 0F, original));
    }
    @Test void daytimeBloodAirFogDoesNotBrightenCaveOrBlindnessDarkness() {
        var state = LunarCalendar.state(time(32, 6000), true);
        color(LunarVisuals.bloodRainFog(state, 1F, new LunarVisuals.Color(0F, 0F, 0F)), 0F, 0F, 0F);
        color(LunarVisuals.bloodRainFog(state, 1F, new LunarVisuals.Color(.1F, .2F, .3F)), .1F, .024F, .036F);
    }
}
