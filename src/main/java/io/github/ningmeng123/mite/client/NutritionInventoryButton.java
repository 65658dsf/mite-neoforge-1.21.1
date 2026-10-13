package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Inventory-only native saturation icon opening a read-only stacked screen. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class NutritionInventoryButton {
    private static final Map<InventoryScreen, SaturationButton> BUTTONS = new WeakHashMap<>();
    private NutritionInventoryButton() {}

    @SubscribeEvent public static void add(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        SaturationButton button = new SaturationButton();
        BUTTONS.put(screen, button);
        update(screen, button);
        event.addListener(button);
    }

    @SubscribeEvent public static void reposition(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        SaturationButton button = BUTTONS.get(screen);
        if (button != null) update(screen, button);
    }

    private static void update(InventoryScreen screen, SaturationButton button) {
        // Recipe-book toggles update leftPos without reinitializing InventoryScreen.
        button.setPosition(screen.getGuiLeft() + 130, screen.getGuiTop() + 61);
        button.visible = !(screen.width < 379 && screen.getRecipeBookComponent().isVisible())
                && button.getX() >= 0 && button.getY() >= 0
                && button.getX() + button.getWidth() <= screen.width
                && button.getY() + button.getHeight() <= screen.height;
        button.active = button.visible && Minecraft.getInstance().player != null;
    }

    private static final class SaturationButton extends Button {
        private SaturationButton() {
            super(0, 0, 20, 18, Component.translatable("screen.mite.nutrition.open"), ignored -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) minecraft.pushGuiLayer(new NutritionScreen());
            }, DEFAULT_NARRATION);
            setTooltip(Tooltip.create(Component.translatable("screen.mite.nutrition.open")));
        }

        @Override public void renderString(GuiGraphics graphics, Font font, int color) {
            // Use the native mob-effect atlas, preserving resource-pack replacement.
            var icon = Minecraft.getInstance().getMobEffectTextures().get(MobEffects.SATURATION);
            graphics.blit(getX() + 3, getY() + 2, 0, 14, 14, icon);
        }
    }
}
