package io.github.ningmeng123.mite.rules;

/** R196 uf melee/reach and og hand-injury arithmetic, independent of engine state. @author NingMeng123 */
public final class SourcePlayerCombatRules {
    private SourcePlayerCombatRules() {}

    public static float meleeLevelModifier(int level) {
        int boundedLevel = ProgressionRules.attributeLevel(level);
        return boundedLevel * (boundedLevel > 0 ? .005F : .02F);
    }
    public static float meleeLevelFactor(int level) { return 1F + meleeLevelModifier(level); }
    public static float primaryDamage(float criticalBase, int level, boolean silverVsUndead,
            boolean bluntVsSkeleton, float enchantmentDamage, boolean suspended) {
        float damage = criticalBase * meleeLevelFactor(level);
        if (silverVsUndead) damage *= 1.25F;
        if (bluntVsSkeleton) damage *= 2F;
        return suspendedDamage(damage + enchantmentDamage, suspended);
    }
    /** Applied after critical, material and enchantment contributions. */
    public static float suspendedDamage(float damage, boolean suspended) {
        return suspended && damage > 1F ? 1F + (damage - 1F) * .5F : damage;
    }
    public static boolean weakStrike(boolean protectedHand, float health, boolean foodEnergy, double baseDamage) {
        return !protectedHand && (health < 2F || !foodEnergy || baseDamage < 1D);
    }
    public static double heightAdvantage(double feetDifference) {
        return feetDifference < -.5D ? Math.max(-1D, (feetDifference + .5D) * .5D)
                : feetDifference > .5D ? Math.min(1D, (feetDifference - .5D) * .5D) : 0D;
    }
    public static double blockReach(boolean creative, double effectiveToolBonus) {
        return creative ? 5D : 2.75D + effectiveToolBonus;
    }
    public static double meleeReach(boolean creative, double feetDifference, double toolBonus, boolean fireball) {
        return creative ? 5D : 1.5D + heightAdvantage(feetDifference) + toolBonus + (fireball ? 2D : 0D);
    }
    public static double interactionReach(boolean creative, double feetDifference, boolean boat) {
        return creative ? 5D : boat ? 2.5D : 2.5D + heightAdvantage(feetDifference);
    }
    /** og has two successive if statements: a targeting cube overwrites its initial 100% with 12.5%. */
    public static float handInjuryChance(boolean cube, boolean fireCreature, boolean targetsAttacker, boolean weak) {
        float chance = cube ? 1F : 0F;
        if (fireCreature) chance = 1F;
        else if (targetsAttacker && !weak) chance = .125F;
        return chance;
    }
}
