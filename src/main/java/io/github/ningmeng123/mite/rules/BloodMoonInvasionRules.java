package io.github.ningmeng123.mite.rules;

/** User-requested modern progression after the third blood moon; original R196 depth rules remain separate. @author NingMeng123 */
public final class BloodMoonInvasionRules {
    public static final long UNLOCK_DAY = 97L;
    public static final long UNLOCK_TIME = 2_304_000L;

    private BloodMoonInvasionRules() {}

    /** Calendar days use raw time / 24000 + 1, without the equipment-day six-thousand-tick offset. */
    public static boolean isUnlocked(long dayTime, boolean overworld) {
        return overworld && dayTime >= UNLOCK_TIME;
    }

    /** Only the post-third-blood-moon extension relaxes depth; cold/desert surface niches remain. */
    public static boolean depthAllows(String sourceClass, int sourceY, boolean overworld, boolean bloodNight,
            boolean freezing, boolean desert, boolean invasionUnlocked) {
        if (!overworld || !invasionUnlocked)
            return R196SpawnRules.depthAllows(sourceClass, sourceY, overworld, bloodNight, freezing, desert);
        if (sourceClass.equals("EntityWight") && sourceY > 48) return freezing;
        if (sourceClass.equals("EntityShadow") && sourceY > 32) return desert;
        return true;
    }
}
