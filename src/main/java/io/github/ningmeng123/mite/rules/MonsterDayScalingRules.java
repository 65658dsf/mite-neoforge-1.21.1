package io.github.ningmeng123.mite.rules;

/** Thirty-two-day rebasing with linear growth inside each period, independent of moon dates. @author NingMeng123 */
public final class MonsterDayScalingRules {
    private MonsterDayScalingRules() {}

    public static double healthMultiplier(long day) {
        return periodMultiplier(day, .005D);
    }

    /** Six-health skeletons retain six initially, but their daily increase uses a twenty-health reference. */
    public static double healthMultiplier(long day, double baseHealth, boolean skeleton) {
        if (!(baseHealth > 0D) || !Double.isFinite(baseHealth)) throw new IllegalArgumentException("Health basis must be finite and positive");
        double multiplier = healthMultiplier(day);
        if (!skeleton || baseHealth >= 20D) return multiplier;
        return Math.min(Double.MAX_VALUE, 1D + (multiplier - 1D) * (20D / baseHealth));
    }

    public static double damageMultiplier(long day) {
        return periodMultiplier(day, .0025D);
    }

    private static double periodMultiplier(long day, double dailyRate) {
        long elapsed = elapsedDays(day);
        double complete = Math.pow(1D + dailyRate * 32D, elapsed / 32L);
        return Math.min(Double.MAX_VALUE, complete * (1D + dailyRate * (elapsed % 32L)));
    }

    private static long elapsedDays(long day) { return Math.max(1L, day) - 1L; }
}
