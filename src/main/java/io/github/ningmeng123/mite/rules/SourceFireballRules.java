package io.github.ningmeng123.mite.rules;

/** Independently expressed td/tj/uj launch arithmetic. @author NingMeng123 */
public final class SourceFireballRules {
    public static final double BLAZE_RANGE = 30D, GHAST_RANGE = 64D, GHAST_TARGET_RANGE = 100D;
    private SourceFireballRules() {}

    public record Direction(double x, double y, double z) {
        public Direction normalized() {
            double length = Math.sqrt(x * x + y * y + z * z);
            return length > 1.0E-12 && Double.isFinite(length)
                    ? new Direction(x / length, y / length, z / length) : new Direction(0, 0, 1);
        }
    }

    public static float blazeLead(double distanceSquared, float random) {
        return (float)Math.pow(distanceSquared, .42D) * 2F * Math.min(.5F + random, 1F);
    }
    public static float ghastLead(double distanceSquared, float random) {
        return (float)Math.pow(distanceSquared, .44D) * (.5F + random);
    }
    public static float blazeSpread(float distance) { return (float)Math.sqrt(distance) * .25F; }

    /** The owner-based uj constructor adds .4 Gaussian noise to all three axes after td's X/Z spread. */
    public static Direction blazeDirection(double x, double y, double z, float distance,
            double spreadX, double spreadZ, double constructorX, double constructorY, double constructorZ) {
        float spread = blazeSpread(distance);
        return new Direction(x + spreadX * spread + constructorX * .4D, y + constructorY * .4D,
                z + spreadZ * spread + constructorZ * .4D).normalized();
    }

    /** tj uses the target constructor: independent triangular +/-10% multipliers, without additive Gaussian noise. */
    public static Direction ghastDirection(double x, double y, double z,
            float xFirst, float xSecond, float yFirst, float ySecond, float zFirst, float zSecond) {
        return new Direction(x * (1F + (xFirst - xSecond) * .1F), y * (1F + (yFirst - ySecond) * .1F),
                z * (1F + (zFirst - zSecond) * .1F)).normalized();
    }

    /** Absolute owner ticks keep the shared attack cooldown running when the attack goal temporarily stops. */
    public static final class BlazeCycle {
        private int step, nextAttackTick;
        public int step() { return step; }
        public boolean charged() { return step != 0; }
        public boolean ready(int tick) { return tick >= nextAttackTick; }
        public void melee(int tick) { nextAttackTick = tick + 20; }
        public boolean advance(int tick) {
            if (!ready(tick)) return false;
            step++;
            if (step == 1) nextAttackTick = tick + 20;
            else if (step <= 4) nextAttackTick = tick + 6;
            else { step = 0; nextAttackTick = tick + 20; }
            return step > 1;
        }
    }

    /** Charge only with a visible in-range target; obscured positive charge decays, negative recovery waits. */
    public static final class GhastCycle {
        private int charge;
        public int charge() { return charge; }
        public boolean charging() { return charge > 10; }
        public boolean warning() { return charge == 11; }
        public boolean advance(boolean eligible) {
            if (!eligible) { if (charge > 0) charge--; return false; }
            if (++charge != 20) return false;
            charge = -40;
            return true;
        }
    }
}
