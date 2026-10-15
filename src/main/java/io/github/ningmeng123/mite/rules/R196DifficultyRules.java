package io.github.ningmeng123.mite.rules;

/** R196 abw.I/aei regional tension and the fixed Extreme setting. @author NingMeng123 */
public final class R196DifficultyRules {
    public static final int FIXED_DIFFICULTY = 3;
    public static final long INHABITED_TIME_TO_MAXIMUM = 3_600_000L;
    private static final float[] PHASE_FRACTIONS = {1F, .75F, .5F, .25F, 0F, .25F, .5F, .75F};

    private R196DifficultyRules() {}

    /**
     * abw.I(III)F, including the inactive peaceful/easy/normal branches.
     * Production worlds use difficulty 3. The phase input is the raw eight-phase fraction,
     * not weather-adjusted moonlight or modern effective local difficulty.
     */
    public static float tension(int difficulty, long inhabitedTime, float phaseBrightness, boolean validPosition) {
        boolean hard = difficulty == FIXED_DIFFICULTY;
        float result = 0F;
        if (validPosition) {
            result += clamp((float) inhabitedTime / 3_600_000F, 0F, 1F) * (hard ? 1F : .75F);
            result += phaseBrightness * .25F;
        }
        if (difficulty < 2) result *= difficulty / 2F;
        return clamp(result, 0F, hard ? 1.5F : 1F);
    }

    /**
     * aei.a(J)I and abw.x()F: tick zero is phase 1, and day eight is the full moon.
     * Nonnegative times match the source. Floor division/modulo extend the cycle safely
     * to modern commands that set a negative time, instead of indexing the array negatively.
     */
    public static float phaseFraction(long dayTime) {
        int phase = (int) Math.floorMod(Math.floorDiv(dayTime, 24_000L) + 1L, 8L);
        return PHASE_FRACTIONS[phase];
    }

    private static float clamp(float value, float minimum, float maximum) {
        return value < minimum ? minimum : value > maximum ? maximum : value;
    }
}
