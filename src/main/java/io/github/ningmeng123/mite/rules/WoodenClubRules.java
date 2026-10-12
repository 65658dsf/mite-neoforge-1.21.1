package io.github.ningmeng123.mite.rules;

/** R196 average-quality Wooden Club constants, independent of game state. @author NingMeng123 */
public final class WoodenClubRules {
    public static final int MAX_DAMAGE = 400;
    public static final float MINING_SPEED = 2.0F;
    public static final int ATTACK_WEAR = 25;
    public static final int ENCHANTABILITY = 10;

    private WoodenClubRules() {}

    /** ItemTool: truncate hardness * 100 * decay, then apply the minimum wear. */
    public static int blockWear(float hardness) {
        if (hardness == 0.0F) return 0;
        float decay = 100.0F * 0.25F;
        return Math.max(Math.max((int) (hardness * decay), (int) (decay / 20.0F)), 1);
    }
}
