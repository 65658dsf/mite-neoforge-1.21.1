package io.github.ningmeng123.mite.rules;

/** Bounded release-time prediction; arrows remain ordinary, inaccurate projectiles. @author NingMeng123 */
public final class SkeletonAimRules {
    public static final double DRAG = .99D;
    public static final double GRAVITY = .05D;
    public static final double MAX_LEAD = 4D;
    private SkeletonAimRules() {}

    public record Aim(double x, double y, double z, double flightTicks) {}

    public static float inaccuracy(int difficulty) { return Math.max(3F, 6F - difficulty); }

    public static Aim aim(double dx, double dy, double dz, double targetVx, double targetVz, double speed) {
        if (!Double.isFinite(dx + dy + dz + targetVx + targetVz + speed) || speed <= 0)
            throw new IllegalArgumentException("Finite aim coordinates and positive arrow speed required");
        double targetSpeed = Math.hypot(targetVx, targetVz);
        if (targetSpeed > .35D) {
            targetVx *= .35D / targetSpeed;
            targetVz *= .35D / targetSpeed;
        }
        Aim best = new Aim(dx, dy, dz, 1D);
        double bestError = Double.POSITIVE_INFINITY;
        // Solve the native drag/gravity trajectory with a bounded partial movement lead.
        for (double ticks = 1D; ticks <= 40D; ticks += .25D) {
            double leadX = targetVx * ticks * .8D, leadZ = targetVz * ticks * .8D;
            double lead = Math.hypot(leadX, leadZ);
            if (lead > MAX_LEAD) { leadX *= MAX_LEAD / lead; leadZ *= MAX_LEAD / lead; }
            double displacement = (1D - Math.pow(DRAG, ticks)) / (1D - DRAG);
            double x = (dx + leadX) / displacement;
            double y = (dy + GRAVITY / (1D - DRAG) * (ticks - displacement)) / displacement;
            double z = (dz + leadZ) / displacement;
            double error = Math.abs(Math.sqrt(x * x + y * y + z * z) - speed);
            if (error < bestError) { bestError = error; best = new Aim(x, y, z, ticks); }
        }
        return best;
    }
}
