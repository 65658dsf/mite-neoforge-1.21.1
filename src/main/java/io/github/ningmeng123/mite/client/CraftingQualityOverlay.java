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

/** Compact controls occupy unused space below the result, clear of recipe book and JEI. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class CraftingQualityOverlay {
    private static final int WIDTH = 46;
    private static final int HEIGHT = 14;
    private static final int ARROW_WIDTH = 10;
    private CraftingQualityOverlay() {}

    private record View(AbstractContainerScreen<?> screen, CraftingQualityPayload state) {
        int x() { return screen.getGuiLeft() + 126; }
        int y() { return screen.getGuiTop() + 59; }
        boolean hovered(double mouseX, double mouseY) {
            return mouseX >= x() && mouseX < x() + WIDTH && mouseY >= y() && mouseY < y() + HEIGHT;
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
        int arrowColor = view.canSelect() ? 0xFFFFFF : 0x777777;
        int textColor = view.state().affordable() ? 0xF1D38A : 0xFF7777;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF373737);
        graphics.fill(x + ARROW_WIDTH, y + 1, x + WIDTH - ARROW_WIDTH, y + HEIGHT - 1, 0xFF171717);
        if (view.canSelect() && view.hovered(event.getMouseX(), event.getMouseY())) {
            if (event.getMouseX() < x + ARROW_WIDTH) graphics.fill(x, y, x + ARROW_WIDTH, y + HEIGHT, 0xFF616161);
            if (event.getMouseX() >= x + WIDTH - ARROW_WIDTH) graphics.fill(x + WIDTH - ARROW_WIDTH, y, x + WIDTH, y + HEIGHT, 0xFF616161);
        }
        graphics.drawCenteredString(font, "<", x + ARROW_WIDTH / 2, y + 3, arrowColor);
        graphics.drawCenteredString(font, ">", x + WIDTH - ARROW_WIDTH / 2, y + 3, arrowColor);
        String label = qualityName(view.state().quality()).getString();
        int labelWidth = WIDTH - ARROW_WIDTH * 2 - 2;
        if (font.width(label) > labelWidth) label = font.plainSubstrByWidth(label, labelWidth - font.width("…")) + "…";
        graphics.drawCenteredString(font, label, x + WIDTH / 2, y + 3, textColor);
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
        int direction = event.getMouseX() < view.x() + ARROW_WIDTH ? -1
                : event.getMouseX() >= view.x() + WIDTH - ARROW_WIDTH ? 1 : 0;
        if (direction == 0) return;
        PacketDistributor.sendToServer(new SelectCraftingQualityPayload(view.state().containerId(), direction));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        CraftingQualityPayload.clearClientState();
    }
}
