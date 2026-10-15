package io.github.ningmeng123.mite.worldgen;

import net.minecraft.world.level.Level;

/** Keeps trial configuration and the cached spinning entity consistent with the current dimension. @author NingMeng123 */
public interface UnderworldTrialSpawnerAccess {
    void mite$normalizeUnderworld(Level level);
}
