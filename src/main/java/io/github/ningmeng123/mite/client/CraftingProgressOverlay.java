package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.CraftingProgressPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Render after the screen so its background never hides authoritative progress. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class CraftingProgressOverlay {
    private CraftingProgressOverlay() {}
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event) {
        var state = CraftingProgressPayload.latest();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof CraftingMenu || screen.getMenu() instanceof InventoryMenu)
                || state == null || !state.active() || screen.getMenu().containerId != state.containerId()
                || System.nanoTime() - CraftingProgressPayload.receivedAt() > 2_000_000_000L) return;
        int width = 176;
        int x = (screen.width - width) / 2;
        int y = Math.max(4, (screen.height - 166) / 2 - 22);
        int percent = Math.clamp((int)(100L * state.progress() / Math.max(1, state.period())), 0, 100);
        var graphics = event.getGuiGraphics();
        graphics.fill(x, y, x + width, y + 20, 0xDD101010);
        graphics.fill(x + 2, y + 16, x + width - 2, y + 19, 0xFF343434);
        graphics.fill(x + 2, y + 16, x + 2 + (width - 4) * percent / 100, y + 19,
                state.hungry() ? 0xFFE09040 : 0xFF70BF44);
        var label = state.hungry() ? Component.translatable("screen.mite.crafting_paused", percent)
                : Component.translatable("screen.mite.crafting_progress", percent);
        graphics.drawCenteredString(Minecraft.getInstance().font, label, screen.width / 2, y + 4, 0xFFFFFF);
    }
}
