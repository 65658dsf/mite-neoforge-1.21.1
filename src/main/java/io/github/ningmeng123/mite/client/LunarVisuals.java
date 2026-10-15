package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.rules.LunarCalendar;

/** The active R196 moon and lightmap formulas, independent of any renderer or GL context. @author NingMeng123 */
public final class LunarVisuals {
    public static final float MOON_RADIUS = 20F;
    public static final float HALO_RADIUS = 160F;
    public static final float RING_RADIUS = 80F;
    private LunarVisuals() {}

    public record Color(float red, float green, float blue) {}
    public enum GlowTexture { NONE, HALO, RING }
    public record Glow(GlowTexture texture, float radius, Color color, float alpha) {}
    public record LightState(LunarCalendar.State calendar, float skyMultiplier, float ascension) {}

    private static final Color WHITE = new Color(1F, 1F, 1F);
    private static final Color BLOOD = new Color(.6F, .2F, .1F);
    private static final Color BLUE = new Color(.66F, .74F, 1F);
    private static final Color HARVEST = new Color(1F, .8F, .45F);
    private static final Color BLUE_HALO = new Color(.33F, .37F, .8F);
    private static final Color MOON_DOG = new Color(.4975F, .6275F, .85F);
    private static final Glow NO_GLOW = new Glow(GlowTexture.NONE, 0F, WHITE, 0F);

    /** bfl.a(float): tint is selected for the whole event day, not just its night flag. */
    public static Color moonColor(LunarCalendar.State state) {
        if (state.bloodPeriod()) return BLOOD;
        if (state.harvestPeriod()) return HARVEST;
        if (state.bluePeriod()) return BLUE;
        return WHITE;
    }

    /** bfl.a(float): both glows have the same position and rotation as the moon disk. */
    public static Glow glow(LunarCalendar.State state, float celestialAngle, float rain) {
        float visibility = 1F - clamp(rain, 0F, 1F);
        if (state.bloodPeriod() || state.bluePeriod()) {
            float alpha = .25F;
            if (celestialAngle > .77F) alpha *= 1F - (celestialAngle - .77F) / (.785F - .77F);
            // The legacy GL color operation clamps the negative post-sunrise alpha to zero.
            return new Glow(GlowTexture.HALO, HALO_RADIUS, state.bloodPeriod() ? BLOOD : BLUE_HALO,
                    visibility * clamp(alpha, 0F, 1F));
        }
        if (state.moonDogPeriod()) {
            float alpha = visibility * .5F;
            if (celestialAngle < .18F || celestialAngle > .785F) alpha = 0F;
            else if (celestialAngle < .28F) alpha *= (celestialAngle - .18F) / (.28F - .18F);
            else if (celestialAngle > .6F) alpha *= 1F - (celestialAngle - .6F) / (.785F - .6F);
            return new Glow(GlowTexture.RING, RING_RADIUS, MOON_DOG, clamp(alpha, 0F, 1F));
        }
        return NO_GLOW;
    }

    /** abw.getMoonAscensionFactor(): source sunrise/sunset use the clock shifted by 6000. */
    public static float moonAscension(long dayTime) {
        int adjusted = (int)((Math.max(0L, dayTime) % 24000L + 6000L) % 24000L);
        if (adjusted <= 5000) return 1F - adjusted / 5000F;
        if (adjusted >= 19000) return (adjusted - 19000) / 5000F;
        return 0F;
    }

    /** bfe.h(float): take one immutable snapshot for all 256 lightmap cells. */
    public static LightState lightState(long dayTime, boolean overworld, float rain) {
        var state = LunarCalendar.state(dayTime, overworld);
        if (!overworld) return new LightState(state, 1F, 0F);
        float ascension = moonAscension(dayTime);
        float weight = clamp(ascension, 0F, .2F) * 5F;
        float multiplier = 1F + weight * (LunarCalendar.moonBrightness(state, rain) - 1F);
        return new LightState(state, multiplier, ascension);
    }

    /** bfe.h(float): tint vanishes without sky light or with block light at least eight. */
    public static Color lightTint(LightState frame, int skyLight, int blockLight) {
        float sky = clamp(skyLight / 15F, 0F, 1F);
        float blockAttenuation = clamp(1F - blockLight / 15F * 2F, 0F, 1F);
        float amount = clamp(frame.ascension(), 0F, .2F) * sky * blockAttenuation;
        var state = frame.calendar();
        if (state.bloodNight()) return new Color(1F + amount * 5F, 1F + amount * 2F, 1F);
        if (state.harvestNight()) return new Color(1F + amount * 2.4F, 1F + amount * 1.6F, 1F - amount * .8F);
        if (state.blueNight()) return new Color(1F, 1F + amount * .5F, 1F + amount * 1.25F);
        return WHITE;
    }

    /** Source tint follows gamma; clamp before the modern lightmap converts floats to bytes. */
    public static Color tintLightmap(LightState frame, int skyLight, int blockLight, float red, float green, float blue) {
        var tint = lightTint(frame, skyLight, blockLight);
        return new Color(clamp(red * tint.red(), 0F, 1F), clamp(green * tint.green(), 0F, 1F),
                clamp(blue * tint.blue(), 0F, 1F));
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
