package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.OverworldAquiferAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** World ownership is supplied by the actual dimension's chunk cache. @author NingMeng123 */
@Mixin(RandomState.class)
public abstract class RandomStateAquiferMixin implements OverworldAquiferAccess {
    @Unique private volatile boolean mite$undergroundWaterReduction;

    @Override public boolean mite$undergroundWaterReduction() {
        return mite$undergroundWaterReduction;
    }

    @Override public void mite$setUndergroundWaterReduction(boolean enabled) {
        mite$undergroundWaterReduction = enabled;
    }
}
