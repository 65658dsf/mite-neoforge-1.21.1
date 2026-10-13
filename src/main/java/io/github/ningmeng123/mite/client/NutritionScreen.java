package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.ningmeng123.mite.network.NutritionPayload;
import io.github.ningmeng123.mite.rules.NutritionRules;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** Read-only live R196 nutrition values; the inventory remains on the GUI stack. @author NingMeng123 */
public final class NutritionScreen extends Screen {
    private static final int PANEL_WIDTH = 286;
    private static final int PANEL_HEIGHT = 204;
    private static final int ROW_HEIGHT = 26;
    private static final int MICRONUTRIENT_LIMIT = NutritionRules.MICRONUTRIENT_MAX;
    private static final int RESISTANCE_LIMIT = NutritionRules.RESISTANCE_MAX;
    private float panelX;
    private float panelY;
    private float layoutScale = 1F;
    private boolean pendingMouseClose;
    @Nullable private NutritionPayload snapshot;

    private record Row(String key, ItemStack icon, int current, int maximum, int color) {
        Component name() { return Component.translatable("screen.mite.nutrition." + key); }
        int percent() { return (int) ((long) current * 100 / maximum); }
        String values() { return current + "/" + maximum + " (" + percent() + "%)"; }
    }

    public NutritionScreen() {
        super(Component.translatable("screen.mite.nutrition.title"));
    }

    @Override protected void init() {
        layoutScale = Math.min(1F, Math.min(Math.max(1, width - 12) / (float) PANEL_WIDTH,
                Math.max(1, height - 12) / (float) PANEL_HEIGHT));
        panelX = (width - PANEL_WIDTH * layoutScale) / 2F;
        panelY = (height - PANEL_HEIGHT * layoutScale) / 2F;
        pendingMouseClose = false;
        refreshSnapshot();
        Button back = addRenderableWidget(new BackButton());
        back.setTooltip(Tooltip.create(Component.translatable("screen.mite.nutrition.return_hint",
                minecraft.options.keyInventory.getTranslatedKeyMessage())));
    }

    @Override public void tick() {
        refreshSnapshot();
    }

    private void refreshSnapshot() {
        var state = NutritionPayload.latest();
        snapshot = minecraft.player != null && state != null && state.entityId() == minecraft.player.getId() ? state : null;
    }

