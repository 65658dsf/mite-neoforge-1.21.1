package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Draws signed debt levels in R196's red, using the modern HUD layout. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class NegativeExperienceHud {
    private NegativeExperienceHud() {
    }

    @SubscribeEvent
    public static void afterExperienceLevel(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null || minecraft.options.hideGui
                || minecraft.player.experienceLevel >= 0 || !minecraft.gameMode.hasExperience()
                || minecraft.player.jumpableVehicle() != null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        String label = Integer.toString(minecraft.player.experienceLevel);
        int x = (graphics.guiWidth() - minecraft.font.width(label)) / 2;
        int y = graphics.guiHeight() - 35;
        graphics.drawString(minecraft.font, label, x + 1, y, 0, false);
        graphics.drawString(minecraft.font, label, x - 1, y, 0, false);
        graphics.drawString(minecraft.font, label, x, y + 1, 0, false);
        graphics.drawString(minecraft.font, label, x, y - 1, 0, false);
        graphics.drawString(minecraft.font, label, x, y, 0xFF1313, false);
    }
}
