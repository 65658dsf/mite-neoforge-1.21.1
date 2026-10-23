package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-authoritative experience levels and numeric ping in the Tab list. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class TabListStatus {
    private TabListStatus() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void formatName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Component name = event.getDisplayName();
        if (name == null) name = PlayerTeam.formatNameForTeam(player.getTeam(), player.getName());
        int latency = player.connection == null ? -1 : player.connection.latency();
        ChatFormatting latencyColor = latency < 0 ? ChatFormatting.GRAY
                : latency < 150 ? ChatFormatting.GREEN
                : latency < 300 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        // An empty root keeps team/name styles confined to the name, and copying
        // preserves components supplied by earlier Tab name event listeners.
        event.setDisplayName(Component.empty().append(name.copy())
                .append(Component.literal("  ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("gui.mite.tab.level", player.experienceLevel)
                        .withStyle(player.experienceLevel < 0 ? ChatFormatting.RED : ChatFormatting.GREEN))
                .append(Component.literal("  ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("gui.mite.tab.latency", latency < 0 ? "--" : Integer.toString(latency))
                        .withStyle(latencyColor)));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) refresh(event.getServer());
    }

    /** Vanilla only broadcasts changed names, to players in every dimension. */
    public static void refresh(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.refreshTabListName();
        }
    }
}
