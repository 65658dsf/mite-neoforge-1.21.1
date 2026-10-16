package io.github.ningmeng123.mite.world;

/** Independently sourced ss/abr numerical rules, separate from modern explosion radii. @author NingMeng123 */
public final class SourceExplosionRules {
    private SourceExplosionRules() {}
    public static double flattenedDistanceSquared(double x, double y, double z) { return x*x + y*y*.25D + z*z; }
    /** Damage intentionally retains the source singularity at the exact center; motion clamps distance to one. */
    public static float force(float power, double squaredDistance) { return (float)(power / Math.pow(squaredDistance, .75D)); }
    public static int equipmentWear(float power, double squaredDistance, float scale) {
        return Math.max(1, Math.round(force(power, squaredDistance) * 64F * scale));
    }
    public static boolean stopsPreBlast(float resistance) { return resistance < 0F || resistance > .1F; }
    public static float preBlastBudget(float power, float draw) { return power * (.7F + draw*.6F) * 2F * 1.25F; }
}
