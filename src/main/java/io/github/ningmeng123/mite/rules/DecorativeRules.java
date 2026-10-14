package io.github.ningmeng123.mite.rules;

/** Independent R196 BlockFlowerMulti/ant facts. @author NingMeng123 */
public final class DecorativeRules {
    private DecorativeRules() {}
    public static boolean flowerBiome(int subtype, float temperature, boolean swamp, boolean jungle) {
        if (subtype == 2) return swamp;
        if (swamp) return false;
        return switch (subtype) {
            case 1 -> temperature > .8F;
            case 5, 7 -> temperature >= .7F;
            case 8 -> !jungle;
            default -> true;
        };
    }
    public static int flowerPatchAttempts(int subtype, boolean plains, boolean jungle) {
        return subtype == 2 ? 8 : plains || jungle ? 64 : 16;
    }
    public static int witherContactDuration(int duration, int amplifier) {
        // ant extends only level-I Wither in intervals of 40, up to 200; higher effects stay intact.
        return amplifier > 0 || duration < 0 ? duration : duration + 40 <= 200 ? duration + 40 : duration;
    }
    public static float flowerHeight(int subtype) {
        return switch (subtype) { case 2 -> .875F; case 5 -> .75F; case 1, 7, 8 -> .8125F; default -> .6F; };
    }
    public static float witherwoodFallDamage(float distance, int jumpAmplifier, float modernMultiplier) {
        float jump = jumpAmplifier < 0 ? 0 : jumpAmplifier + 1;
        float damage = ((distance - 2.5F - jump) * 2F - 2F) * .8F;
        // Source of.b calls attackEntityFrom only for damage >= 1, and keeps fractional damage.
        return damage < 1F ? 0F : Math.max(0, damage * modernMultiplier);
    }
}
