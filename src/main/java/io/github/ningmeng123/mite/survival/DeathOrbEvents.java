package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Runs saved deliveries without retaining a dead player object. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class DeathOrbEvents {
    private DeathOrbEvents() {
    }

    @SubscribeEvent
    public static void afterLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            DeathOrbQueue queue = level.getDataStorage().get(DeathOrbQueue.FACTORY, DeathOrbQueue.DATA_NAME);
            if (queue != null) {
                queue.tick(level);
            }
        }
    }
}
