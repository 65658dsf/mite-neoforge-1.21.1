package io.github.ningmeng123.mite.gametest;

import java.util.function.Supplier;

/** Isolates synchronous rule fixtures from the independently tested server schedulers. @author NingMeng123 */
public final class PathfindingTestSettings implements AutoCloseable {
    private final String asynchronous = System.getProperty("mite.asyncPathfinding");
    private final String budget = System.getProperty("mite.pursuitSearchBudget");

    private PathfindingTestSettings(boolean asynchronous) {
        System.setProperty("mite.asyncPathfinding", Boolean.toString(asynchronous));
        System.setProperty("mite.pursuitSearchBudget", "false");
    }

    public static PathfindingTestSettings synchronous() { return new PathfindingTestSettings(false); }
    public static PathfindingTestSettings unboundedAsync() { return new PathfindingTestSettings(true); }

    public static void runSynchronous(Runnable action) {
        try (var ignored = synchronous()) { action.run(); }
    }

    public static <T> T synchronousResult(Supplier<T> action) {
        try (var ignored = synchronous()) { return action.get(); }
    }

    @Override public void close() {
        restore("mite.asyncPathfinding", asynchronous);
        restore("mite.pursuitSearchBudget", budget);
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
