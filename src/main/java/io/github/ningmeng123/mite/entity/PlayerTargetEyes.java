package io.github.ningmeng123.mite.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Server-authoritative eye state; clients render the synchronized flag, not Mob.target. @author NingMeng123 */
public interface PlayerTargetEyes {
    boolean hasRedEyes();

    static boolean tracksLivingPlayer(Mob mob) {
        return mob.isAlive() && mob.getTarget() instanceof Player player && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }
}
