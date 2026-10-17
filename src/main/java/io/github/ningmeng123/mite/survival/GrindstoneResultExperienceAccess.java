package io.github.ningmeng123.mite.survival;

import net.minecraft.world.level.Level;

/** Reuses the native grindstone reward calculation when preparing a result. @author NingMeng123 */
public interface GrindstoneResultExperienceAccess {
    int mite$rollExperience(Level level);
}
