package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.MiningRules;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Applies the level factor to modern mining speed on both logical sides. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PlayerMiningEvents {
    private PlayerMiningEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (event.isCanceled() || player.isCreative() || player.isSpectator() || !(event.getNewSpeed() > 0.0F)) {
            return;
        }
        // Keep tool, attribute, environment and earlier event adjustments intact.
        event.setNewSpeed(event.getNewSpeed() * MiningRules.speedMultiplierForLevel(player.experienceLevel));
    }
}
