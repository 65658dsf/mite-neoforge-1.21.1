package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.registry.MiteAttachments;
import java.util.UUID;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Shared server/client rules for owned death XP orbs. @author NingMeng123 */
public final class DeathExperienceOrbs {
    public static final int LIFETIME_TICKS = 24000;
    private static final double FOLLOW_DISTANCE_SQUARED = 8.0 * 8.0;

    private DeathExperienceOrbs() {
    }

    /**
     * Marks a newly constructed orb before it is added to the world. Vanilla's
     * initial age is zero; marking or loading never refreshes its remaining life.
     */
    public static void mark(ExperienceOrb orb, UUID owner) {
        orb.setData(MiteAttachments.DEATH_ORB_OWNER,
                new DeathOrbOwner(DeathOrbOwner.CURRENT_VERSION, owner));
    }

    public static boolean hasOwner(ExperienceOrb orb) {
        return orb.hasData(MiteAttachments.DEATH_ORB_OWNER);
    }

    /** Public orbs retain vanilla behavior, including its pickup event and mending. */
    public static boolean canPickup(ExperienceOrb orb, Player player) {
        DeathOrbOwner data = orb.getExistingDataOrNull(MiteAttachments.DEATH_ORB_OWNER);
        return data == null || data.owner().equals(player.getUUID())
                && player.level() == orb.level() && player.isAlive() && !player.isSpectator();
    }

    /** Resolves anew so respawn, logout and dimension changes cannot leave a stale target. */
    public static @Nullable Player findFollowingOwner(ExperienceOrb orb) {
        DeathOrbOwner data = orb.getExistingDataOrNull(MiteAttachments.DEATH_ORB_OWNER);
        if (data == null) {
            return null;
        }
        Player owner = orb.level().getPlayerByUUID(data.owner());
        return owner != null && canPickup(orb, owner) && owner.distanceToSqr(orb) <= FOLLOW_DISTANCE_SQUARED
                ? owner : null;
    }
}
