package io.github.ningmeng123.mite.survival;

import net.minecraft.world.entity.player.Player;

/** The server's fixed point cost for the current grindstone inputs. @author NingMeng123 */
public interface GrindstoneExperienceAccess {
    /** A negative value means the result cost has not been synchronized yet. */
    int mite$experienceCost();

    boolean mite$canTake(Player player);
}
