package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.RespawnCountdownPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Keep modern death-screen controls; show the automatic timer alongside manual respawn. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class RespawnCountdownDisplay {
    private RespawnCountdownDisplay() {}
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        var mc = Minecraft.getInstance();
        var timer = RespawnCountdownPayload.latest();
        if (!(event.getScreen() instanceof DeathScreen) || mc.player == null || mc.level == null
                || mc.level.getLevelData().isHardcore() || timer == null || timer.entityId() != mc.player.getId() || timer.seconds() < 0) return;
        event.getGuiGraphics().drawCenteredString(mc.font, Component.translatable("screen.mite.auto_respawn", timer.seconds()),
                event.getScreen().width / 2, event.getScreen().height / 4 + 58, 0xFFFFFF);
    }
}
