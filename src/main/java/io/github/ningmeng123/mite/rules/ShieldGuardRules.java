package io.github.ningmeng123.mite.rules;

/** Actual blocked damage and accepted blocks independently exhaust a shield guard. @author NingMeng123 */
public final class ShieldGuardRules {
    public static final double DAMAGE_LIMIT = 25D;
    public static final int BLOCK_LIMIT = 3;
    public static final int COOLDOWN_TICKS = 100;

    public record Result(double blockedDamage, int blocks, boolean broken) {}

    private ShieldGuardRules() {}

    public static Result advance(double blockedDamage, int blocks, float acceptedDamage) {
        if (!Double.isFinite(blockedDamage) || blockedDamage < 0D || blocks < 0)
            throw new IllegalArgumentException("Invalid shield guard progress");
        if (!Float.isFinite(acceptedDamage) || !(acceptedDamage > 0F)) return new Result(blockedDamage, blocks, false);
        double accumulated = blockedDamage + acceptedDamage;
        int acceptedBlocks = (int)Math.min((long)blocks + 1L, BLOCK_LIMIT);
        boolean broken = accumulated >= DAMAGE_LIMIT || acceptedBlocks >= BLOCK_LIMIT;
        return broken ? new Result(0D, 0, true) : new Result(accumulated, acceptedBlocks, false);
    }
}
