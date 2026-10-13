package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.MiningRules;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Applies food energy and level rules on both logical sides. @author NingMeng123 */
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
        // Modern tool/environment/event adjustments already produced this strength.
        event.setNewSpeed(MiningRules.adjustStrengthForSurvival(event.getNewSpeed(), player.experienceLevel,
                FoodConsumption.hasEnergy(player)));
    }
}
