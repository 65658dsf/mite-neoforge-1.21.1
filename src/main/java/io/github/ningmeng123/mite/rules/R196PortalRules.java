package io.github.ningmeng123.mite.rules;

import java.util.Random;
import java.util.function.BiPredicate;

/** R196 aps.java routing and rune coordinate rules, without Minecraft state. @author NingMeng123 */
public final class R196PortalRules {
    public static final int OVERWORLD = 0;
    public static final int UNDERWORLD = 1;
    public static final int NETHER = 2;
    public static final int SEARCH_RADIUS = 128;

    private R196PortalRules() {}

    public static int ordinaryDestination(int source, boolean touchingBottom) {
        if (source == NETHER) return UNDERWORLD;
        if (touchingBottom) return source == OVERWORLD ? UNDERWORLD : NETHER;
        return OVERWORLD;
    }

    /** Use each dimension's actual minimum; both modern overworld and underworld have a negative floor. */
    public static boolean canTouchBottom(int frameBottomY, int minBuildHeight) {
        return frameBottomY <= minBuildHeight + 8 && frameBottomY > minBuildHeight;
    }

    public static double coordinateScale(int source, int destination) {
        if (source == NETHER && destination != NETHER) return 8.0;
        if (source != NETHER && destination == NETHER) return 0.125;
        return 1.0;
    }

    public static int runeSeed(int lowerLeft, int lowerRight, int upperLeft, int upperRight) {
        for (int value : new int[]{lowerLeft, lowerRight, upperLeft, upperRight}) {
            if (value < 0 || value > 15) throw new IllegalArgumentException("Rune value must be 0..15");
        }
        return lowerLeft | lowerRight << 4 | upperLeft << 8 | upperRight << 12;
    }

    public static int runeRadius(boolean adamantium, double worldRadius) {
        int radius = Math.max(1, (int)Math.min(worldRadius / 2.0, 40000.0));
        return adamantium ? radius : Math.max(1, radius / 8);
    }

    /** World seed affects terrain/ocean rejection, but source X/Z random is seeded only by corner runes. */
    public static Coordinates runeCoordinates(int seed, boolean adamantium, int radius,
            BiPredicate<Integer, Integer> isOcean) {
        if (seed == 0) return new Coordinates(0, 0);
        Random random = new Random(seed);
        int x = 0;
        int z = 0;
        for (int attempt = 0; attempt < 4; attempt++) {
            do {
                x = random.nextInt(radius * 2) - radius;
                z = random.nextInt(radius * 2) - radius;
            } while (adamantium && Math.hypot(x, z) < radius / 2);
            if (!isOcean.test(x, z)) break;
        }
        return new Coordinates(x, z);
    }

    public record Coordinates(int x, int z) {}
}
