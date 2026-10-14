package io.github.ningmeng123.mite.rules;

/** wp, uh and uq formulas without Minecraft dependencies. @author NingMeng123 */
public final class R196WeaponRules {
    private R196WeaponRules() {}
    public static int sphereDamage(int subtype) {
        if (subtype < 0 || subtype > 4) throw new IllegalArgumentException("Unknown sphere subtype");
        return 1 + (subtype < 3 ? subtype + 1 : subtype);
    }
    public static boolean acidic(int subtype) { return subtype == 3 || subtype == 4; }
    public static float velocityMultiplier(String material) {
        return material.equals("mithril") ? 1.25F : material.equals("ancient_metal") ? 1.1F : 1F;
    }
    /** Skills are suppressed in the confirmed R196 runtime; True Flight is not registered here yet. */
    public static float inaccuracy(int level, boolean inLiquid) {
        float wander = level < 0 ? 5F + (float)level * -.5F
                : (float)(.5 + 4.5 / Math.pow(.8F + (float)((long)level + 1) / 5F, 2));
        return inLiquid ? wander * 2F : wander;
    }
}
