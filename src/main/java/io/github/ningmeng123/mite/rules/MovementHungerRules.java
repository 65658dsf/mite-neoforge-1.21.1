package io.github.ningmeng123.mite.rules;

/** Source uf.j uses rounded centimeters for water/ground, but the actual upward distance when climbing. @author NingMeng123 */
public final class MovementHungerRules {
    private MovementHungerRules() {}
    public static int centimeters(double x, double y, double z) {
        return Math.round((float)Math.sqrt(x * x + y * y + z * z) * 100F);
    }
    public static float exhaustion(double x, double y, double z, boolean eyesInWater, boolean inWater,
                                   boolean climbable, boolean onGround, boolean sleeping, boolean sprinting) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return 0F;
        if (eyesInWater) return water(centimeters(x, y, z));
        if (inWater) return water(centimeters(x, 0D, z));
        if (climbable) return y > 0D ? (float)y / 10F : 0F;
        if (!onGround || sleeping) return 0F;
        int distance = centimeters(x, 0D, z);
        return distance > 0 ? (sprinting ? .05F : .01F) * distance * .01F : 0F;
    }
    private static float water(int distance) { return distance > 0 ? .015F * distance * .01F : 0F; }
    public static float rowing(float forward) { return Float.isFinite(forward) ? Math.abs(forward) * .01F : 0F; }
}
