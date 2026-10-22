package io.github.ningmeng123.mite.worldgen;

/** Dimension-scoped underground-water policy shared by the noise state and its aquifers. @author NingMeng123 */
public interface OverworldAquiferAccess {
    boolean mite$undergroundWaterReduction();

    void mite$setUndergroundWaterReduction(boolean enabled);
}
