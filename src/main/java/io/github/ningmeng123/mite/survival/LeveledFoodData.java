package io.github.ningmeng123.mite.survival;

import net.minecraft.world.entity.player.Player;

/** FoodData context bridge; the owning player can change on respawn. @author NingMeng123 */
public interface LeveledFoodData {
    void mite$bindPlayer(Player player);

    void mite$clampToLimit();
}
