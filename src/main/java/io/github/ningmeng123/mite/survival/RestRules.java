package io.github.ningmeng123.mite.survival;

/** Independent clock and consciousness rules from abw/js/bdi. @author NingMeng123 */
public final class RestRules {
    private RestRules() {}
    public static boolean sleepingTime(long rawTime) {
        int adjusted = (int)Math.floorMod(Math.floorMod(rawTime, 24000L) + 6000L, 24000L);
        return adjusted < 4000 || adjusted >= 21000;
    }
    public static int untilSunrise(long rawTime) { return (int)Math.floorMod(23000L - Math.floorMod(rawTime, 24000L), 24000L); }
    public static int consciousness(int counter, boolean fallingAsleep) {
        return fallingAsleep ? Math.min(counter + 1, 110) : Math.max(counter - 1, 0);
    }
}
