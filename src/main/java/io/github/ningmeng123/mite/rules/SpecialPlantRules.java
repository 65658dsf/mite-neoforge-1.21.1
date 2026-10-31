package io.github.ningmeng123.mite.rules;

import java.util.Random;

/** Temperature-independent plant growth with the original coordinate RNG table. @author NingMeng123 */
public final class SpecialPlantRules {
    private SpecialPlantRules() {}
    public static float stemClimate(boolean melon, boolean humid) {
        return (melon && humid ? 1.2F : 1F) * .25F;
    }
    public static boolean caneClimate(float slowdownDraw, int light) {
        return slowdownDraw >= .8F && light >= 15;
    }
    public static int stemColor(int growth, boolean dead) {
        int age = Math.clamp(growth, 0, 7), red = age * 32, green = 255 - age * 8, blue = age * 4;
        if (dead) { red = (224 + 296) / 3; green = (199 + 200) / 3; blue = (28 + 80) / 3; }
        return red << 16 | green << 8 | blue;
    }
    public static int cactusIndex(int x, int supportY, int z) { return (x + supportY * 501 + z * 9043) & Short.MAX_VALUE; }
    public static byte[] cactusHeights(long seed) {
        byte[] result = new byte[32768];
        Random random = new Random(seed);
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) ((random.nextInt() & Integer.MAX_VALUE) % 3 + 1);
            random.nextInt(); random.nextFloat(); random.nextFloat(); random.nextDouble();
        }
        return result;
    }
}
