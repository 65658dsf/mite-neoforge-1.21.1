package io.github.ningmeng123.mite.rules;

/** R196 average-quality flint hatchet values and scaled durability costs. @author NingMeng123 */
public final class FlintToolRules {
    public static final int MAX_DAMAGE = 400;
    public static final float MINING_SPEED = 2.5F;

    private FlintToolRules() {
    }

    /** Preserves the original multiply/divide order and truncation before applying the minimum. */
    public static int blockWear(float hardness) {
        if (hardness == 0.0F) {
            return 0;
        }
        float baseDecay = 1.0F * 4.0F / 3.0F;
        float decay = 100.0F * baseDecay;
        return Math.max(Math.max((int) (hardness * decay), (int) (decay / 20.0F)), 1);
    }

    public static int attackWear() {
        return Math.max((int) (100.0F * (1.0F * 4.0F / 3.0F)), 1);
    }
}
