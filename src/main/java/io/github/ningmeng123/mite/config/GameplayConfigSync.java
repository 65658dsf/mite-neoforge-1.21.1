package io.github.ningmeng123.mite.config;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.GameplayConfigPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Only the server publishes gameplay settings; clients have no write-back channel. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class GameplayConfigSync {
    private GameplayConfigSync() {}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) GameplayConfigPayload.send(player);
    }
    public static void broadcast() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> server.getPlayerList().getPlayers().forEach(player -> {
            // Rebuild seeded clues after a reload so the displayed candidate matches the next click.
            if (player.containerMenu instanceof EnchantmentMenu menu) {
                menu.slotsChanged(menu.getSlot(0).container);
                menu.broadcastChanges();
            }
            GameplayConfigPayload.send(player);
        }));
    }
}
