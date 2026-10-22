package io.github.ningmeng123.mite.client;

/** Aligns native repeated-use timing only with actual placement attempts. @author NingMeng123 */
public final class PlacementClickTiming {
    private static final ThreadLocal<Boolean> BLOCKED = new ThreadLocal<>();

    private PlacementClickTiming() {}

    public static void begin() {
        BLOCKED.remove();
    }

    public static void blocked() {
        BLOCKED.set(true);
    }

    public static boolean consumeBlocked() {
        boolean blocked = Boolean.TRUE.equals(BLOCKED.get());
        BLOCKED.remove();
        return blocked;
    }

    public static int rightClickDelay(int nativeDelay, int previousRemaining, int remaining, boolean blocked) {
        return remaining > 0 && (blocked || remaining > previousRemaining) ? remaining : nativeDelay;
    }
}
