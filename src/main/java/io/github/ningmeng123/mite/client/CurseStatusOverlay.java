package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Source axp shows an unknown curse until its restriction is encountered. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class CurseStatusOverlay {
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "textures/gui/curse_source_icons.png");
    private CurseStatusOverlay() {}

    @SubscribeEvent public static void inventory(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        var state = PlayerCurses.state(minecraft.player);
        if (!state.active()) return;
        boolean recipeVisible = screen.getRecipeBookComponent().isVisible();
        if (screen.width < 379 && recipeVisible) return;
        // Keep the source's left status panel where possible; the compact fallback leaves the
        // recipe book, native potion effects, crafting controls and nutrition button available.
        boolean full = screen.getGuiLeft() >= 128 && !recipeVisible;
        int x = screen.getGuiLeft() + (full ? -126 : 154);
        int y = screen.getGuiTop() + (full ? 0 : 62);
        int width = full ? 122 : 18;
        int height = full ? 32 : 18;
        var graphics = event.getGuiGraphics();
        graphics.fill(x, y, x + width, y + height, 0xDD202020);
        graphics.fill(x, y, x + width, y + 1, 0xFF8B8B8B);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF080808);
        graphics.blit(ICONS, x + (full ? 5 : 0), y + (full ? 7 : 0), 0, 198, 18, 18, 256, 256);
        Component subtitle = state.known() ? state.type().title().copy().withStyle(ChatFormatting.GOLD)
                : Component.translatable("curse.mite.unknown").withStyle(ChatFormatting.GRAY);
        if (full) {
            graphics.drawString(minecraft.font, Component.translatable("curse.mite.cursed"), x + 27, y + 6, 0xFFFFFF);
            String label = minecraft.font.plainSubstrByWidth(subtitle.getString(), width - 30);
            graphics.drawString(minecraft.font, label, x + 27, y + 17, state.known() ? 0xFFAA00 : 0xAAAAAA);
        }
        if (event.getMouseX() >= x && event.getMouseX() < x + width
                && event.getMouseY() >= y && event.getMouseY() < y + height) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("curse.mite.cursed"));
            tooltip.add(subtitle);
            if (state.known()) for (String line : state.type().description().getString().split("\\|"))
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(minecraft.font, tooltip, event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent public static void notification(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        int ticks = PlayerCurses.notificationTicks(minecraft.player);
        if (ticks <= 0) return;
        int alpha = Math.min(255, ticks * 256 / 10);
        if (alpha < 4) return;
        Component message = Component.translatable(PlayerCurses.notificationActive(minecraft.player)
                ? "curse.mite.notify" : "curse.mite.lifted").withStyle(ChatFormatting.DARK_PURPLE);
        int y = event.getGuiGraphics().guiHeight() - 59 + (minecraft.player.isCreative() ? 14 : 0);
        event.getGuiGraphics().drawCenteredString(minecraft.font, message,
                event.getGuiGraphics().guiWidth() / 2, y, (alpha << 24) | 0xFFFFFF);
    }
}
