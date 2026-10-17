package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GrindstoneEvent;

/** Charges the quoted grindstone fee instead of spawning XP orbs. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class GrindstoneExperienceEvents {
    private GrindstoneExperienceEvents() {}

    public static int experienceCost(int reward) {
        return (int)Math.min(Integer.MAX_VALUE, 2L * Math.max(0, reward));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTakeItem(GrindstoneEvent.OnTakeItem event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;

        int cost = experienceCost(event.getXp());
        event.setXp(0);
        // The result slot and quick-transfer entry point check the same fixed quote before extraction.
        if (cost > 0) player.giveExperiencePoints(-cost);
    }
}
