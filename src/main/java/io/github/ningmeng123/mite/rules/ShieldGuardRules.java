package io.github.ningmeng123.mite.rules;

/** Actual blocked damage and accepted blocks independently exhaust a shield guard. @author NingMeng123 */
public final class ShieldGuardRules {
    public static final double BASE_DAMAGE_LIMIT = 50D;
    public static final int BASE_BLOCK_LIMIT = 5;
    private static final int BLOCKS_PER_TIER = 5;
    private static final int MAX_TIER = 4;
    public static final int MAX_BLOCK_LIMIT = BASE_BLOCK_LIMIT + BLOCKS_PER_TIER * MAX_TIER;
    public static final double MAX_DAMAGE_LIMIT = damageLimit(MAX_TIER);

    public record Profile(double damageLimit, int blockLimit, int cooldownTicks) {
        public Profile {
            if (!Double.isFinite(damageLimit) || damageLimit <= 0D || blockLimit <= 0 || cooldownTicks <= 0)
                throw new IllegalArgumentException("Invalid shield guard profile");
        }
    }

    public record Result(double blockedDamage, int blocks, boolean broken) {}

    private ShieldGuardRules() {}

    /** Material tiers are independent of an individual shield's experience level or quality. */
    public static Profile profile(String material) {
        return switch (material == null ? "" : material) {
            case "iron" -> tier(1, 100);
            case "ancient_metal" -> tier(1, 90);
            case "mithril" -> tier(2, 80);
            case "adamantium" -> tier(3, 60);
            case "vibranium" -> tier(MAX_TIER, 30);
            default -> tier(0, 110);
        };
    }

    private static Profile tier(int tier, int cooldownTicks) {
        return new Profile(damageLimit(tier), BASE_BLOCK_LIMIT + BLOCKS_PER_TIER * tier, cooldownTicks);
    }

    private static double damageLimit(int tier) {
        // Calculate 1.8 as a ratio so each tier rounds to its decimal threshold only once.
        return BASE_DAMAGE_LIMIT * Math.pow(18D, tier) / Math.pow(10D, tier);
    }

    public static Result advance(Profile profile, double blockedDamage, int blocks, float acceptedDamage) {
        if (!Double.isFinite(blockedDamage) || blockedDamage < 0D || blocks < 0)
            throw new IllegalArgumentException("Invalid shield guard progress");
        if (!Float.isFinite(acceptedDamage) || !(acceptedDamage > 0F)) return new Result(blockedDamage, blocks, false);
        double accumulated = blockedDamage + acceptedDamage;
        int acceptedBlocks = (int)Math.min((long)blocks + 1L, profile.blockLimit());
        boolean broken = accumulated >= profile.damageLimit() || acceptedBlocks >= profile.blockLimit();
        return broken ? new Result(0D, 0, true) : new Result(accumulated, acceptedBlocks, false);
    }
}
