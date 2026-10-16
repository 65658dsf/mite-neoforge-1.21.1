package io.github.ningmeng123.mite.rules;

/** uf starts portable handling at four hardness, ignores held efficiency and clamps after every penalty. @author NingMeng123 */
public final class PortableBlockRules {
    private PortableBlockRules() {}
    public static float strength(float hardness, int level, int hasteAmplifier, int fatigueAmplifier,
                                 boolean underwaterWithoutAffinity, boolean onGround, boolean energy) {
        float minimum = 4F * hardness;
        float strength = minimum;
        if (hasteAmplifier >= 0) strength *= 1F + (hasteAmplifier + 1) * .2F;
        if (fatigueAmplifier >= 0) strength *= 1F - (fatigueAmplifier + 1) * .2F;
        if (underwaterWithoutAffinity) strength /= 5F;
        if (!onGround) strength /= 5F;
        if (!energy) strength /= 5F;
        return Math.max(strength * MiningRules.speedMultiplierForLevel(level), minimum);
    }
}
