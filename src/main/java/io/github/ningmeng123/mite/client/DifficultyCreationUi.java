package io.github.ningmeng123.mite.client;

import net.minecraft.world.Difficulty;

/** Keep modern game-mode and command controls independent from the fixed difficulty. @author NingMeng123 */
public final class DifficultyCreationUi {
    private DifficultyCreationUi() {}

    public static boolean active(Object value, boolean requested) {
        return requested && !(value instanceof Difficulty);
    }
}
