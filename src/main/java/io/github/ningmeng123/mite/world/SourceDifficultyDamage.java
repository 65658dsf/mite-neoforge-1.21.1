package io.github.ningmeng123.mite.world;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.damagesource.IScalingFunction;

/** uf.attackEntityFrom removes difficulty scaling and applies one explosion multiplier. @author NingMeng123 */
public final class SourceDifficultyDamage {
    private SourceDifficultyDamage() {}

    /** Extended damage-scaling functions remain owned by their defining mod. */
    public static boolean usesStandardScaling(IScalingFunction function) {
        return function == IScalingFunction.DEFAULT;
    }

    /** Source uf:978; abr and R196ExplosionCalculator provide damage before this player-only adjustment. */
    public static float incoming(DamageSource source, float amount) {
        return source.is(DamageTypeTags.IS_EXPLOSION) ? amount * 1.5F : amount;
    }
}
