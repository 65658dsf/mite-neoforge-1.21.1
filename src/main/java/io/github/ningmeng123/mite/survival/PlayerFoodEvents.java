package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Applies limits before food is used or synchronized. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PlayerFoodEvents {
    private PlayerFoodEvents() {
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        applyLimit(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        applyLimit(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        applyLimit(event.getEntity());
    }

    @SubscribeEvent
    public static void beforePlayerTick(PlayerTickEvent.Pre event) {
        applyLimit(event.getEntity());
    }

    private static void applyLimit(Player player) {
        if (player instanceof ServerPlayer) {
            ((LeveledFoodData) player.getFoodData()).mite$clampToLimit();
        }
    }
}
