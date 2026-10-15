package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/** js refuses all blood-period sleep progression; native respawn-setting remains intact. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class LunarSleepControl {
    private LunarSleepControl() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void start(CanPlayerSleepEvent event) {
        if (!LunarWeather.bloodPeriod(event.getLevel())) return;
        event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        event.getEntity().displayClientMessage(Component.translatable("message.mite.blood_moon_sleep_denied"), true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void continuing(CanContinueSleepingEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && LunarWeather.bloodPeriod(player.level()))
            event.setContinueSleeping(false);
    }
}
