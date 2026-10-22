package io.github.ningmeng123.mite.entity.animal;

/** EntityLivestock, rq, rr, ry, rz and nk numerical rules. @author NingMeng123 */
public final class LivestockRules032 {
    public static final int NEWBORN_AGE = -64000;
    public static final int BREEDING_COOLDOWN = 16000;
    public static final int RECOVERY_TICKS = 3 * 60 * 20;
    private LivestockRules032() {}
    public record Needs(float food, float water, float freedom) {
        public Needs { food = clamp(food); water = clamp(water); freedom = clamp(freedom); }
        public boolean well() { return Math.min(food, Math.min(water, freedom)) >= .25F; }
        public Needs update(boolean nearFood, boolean nearWater, boolean raining, boolean crowded) {
            return new Needs(food + (nearFood ? .1F : -.005F), water + (nearWater ? .1F : raining ? .01F : -.005F),
                    freedom + (crowded ? -.005F : .1F));
        }
        public Needs feed() { return new Needs(food + .5F, water, freedom); }
        public Needs drink(int volume) { return new Needs(food, water + .25F * volume, freedom); }
        public Needs inherit(Needs other) { return new Needs(Math.min(food, other.food), Math.min(water, other.water), Math.min(freedom, other.freedom)); }
    }
    public static float clamp(float value) { return Float.isFinite(value) ? Math.clamp(value, 0F, 1F) : 0F; }
    public static int searchRoll(float resource) { return resource < .05F ? 10 : resource < .25F ? 40 : 120; }
    public static int searchDistance(float resource) { return resource < .05F ? 48 : resource < .25F ? 32 : 16; }
    public static int searchCandidates(float resource) { return resource < .05F ? 24 : resource < .25F ? 16 : 8; }
    public static int manurePeriod(boolean chicken, boolean pigOrSheep) { return chicken ? 384000 : pigOrSheep ? 48000 : 24000; }
    public static float tramplingEffect(int count) { return Math.clamp((count - 3) * .05F, 0F, .5F); }
}
