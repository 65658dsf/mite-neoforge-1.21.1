package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.CraftingProgressPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        if (screen.width < 379 && screen instanceof RecipeUpdateListener recipeScreen
                && recipeScreen.getRecipeBookComponent().isVisible()) return;
        int width = 176;
        int x = screen.getGuiLeft();
        int y = Math.max(4, screen.getGuiTop() - 22);
        int percent = Math.clamp((int)(100L * state.progress() / Math.max(1, state.period())), 0, 100);
        var graphics = event.getGuiGraphics();
        boolean inventory = screen.getMenu() instanceof InventoryMenu;
        int arrowX = screen.getGuiLeft() + (inventory ? 135 : 89);
        int arrowY = screen.getGuiTop() + (inventory ? 29 : 35);
        int arrowWidth = inventory ? 16 : 24;
        int arrowHeight = inventory ? 13 : 16;
        int filled = Math.clamp((int)((long)arrowWidth * state.progress() / Math.max(1, state.period())), 0, arrowWidth);
        if (filled > 0) graphics.blitSprite(ResourceLocation.withDefaultNamespace("container/furnace/burn_progress"),
                arrowWidth, arrowHeight, 0, 0, arrowX, arrowY, filled, arrowHeight);
        graphics.fill(x, y, x + width, y + 20, 0xDD101010);
        graphics.fill(x + 2, y + 16, x + width - 2, y + 19, 0xFF343434);
        graphics.fill(x + 2, y + 16, x + 2 + (width - 4) * percent / 100, y + 19,
                state.hungry() ? 0xFFE09040 : 0xFF70BF44);
        var label = state.hungry() ? Component.translatable("screen.mite.crafting_paused", percent)
                : Component.translatable("screen.mite.crafting_progress", percent);
        graphics.drawCenteredString(Minecraft.getInstance().font, label, screen.width / 2, y + 4, 0xFFFFFF);
    }
}
