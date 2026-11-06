package io.github.ningmeng123.mite.client;

/** Tracks actual placement attempts during one native use action. @author NingMeng123 */
public final class PlacementClickTiming {
    private static final ThreadLocal<Boolean> ATTEMPTED = new ThreadLocal<>();

    private PlacementClickTiming() {}

    public static void begin() {
        ATTEMPTED.remove();
    }

    public static void attempted() {
        ATTEMPTED.set(true);
    }

    public static void actionConsumed() {
        ATTEMPTED.remove();
    }

    public static boolean consumeAttempted() {
        boolean attempted = Boolean.TRUE.equals(ATTEMPTED.get());
        ATTEMPTED.remove();
        return attempted;
    }

    public static int rightClickDelay(int nativeDelay, long remainingMillis, boolean attempted, boolean usingItem) {
        if (remainingMillis > 0L) return 1;
        return attempted && !usingItem ? 0 : nativeDelay;
    }
}
