package io.github.ningmeng123.mite.rules;

/** Complete equipment metal costs round upward only after counting all nuggets. @author NingMeng123 */
public final class MaterialUpgradeCostRules {
    private MaterialUpgradeCostRules() {}

    public static int roundedIngots(int ingots, int nuggets) {
        if (ingots < 0 || nuggets < 0) throw new IllegalArgumentException("Negative equipment metal cost");
        long units = 9L * ingots + nuggets;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (units + 8L) / 9L));
    }
}
