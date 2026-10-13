package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.CraftingQualityPayload;
import io.github.ningmeng123.mite.network.SelectCraftingQualityPayload;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Controls sit below the result, clear of the inventory nutrition button and recipe book. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class CraftingQualityOverlay {
    private static final int WIDTH = 46;
    private static final int HEIGHT = 14;
    private static final int ARROW_WIDTH = 10;
    private CraftingQualityOverlay() {}

    private record View(AbstractContainerScreen<?> screen, CraftingQualityPayload state) {
        boolean inventory() { return screen.getMenu() instanceof InventoryMenu; }
        int x() { return screen.getGuiLeft() + (inventory() ? 136 : 126); }
        int y() { return screen.getGuiTop() + (inventory() ? 49 : 59); }
        int width() { return inventory() ? 36 : WIDTH; }
        int height() { return inventory() ? 11 : HEIGHT; }
        int arrowWidth() { return inventory() ? 8 : ARROW_WIDTH; }
        int textOffset() { return inventory() ? 1 : 3; }
        boolean hovered(double mouseX, double mouseY) {
            return mouseX >= x() && mouseX < x() + width() && mouseY >= y() && mouseY < y() + height();
        }
        boolean canSelect() { return !state.crafting() && state.minQuality() < state.maxQuality(); }
    }

    private static View view(Screen screen) {
        var state = CraftingQualityPayload.latest();
        if (!(screen instanceof AbstractContainerScreen<?> container)
                || !(container.getMenu() instanceof CraftingMenu || container.getMenu() instanceof InventoryMenu)
                || state == null || !state.visible() || container.getMenu().containerId != state.containerId()
                || state.minQuality() < 0 || state.maxQuality() > 7 || state.minQuality() > state.maxQuality()
                || state.quality() < state.minQuality() || state.quality() > state.maxQuality()
                || container.getMenu().getSlot(0).getItem().isEmpty()
                || System.nanoTime() - CraftingQualityPayload.receivedAt() > 2_000_000_000L) return null;
        // In narrow windows the expanded recipe book replaces the inventory screen entirely.
        if (screen.width < 379 && screen instanceof RecipeUpdateListener recipeScreen
                && recipeScreen.getRecipeBookComponent().isVisible()) return null;
        return new View(container, state);
    }

    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event) {
        View view = view(event.getScreen());
        if (view == null) return;
        var graphics = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int x = view.x();
        int y = view.y();
        int width = view.width();
        int height = view.height();
        int arrowWidth = view.arrowWidth();
        int textY = y + view.textOffset();
        int arrowColor = view.canSelect() ? 0xFFFFFF : 0x777777;
        int textColor = view.state().affordable() ? 0xF1D38A : 0xFF7777;
        graphics.fill(x, y, x + width, y + height, 0xFF373737);
        graphics.fill(x + arrowWidth, y + 1, x + width - arrowWidth, y + height - 1, 0xFF171717);
        if (view.canSelect() && view.hovered(event.getMouseX(), event.getMouseY())) {
            if (event.getMouseX() < x + arrowWidth) graphics.fill(x, y, x + arrowWidth, y + height, 0xFF616161);
            if (event.getMouseX() >= x + width - arrowWidth) graphics.fill(x + width - arrowWidth, y, x + width, y + height, 0xFF616161);
        }
        graphics.drawCenteredString(font, "<", x + arrowWidth / 2, textY, arrowColor);
        graphics.drawCenteredString(font, ">", x + width - arrowWidth / 2, textY, arrowColor);
        String label = qualityName(view.state().quality()).getString();
        int labelWidth = width - arrowWidth * 2 - 2;
        if (font.width(label) > labelWidth) label = font.plainSubstrByWidth(label, labelWidth - font.width("…")) + "…";
        graphics.drawCenteredString(font, label, x + width / 2, textY, textColor);
        if (view.hovered(event.getMouseX(), event.getMouseY())) {
            graphics.renderComponentTooltip(font, tooltip(view.state()), event.getMouseX(), event.getMouseY());
        }
    }

    private static List<Component> tooltip(CraftingQualityPayload state) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.mite.quality", qualityName(state.quality())).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("screen.mite.quality_range", qualityName(state.minQuality()), qualityName(state.maxQuality())));
        lines.add(Component.translatable("screen.mite.quality_cost", Math.max(state.experienceCost(), 0))
                .withStyle(state.affordable() ? ChatFormatting.GREEN : ChatFormatting.RED));
        lines.add(Component.translatable("screen.mite.quality_time", String.format(Locale.ROOT, "%.2f", Math.max(state.period(), 0) / 20.0F)));
        if (!state.affordable()) lines.add(Component.translatable("screen.mite.quality_unaffordable").withStyle(ChatFormatting.RED));
        if (state.minQuality() < state.maxQuality())
            lines.add(Component.translatable("screen.mite.quality_preview").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(state.crafting() ? "screen.mite.quality_running"
                : state.minQuality() == state.maxQuality() ? "screen.mite.quality_fixed" : "screen.mite.quality_select")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.mite.quality_click_modes").withStyle(ChatFormatting.GRAY));
        if (state.experienceCost() > 0) lines.add(Component.translatable("screen.mite.quality_paid_single").withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    private static Component qualityName(int quality) {
        return Component.translatable("quality.mite." + Quality.fromOrdinal(quality).name().toLowerCase(Locale.ROOT));
    }

    @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return;
        View view = view(event.getScreen());
        if (view == null || !view.hovered(event.getMouseX(), event.getMouseY())) return;
        // Consume only our small control strip, never a result-slot or JEI click.
        event.setCanceled(true);
        if (!view.canSelect()) return;
        int direction = event.getMouseX() < view.x() + view.arrowWidth() ? -1
                : event.getMouseX() >= view.x() + view.width() - view.arrowWidth() ? 1 : 0;
        if (direction == 0) return;
        PacketDistributor.sendToServer(new SelectCraftingQualityPayload(view.state().containerId(), direction));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        CraftingQualityPayload.clearClientState();
    }
}
