package io.github.ningmeng123.mite.rules;

import java.util.Map;
import java.util.function.IntUnaryOperator;

/** R196 aci/js/tm arithmetic with explicit modern-height adaptation. @author NingMeng123 */
public final class R196SpawnRules {
    public enum Category { MONSTER(50), ANIMAL(10), AMBIENT(5), AQUATIC(5);
        private final int cap; Category(int cap) { this.cap = cap; } public int cap() { return cap; }
    }
    public record Moon(int phase, boolean blood, boolean blue) {
        public boolean full() { return phase == 0; }
        public boolean fresh() { return phase == 4; }
    }
    public record RateState(int decreased, int increased, int paused) {}
    public record RateStep(RateState state, float modifier) {}
    private static final Map<String, Integer> DEPTH = Map.ofEntries(
            Map.entry("EntityGhoul", 56), Map.entry("EntityWight", 48), Map.entry("EntityVampireBat", 48),
            Map.entry("EntityRevenant", 44), Map.entry("EntityInvisibleStalker", 40), Map.entry("EntityEarthElemental", 40),
            Map.entry("EntityBlob", 40), Map.entry("EntityOoze", 32), Map.entry("EntityNightwing", 32),
            Map.entry("EntityBoneLord", 32), Map.entry("EntityPudding", 24), Map.entry("EntityDemonSpider", 32),
            Map.entry("EntityPhaseSpider", 32), Map.entry("EntityHellhound", 32), Map.entry("EntityShadow", 32));
    private R196SpawnRules() {}

    /** Overworld thresholds remain absolute; negative Y extends the deepest old tier. Underworld uses layout v2. */
    public static int sourceY(int modernY, boolean underworld) {
        return underworld ? Math.max(0, Math.floorDiv((modernY + 64) * 256, 384)) : Math.max(0, modernY);
    }

    public static boolean daytime(int timeOfDay) {
        int adjusted = Math.floorMod(timeOfDay + 6000, 24000);
        return adjusted > 5000 && adjusted < 19000;
    }

    public static Moon moon(long unadjustedTotalTime, boolean overworld) {
        long day = Math.max(0, unadjustedTotalTime) / 24000L + 1L;
        boolean blue = overworld && day % 128 == 0;
        return new Moon((int)(day % 8), overworld && day % 32 == 0 && !blue, blue);
    }

    public static int density(int sourceFootY, boolean overworld, boolean day, Moon moon, float strongholdProximity) {
        int density = 8;
        if (overworld) {
            density = (int)(8F * (1F + (64 - Math.max(0, sourceFootY)) / 32F));
            int minimum = !day && moon.blood() ? 12 : !day && moon.full() ? 10 : 8;
            density = Math.max(density, minimum);
        }
        return (int)(density + density * Math.clamp(strongholdProximity, 0F, 1F));
    }

    public static int cap(Category category, int eligibleChunks, boolean deep, boolean tournamentAnimals) {
        int cap = category.cap() * eligibleChunks / 256;
        if (category == Category.MONSTER && deep || category == Category.ANIMAL && tournamentAnimals) cap *= 2;
        return cap;
    }

    public static int outdoorNightDenominator(Moon moon) {
        return moon.blue() ? 54 : moon.blood() ? 2 : moon.full() ? 3 : moon.fresh() ? 6 : 4;
    }

    public static boolean depthAllows(String sourceClass, int sourceY, boolean overworld, boolean bloodNight, boolean freezing, boolean desert) {
        if (!overworld || sourceY <= DEPTH.getOrDefault(sourceClass, Integer.MAX_VALUE)) return true;
        if (!bloodNight) return false;
        return switch (sourceClass) {
            case "EntityGhoul", "EntityVampireBat", "EntityRevenant", "EntityNightwing", "EntityBoneLord" -> true;
            case "EntityWight" -> freezing;
            case "EntityShadow" -> desert;
            default -> false;
        };
    }

    public static boolean lightAllows(int savedSky, int brightness, int skyDraw, int brightnessDraw) {
        return savedSky <= skyDraw && brightness <= brightnessDraw;
    }
    /** aei light table, with the aej Nether floor and source extra drop-off kept separate. */
    public static float brightness(int light, boolean nether) {
        int clamped = Math.clamp(light, 0, 15);
        float dark = 1F - (float)clamped / 15F;
        float value = (1F - dark) / (dark * 3F + 1F);
        return nether ? value * .9F + .1F : value - (clamped > 0 && clamped < 15 ? .001F * (15 - clamped) : 0F);
    }
    public static boolean mobPathAllows(int light, boolean spawner, boolean nether) {
        return (spawner ? .7F : .5F) - brightness(light, nether) >= 0F;
    }

    public static boolean infernalCreeper(int sourceY, int depthDraw, float chanceDraw) {
        return depthDraw >= Math.max(0, sourceY) && chanceDraw < .5F;
    }

    public static boolean distanceAllows(double nearestPlayerSquared, double spawnSquared, boolean darkCloseException) {
        return spawnSquared >= 576 && nearestPlayerSquared >= (darkCloseException ? 64 : 576);
    }

    public static float initialProbability(boolean freezing) { return freezing ? .05F : .1F; }

    public static RateStep advanceRate(RateState state, boolean overworld, boolean bloodOrThunder, IntUnaryOperator random) {
        if (!overworld) return new RateStep(state, .25F);
        int decreased = state.decreased(), increased = state.increased(), paused = state.paused();
        float modifier = 1F;
        if (decreased > 0) { decreased--; modifier *= .5F; }
        else if (random.applyAsInt(24000) == 0) decreased = random.applyAsInt(4000) + 1;
        if (increased > 0) { increased--; modifier *= 2F; }
        else if (random.applyAsInt(24000) == 0) increased = random.applyAsInt(2000);
        if (paused > 0) { paused--; modifier = 0F; }
        else if (random.applyAsInt(24000) == 0) paused = random.applyAsInt(2000) + random.applyAsInt(2000);
        if (modifier < 1F && bloodOrThunder) modifier = 1F;
        return new RateStep(new RateState(decreased, increased, paused), modifier);
    }
}
