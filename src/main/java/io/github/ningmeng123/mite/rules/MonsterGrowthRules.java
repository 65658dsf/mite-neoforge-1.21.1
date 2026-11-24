package io.github.ningmeng123.mite.rules;

import java.util.LinkedHashMap;
import java.util.Map;

/** Editable attribute growth with a new real basis after each completed blood moon. @author NingMeng123 */
public final class MonsterGrowthRules {
    public static final String DEFAULT_FORMULA = "base + reference * rate * days";
    public static final double DEFAULT_MINIMUM_HEALTH_REFERENCE = 40D;
    public static final double MINIMUM_DAMAGE_REFERENCE = 20D;
    public static final Settings DEFAULTS = new Settings(20D, 0D, 20D, .005D, .005D,
            DEFAULT_FORMULA, DEFAULT_FORMULA, DEFAULT_FORMULA, DEFAULT_FORMULA, DEFAULT_MINIMUM_HEALTH_REFERENCE);
    private static final int MAX_REBASES = 4096;
    private static final int CACHE_SIZE = 512;
    private static final Map<Key, Result> RESULTS = new LinkedHashMap<>(32, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Result> eldest) { return size() > CACHE_SIZE; }
    };
    private static final Map<String, GrowthFormula.Compiled> FORMULAS = new LinkedHashMap<>(16, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, GrowthFormula.Compiled> eldest) { return size() > 64; }
    };

    private MonsterGrowthRules() {}

    /** Health base is the usual first growth reference. The minimum applies to bodies starting below that threshold. */
    public record Settings(double healthBase, double damageBase, double skeletonReference,
                           double healthRate, double damageRate, String healthFormula, String damageFormula,
                           String healthBloodMoonFormula, String damageBloodMoonFormula, double minimumHealthReference) {
        public Settings(double healthBase, double damageBase, double skeletonReference,
                        double healthRate, double damageRate, String healthFormula, String damageFormula,
                        String healthBloodMoonFormula, String damageBloodMoonFormula) {
            this(healthBase, damageBase, skeletonReference, healthRate, damageRate, healthFormula, damageFormula,
                    healthBloodMoonFormula, damageBloodMoonFormula, 0D);
        }

        /** Existing settings used the same expression for period growth and blood-moon settlement. */
        public Settings(double healthBase, double damageBase, double skeletonReference,
                        double healthRate, double damageRate, String healthFormula, String damageFormula) {
            this(healthBase, damageBase, skeletonReference, healthRate, damageRate, healthFormula, damageFormula,
                    healthFormula, damageFormula);
        }
    }

    /** The current period's basis and elapsed days are exactly the variables supplied to the formula. */
    public record Result(double value, double base, double reference, long days, long bloodMoons, boolean fallback) {}

    private enum Basis { HEALTH, INNATE_DAMAGE, OWNED_DAMAGE, RELATIVE_DAMAGE }

    private record Key(long day, double nativeBase, boolean skeleton, Settings settings, double multiplier, Basis basis) {}

    public static double health(long day, double nativeBase, boolean skeleton, Settings settings, double multiplier) {
        return healthPreview(day, nativeBase, skeleton, settings, multiplier).value();
    }

    public static double damage(long day, double nativeBase, Settings settings, double multiplier) {
        return damagePreview(day, nativeBase, settings, multiplier).value();
    }

    public static Result healthPreview(long day, double nativeBase, boolean skeleton, Settings settings, double multiplier) {
        return calculate(day, nativeBase, skeleton, settings, multiplier, Basis.HEALTH);
    }

    public static Result damagePreview(long day, double nativeBase, Settings settings, double multiplier) {
        return calculate(day, nativeBase, false, settings, multiplier, Basis.INNATE_DAMAGE);
    }

    /** Projectiles, explosions and contact hits use their own raw damage as the initial value. */
    public static Result ownedDamagePreview(long day, double amount, Settings settings, double multiplier) {
        return calculate(day, amount, false, settings, multiplier, Basis.OWNED_DAMAGE);
    }

    /** Legacy unit-based growth estimate for callers without an actual damage amount. */
    public static Result relativeDamagePreview(long day, Settings settings, double multiplier) {
        return calculate(day, 1D, false, settings, multiplier, Basis.RELATIVE_DAMAGE);
    }

    /** The ordinary formula may have rebased since this calendar settlement. */
    public static long lastBloodMoonSettlementDay(long day) {
        return lastBloodMoonBoundary(Math.max(1L, day) - 1L) + 1L;
    }

    /** Validate syntax and actual rebased executions, without allowing the runtime fallback to hide an error. */
    public static GrowthFormula.Validation validate(Settings settings) {
        return validate(settings, 1D, 1D);
    }

    /** Only the configured multiplier, neutral multiplier and zero-growth case are relevant to saving this revision. */
    public static GrowthFormula.Validation validate(Settings settings, double healthMultiplier, double damageMultiplier) {
        String invalid = numericProblem(settings);
        if (invalid != null) return new GrowthFormula.Validation(false, invalid);
        if (!Double.isFinite(healthMultiplier) || healthMultiplier < 0D
                || !Double.isFinite(damageMultiplier) || damageMultiplier < 0D)
            return new GrowthFormula.Validation(false, "增长倍率必须是非负有限数值");
        try {
            GrowthFormula.Compiled health = phaseFormula(settings.healthFormula(), true, false);
            GrowthFormula.Compiled healthBloodMoon = phaseFormula(settings.healthBloodMoonFormula(), true, true);
            GrowthFormula.Compiled damage = phaseFormula(settings.damageFormula(), false, false);
            GrowthFormula.Compiled damageBloodMoon = phaseFormula(settings.damageBloodMoonFormula(), false, true);
            long[] dates = {1L, 2L, 32L, 33L, 34L, 65L, 97L, 128L, 129L, 160L, 161L, 1025L};
            for (double multiplier : new double[]{0D, 1D, healthMultiplier}) {
                for (double nativeBase : new double[]{1D, 6D, 20D, 30D, 1024D}) {
                    double reference = settings.healthBase() > 0D ? settings.healthBase() : 20D;
                    double floor = growthFloor(nativeBase, settings.minimumHealthReference());
                    for (long day : dates)
                        strict(day, nativeBase, reference, settings.healthRate() * multiplier, floor, health, healthBloodMoon, true);
                }
            }
            for (double multiplier : new double[]{0D, 1D, damageMultiplier}) {
                // Owned attacks use their actual damage even when melee has a configured override.
                double[] initialValues = {settings.damageBase(), 1D, 3D, 19.95D, 20D, 2048D};
                for (double initial : initialValues) for (long day : dates) {
                    strict(day, initial, initial, settings.damageRate() * multiplier,
                            growthFloor(initial, MINIMUM_DAMAGE_REFERENCE), damage, damageBloodMoon, false);
                }
            }
            return new GrowthFormula.Validation(true, "公式语法、代表性属性及血月递推试算均通过");
        } catch (IllegalArgumentException exception) {
            return new GrowthFormula.Validation(false, exception.getMessage());
        }
    }

    private static String numericProblem(Settings settings) {
        if (settings == null) return "缺少怪物增长设置";
        if (!inRange(settings.healthBase(), 1024D)) return "首周期参考生命必须是 0 到 1024 之间的有限数值";
        if (!inRange(settings.minimumHealthReference(), 1024D)) return "最低生命增长基准必须是 0 到 1024 之间的有限数值";
        if (!inRange(settings.damageBase(), 2048D)) return "攻击基准必须是 0 到 2048 之间的有限数值";
        if (!inRange(settings.skeletonReference(), 1024D)) return "骷髅首周期参考生命必须是 0 到 1024 之间的有限数值";
        if (!inRange(settings.healthRate(), 1D) || !inRange(settings.damageRate(), 1D))
            return "每日增长率必须是 0 到 1 之间的有限数值（0.005 = 0.5%）";
        return null;
    }

    private static boolean inRange(double value, double maximum) {
        return Double.isFinite(value) && value >= 0D && value <= maximum;
    }

    private static double growthFloor(double initial, double minimum) {
        return initial > 0D && initial < minimum ? minimum : 0D;
    }

    private static Result calculate(long requestedDay, double nativeBase, boolean skeleton,
                                    Settings requestedSettings, double multiplier, Basis basis) {
        boolean health = basis == Basis.HEALTH;
        if (!Double.isFinite(nativeBase) || (health ? nativeBase <= 0D : nativeBase < 0D))
            throw new IllegalArgumentException("原生属性基准必须为有限数值，生命必须大于 0，攻击不能小于 0");
        long day = Math.max(1L, requestedDay);
        boolean unsafe = numericProblem(requestedSettings) != null || !Double.isFinite(multiplier) || multiplier < 0D;
        Settings settings = unsafe ? DEFAULTS : requestedSettings;
        double effectiveMultiplier = unsafe ? 1D : multiplier;
        Key key = new Key(day, nativeBase, skeleton, settings, effectiveMultiplier, basis);
        if (!unsafe) synchronized (RESULTS) {
            Result result = RESULTS.get(key);
            if (result != null) return result;
        }
        double initial = basis == Basis.INNATE_DAMAGE && settings.damageBase() > 0D ? settings.damageBase() : nativeBase;
        double reference = health ? (settings.healthBase() > 0D ? settings.healthBase() : 20D) : initial;
        double minimum = switch (basis) {
            case HEALTH -> settings.minimumHealthReference();
            case INNATE_DAMAGE, OWNED_DAMAGE -> MINIMUM_DAMAGE_REFERENCE;
            case RELATIVE_DAMAGE -> 0D;
        };
        double floor = growthFloor(initial, minimum);
        double rate = (health ? settings.healthRate() : settings.damageRate()) * effectiveMultiplier;
        String expression = health ? settings.healthFormula() : settings.damageFormula();
        String bloodMoonExpression = health ? settings.healthBloodMoonFormula() : settings.damageBloodMoonFormula();
        Result result;
        try {
            result = !unsafe && DEFAULT_FORMULA.equals(expression) && DEFAULT_FORMULA.equals(bloodMoonExpression)
                    ? linear(day, initial, reference, rate, floor, false)
                    : strict(day, initial, reference, rate, floor, phaseFormula(expression, health, false),
                            phaseFormula(bloodMoonExpression, health, true), health);
            if (unsafe) result = new Result(result.value(), result.base(), result.reference(), result.days(), result.bloodMoons(), true);
        } catch (IllegalArgumentException exception) {
            result = linear(day, initial, reference, rate, floor, true);
        }
        if (!unsafe) synchronized (RESULTS) { RESULTS.put(key, result); }
        return result;
    }

    private static GrowthFormula.Compiled formula(String expression) {
        synchronized (FORMULAS) {
            GrowthFormula.Compiled result = FORMULAS.get(expression);
            if (result == null) {
                result = GrowthFormula.compile(expression);
                FORMULAS.put(expression, result);
            }
            return result;
        }
    }

    private static GrowthFormula.Compiled phaseFormula(String expression, boolean health, boolean bloodMoon) {
        try {
            return formula(expression);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(phaseName(health, bloodMoon) + "：" + exception.getMessage(), exception);
        }
    }

    private static String phaseName(boolean health, boolean bloodMoon) {
        return (health ? "生命" : "攻击") + (bloodMoon ? "血月基准公式" : "周期内公式");
    }

    private static Result strict(long day, double initial, double firstReference, double rate, double floor,
                                 GrowthFormula.Compiled periodExpression, GrowthFormula.Compiled bloodMoonExpression,
                                 boolean health) {
        long elapsed = Math.max(1L, day) - 1L;
        long bloodMoons = completedBloodMoons(elapsed);
        if (bloodMoons > MAX_REBASES) throw new IllegalArgumentException("血月递推超过安全运行预算，运行时使用默认增长公式");
        boolean seekingFloor = floor > 0D;
        double base = initial, reference = seekingFloor ? floor : firstReference;
        long previous = 0L, count = 0L;
        // The 128th day is a blue moon: the period beginning after day 96 lasts until day 160 ends.
        for (long boundary = 32L; ; boundary = nextBloodMoonBoundary(boundary)) {
            if (seekingFloor) {
                for (long offset = previous; offset < Math.min(elapsed, boundary); offset++) {
                    double value = evaluate(periodExpression, base, initial, reference, rate, offset - previous,
                            offset + 1L, count, health, false);
                    if (!reachesFloor(value, initial, floor)) continue;
                    base = reference = Math.max(floor, value);
                    previous = offset;
                    seekingFloor = false;
                    break;
                }
            }
            if (boundary > elapsed) break;
            base = evaluate(bloodMoonExpression, base, initial, reference, rate, boundary - previous, boundary + 1L,
                    count, health, true);
            if (seekingFloor && reachesFloor(base, initial, floor)) {
                base = Math.max(floor, base);
                seekingFloor = false;
            }
            reference = seekingFloor ? floor : base;
            previous = boundary;
            count++;
        }
        long days = elapsed - previous;
        double value = evaluate(periodExpression, base, initial, reference, rate, days, day, count, health, false);
        return new Result(value, base, reference, days, count, false);
    }

    private static double evaluate(GrowthFormula.Compiled expression, double base, double initial, double reference,
                                   double rate, long days, long day, long bloodMoons, boolean health, boolean bloodMoon) {
        GrowthFormula.Context context = new GrowthFormula.Context(base, initial, reference, rate, days, day, bloodMoons);
        try {
            double value = expression.evaluate(context);
            if (health && value == 0D) throw new IllegalArgumentException("生命公式计算结果必须大于 0");
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(phaseName(health, bloodMoon) + "：" + exception.getMessage() + "（试算 day=" + day + ", days=" + days
                    + ", base=" + base + ", reference=" + reference + ", rate=" + rate + ", blood_moons=" + bloodMoons + "）", exception);
        }
    }

    /** Closed form for the shipped formula, so even Long.MAX_VALUE dates take bounded time. */
    private static Result linear(long day, double initial, double firstReference, double rate, double floor, boolean fallback) {
        long elapsed = Math.max(1L, day) - 1L;
        if (floor == 0D) return linearSettlements(elapsed, 0L, initial, firstReference, rate, fallback);

        long previous = lastBloodMoonBoundary(elapsed);
        long crossing = firstFloorCrossing(elapsed, initial, floor, rate);
        if (crossing > elapsed || crossing == elapsed && crossing != previous) {
            double base = linearValue(initial, floor, rate, previous);
            return new Result(linearValue(base, floor, rate, elapsed - previous), base, floor,
                    elapsed - previous, completedBloodMoons(elapsed), fallback);
        }
        double attained = Math.max(floor, linearValue(initial, floor, rate, crossing));
        return linearSettlements(elapsed, crossing, attained, attained, rate, fallback);
    }

    /** The linear floor accrues at a fixed pace. Binary search avoids overflow or division by a tiny rate. */
    private static long firstFloorCrossing(long elapsed, double initial, double floor, double rate) {
        if (!reachesFloor(linearValue(initial, floor, rate, elapsed), initial, floor)) return elapsed + 1L;
        long lower = 0L, upper = elapsed;
        while (upper - lower > 1L) {
            long day = lower + (upper - lower) / 2L;
            if (!reachesFloor(linearValue(initial, floor, rate, day), initial, floor)) lower = day;
            else upper = day;
        }
        return upper;
    }

    public static boolean reachesFloor(double value, double initial, double floor) {
        // Repeated settlements can round a mathematically exact crossing slightly below the threshold.
        return value >= floor || value > initial && floor - value <= 8D * Math.ulp(floor);
    }

    private static Result linearSettlements(long elapsed, long start, double base, double reference, double rate, boolean fallback) {
        long moons = completedBloodMoons(elapsed);
        long settlements = moons - completedBloodMoons(start);
        long previous = start;
        if (settlements > 0L) {
            long first = nextBloodMoonBoundary(start);
            long longPeriods = completedLongPeriods(elapsed) - completedLongPeriods(first);
            long shortPeriods = settlements - longPeriods - 1L;
            base = linearValue(base, reference, rate, first - start);
            base = multiply(base, settlementFactor(rate * 32D, shortPeriods));
            base = multiply(base, settlementFactor(rate * 64D, longPeriods));
            reference = base;
            previous = lastBloodMoonBoundary(elapsed);
        }
        long days = elapsed - previous;
        return new Result(linearValue(base, reference, rate, days), base, reference, days, moons, fallback);
    }

    private static double linearValue(double base, double reference, double rate, long days) {
        return add(base, multiply(reference, rate * days));
    }

    private static long lastBloodMoonBoundary(long elapsed) {
        long previous = elapsed / 32L * 32L;
        if (previous > 0L && previous % 128L == 0L) previous -= 32L;
        return previous;
    }

    private static long nextBloodMoonBoundary(long elapsed) {
        long next = (elapsed / 32L + 1L) * 32L;
        return next % 128L == 0L ? next + 32L : next;
    }

    private static long completedLongPeriods(long elapsed) { return elapsed < 32L ? 0L : (elapsed - 32L) / 128L; }
    private static long completedBloodMoons(long elapsed) { return elapsed / 32L - elapsed / 128L; }
    private static double settlementFactor(double increase, long periods) {
        return periods == 0L ? 1D : Math.min(Double.MAX_VALUE, Math.exp(Math.log1p(increase) * periods));
    }
    private static double multiply(double first, double second) { return first == 0D || second == 0D ? 0D : Math.min(Double.MAX_VALUE, first * second); }
    private static double add(double first, double second) { return Math.min(Double.MAX_VALUE, first + second); }
}
