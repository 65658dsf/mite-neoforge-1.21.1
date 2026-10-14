package io.github.ningmeng123.mite.client.mob;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.VisionDimming;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Source avj draws before the HUD, caps only visible alpha and fades the actual state by .01 per tick. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class VisionDimmingOverlay {
    private VisionDimmingOverlay() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.isPaused()) return;
        var data = minecraft.player.getPersistentData();
        float value = data.getFloat(VisionDimming.VALUE);
        data.putFloat(VisionDimming.VALUE, Float.isFinite(value) ? Math.max(0F,value-.01F) : 0F);
    }
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        float value = VisionDimming.value(minecraft.player) - delta.getGameTimeDeltaPartialTick(false) * .01F;
        if (value <= .01F) return;
        int alpha = Math.round(Math.min(value,.9F) * 255F);
        graphics.fill(0,0,graphics.guiWidth(),graphics.guiHeight(),alpha << 24);
    }
}
