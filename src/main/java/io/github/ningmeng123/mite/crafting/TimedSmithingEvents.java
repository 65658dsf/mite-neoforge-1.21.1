package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Smithing uses the same logical-server lifecycle boundaries as timed crafting. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class TimedSmithingEvents {
    private TimedSmithingEvents() {}
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) TimedSmithingController.tick(player);
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) TimedSmithingController.damaged(player, event.getNewDamage());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TimedSmithingController.cancel(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TimedSmithingController.cancel(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TimedSmithingController.cancel(player);
    }
}