    private List<Row> rows(NutritionPayload state) {
        int limit = Math.max(1, state.limit());
        return List.of(
                new Row("nutrition", new ItemStack(Items.BREAD), Math.clamp(state.nutrition(), 0, limit), limit, 0xD89846),
                new Row("protein", new ItemStack(Items.COOKED_BEEF), Math.clamp(state.protein(), 0, MICRONUTRIENT_LIMIT), MICRONUTRIENT_LIMIT, 0xE5B536),
                new Row("fat", new ItemStack(Items.MILK_BUCKET), Math.clamp(state.fat(), 0, MICRONUTRIENT_LIMIT), MICRONUTRIENT_LIMIT, 0xDFD198),
                new Row("plants", new ItemStack(Items.WHEAT), Math.clamp(state.plants(), 0, MICRONUTRIENT_LIMIT), MICRONUTRIENT_LIMIT, 0x70B743),
                new Row("resistance", new ItemStack(Items.SUGAR), Math.clamp(state.resistance(), 0, RESISTANCE_LIMIT), RESISTANCE_LIMIT, 0xCA6558)
        );
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x88000000);
        pushLayout(graphics);
        try {
            graphics.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, 0xFF000000);
            graphics.fill(1, 1, PANEL_WIDTH - 1, PANEL_HEIGHT - 1, 0xFF555555);
            graphics.fill(2, 2, PANEL_WIDTH - 2, PANEL_HEIGHT - 2, 0xFFC6C6C6);
            graphics.fill(1, 1, PANEL_WIDTH - 1, 2, 0xFFFFFFFF);
            graphics.fill(1, 1, 2, PANEL_HEIGHT - 1, 0xFFFFFFFF);
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshSnapshot();
        super.render(graphics, mouseX, mouseY, partialTick);
        Row hovered = null;
        double localMouseX = (mouseX - panelX) / layoutScale;
        double localMouseY = (mouseY - panelY) / layoutScale;
        pushLayout(graphics);
        try {
            drawFit(graphics, title, 12, 12, PANEL_WIDTH - 24, 0x404040, false);
            if (snapshot == null) {
                drawFit(graphics, Component.translatable("screen.mite.nutrition.waiting"),
                        12, PANEL_HEIGHT / 2 - 4, PANEL_WIDTH - 24, 0x404040, false);
            } else {
                List<Row> rows = rows(snapshot);
                for (int i = 0; i < rows.size(); i++) {
                    Row row = rows.get(i);
                    int rowY = 34 + i * ROW_HEIGHT;
                    graphics.renderItem(row.icon(), 12, rowY + 1);
                    int labelWidth = 92;
                    drawFit(graphics, row.name(), 34, rowY + 5, labelWidth - 36, 0x404040, false);
                    int barX = labelWidth;
                    int barWidth = PANEL_WIDTH - labelWidth - 12;
                    graphics.fill(barX, rowY + 2, barX + barWidth, rowY + 17, 0xFF373737);
                    graphics.fill(barX + 1, rowY + 3, barX + barWidth - 1, rowY + 16, 0xFF202020);
                    int filled = (int) ((long) (barWidth - 2) * row.current() / row.maximum());
                    if (filled > 0) graphics.fill(barX + 1, rowY + 3, barX + 1 + filled, rowY + 16, 0xFF000000 | row.color());
                    drawFit(graphics, Component.literal(row.values()), barX + 2, rowY + 5, barWidth - 4, 0xFFFFFF, true);
                    if (localMouseX >= 8 && localMouseX < PANEL_WIDTH - 8 && localMouseY >= rowY && localMouseY < rowY + 20)
                        hovered = row;
                }
            }
        } finally {
            graphics.pose().popPose();
        }
        // Native tooltip sizing and mouse coordinates use the actual screen space.
        if (hovered != null)
            graphics.renderTooltip(font, Component.translatable("screen.mite.nutrition.value", hovered.name(),
                    hovered.current(), hovered.maximum(), hovered.percent()), mouseX, mouseY);
    }

    private void pushLayout(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0F);
        graphics.pose().scale(layoutScale, layoutScale, 1F);
    }

    private void drawFit(GuiGraphics graphics, Component text, int x, int y, int availableWidth, int color, boolean centered) {
        if (availableWidth <= 0) return;
        int textWidth = font.width(text);
        float scale = Math.min(1F, availableWidth / (float) Math.max(1, textWidth));
        float offset = centered ? (availableWidth - textWidth * scale) / 2F : 0F;
        graphics.pose().pushPose();
        graphics.pose().translate(x + offset, y + (font.lineHeight * (1F - scale) / 2F), 0F);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, color, centered);
        graphics.pose().popPose();
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void onClose() {
        pendingMouseClose = false;
        // popGuiLayer restores the original InventoryScreen without removing its menu.
        if (minecraft.screen == this) minecraft.popGuiLayer();
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && pendingMouseClose) {
            pendingMouseClose = false;
            super.mouseReleased(mouseX, mouseY, button);
            // Pop only after this screen has consumed the release. An immediate pop
            // on press would let the inventory treat release as a carried-stack drop.
            onClose();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private final class BackButton extends Button {
        private BackButton() {
            super(Math.round(panelX + (PANEL_WIDTH - 100) / 2F * layoutScale),
                    Math.round(panelY + (PANEL_HEIGHT - 26) * layoutScale),
                    Math.max(1, Math.round(100 * layoutScale)), Math.max(1, Math.round(20 * layoutScale)),
                    Component.translatable("gui.back"), ignored -> NutritionScreen.this.onClose(), DEFAULT_NARRATION);
        }

        @Override public void onClick(double mouseX, double mouseY) {
            // AbstractWidget.mouseClicked already played the native button sound.
            pendingMouseClose = true;
        }

        @Override public void renderString(GuiGraphics graphics, Font font, int color) {
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + getWidth() / 2F,
                    getY() + (getHeight() - font.lineHeight * layoutScale) / 2F, 0F);
            graphics.pose().scale(layoutScale, layoutScale, 1F);
            graphics.drawCenteredString(font, getMessage(), 0, 0, color);
            graphics.pose().popPose();
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public Component getNarrationMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        var state = NutritionPayload.latest();
        var narration = title.copy();
        if (minecraft.player == null || state == null || state.entityId() != minecraft.player.getId())
            return narration.append(". ").append(Component.translatable("screen.mite.nutrition.waiting"));
        for (Row row : rows(state))
            narration.append(". ").append(Component.translatable("screen.mite.nutrition.value",
                    row.name(), row.current(), row.maximum(), row.percent()));
        return narration;
    }
}
