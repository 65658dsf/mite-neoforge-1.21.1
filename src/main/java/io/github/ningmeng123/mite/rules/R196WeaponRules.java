package io.github.ningmeng123.mite.rules;

/** wp, uh and uq formulas without Minecraft dependencies. @author NingMeng123 */
public final class R196WeaponRules {
    public static final float DEFAULT_MELEE_DAMAGE_FACTOR = 1F;
    private R196WeaponRules() {}
    /** Retains the source weapon bonus, with the player's base attack supplied by the entity. */
    public static float meleeDamageBonus(String material, float originalBonus) {
        return originalBonus;
    }
    /** Public compatibility hook for the shared material multiplier. */
    public static float meleeDamageFactor(String material) {
        return DEFAULT_MELEE_DAMAGE_FACTOR;
    }
    public static int sphereDamage(int subtype) {
        if (subtype < 0 || subtype > 4) throw new IllegalArgumentException("Unknown sphere subtype");
        return 1 + (subtype < 3 ? subtype + 1 : subtype);
    }
    public static boolean acidic(int subtype) { return subtype == 3 || subtype == 4; }
    public static float velocityMultiplier(String material) {
        return switch (material) {
            case "vibranium" -> 1.75F;
            case "mithril" -> 1.25F;
            case "ancient_metal" -> 1.1F;
            default -> 1F;
        };
    }
    /** Skills are suppressed in the confirmed R196 runtime; caller includes True Flight virtual levels. */
    public static float inaccuracy(int level, boolean inLiquid) {
        float wander = level < 0 ? 5F + (float)level * -.5F
                : (float)(.5 + 4.5 / Math.pow(.8F + (float)((long)level + 1) / 5F, 2));
        return inLiquid ? wander * 2F : wander;
    }
}
