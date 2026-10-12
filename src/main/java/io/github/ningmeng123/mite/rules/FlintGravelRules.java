package io.github.ningmeng123.mite.rules;

import java.util.function.IntUnaryOperator;

/** R196's flint branch; unported mineral outcomes retain gravel. @author NingMeng123 */
public final class FlintGravelRules {
    public enum Drop { GRAVEL, FLINT_CHIP, FLINT }

    private FlintGravelRules() {
    }

    public static Drop roll(int fortune, IntUnaryOperator nextInt) {
        int denominator = 12 - 2 * Math.clamp(fortune, 0, 3);
        if (nextInt.applyAsInt(denominator) > 2) {
            return Drop.GRAVEL;
        }
        if (nextInt.applyAsInt(3) == 0) {
            // The complete ore/nugget branch is a later material-system feature.
            return Drop.GRAVEL;
        }
        return nextInt.applyAsInt(16) == 0 ? Drop.FLINT : Drop.FLINT_CHIP;
    }
}
