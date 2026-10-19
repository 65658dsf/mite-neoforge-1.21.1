package io.github.ningmeng123.mite.client.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Export location and server installation instructions. @author NingMeng123 */
public final class ConfigExportResultScreen extends Screen {
    private record TextLine(FormattedCharSequence text, int y, int color) {}
    private final Screen parent;
    private final Path file;
    private final String failure;
    private final List<TextLine> lines = new ArrayList<>();
    private int contentX;
    private int contentWidth;
    private int contentHeight;
    private int scroll;

    public ConfigExportResultScreen(Screen parent, Path file, String failure) {
        super(Component.translatable(file == null ? "mite.config.export.failed" : "mite.config.export.success"));
        this.parent = parent;
        this.file = file;
        this.failure = failure;
    }

    @Override protected void init() {
        lines.clear();
        contentWidth = Math.max(120, Math.min(440, width - 40));
        contentX = (width - contentWidth) / 2;
        int y = 0;
        if (file != null) {
            y = paragraph(Component.translatable("mite.config.export.saved"), y, 0x88FF88) + 8;
            y = paragraph(Component.literal(file.toString()), y, 0xFFFFFF) + 12;
            y = paragraph(Component.translatable("mite.config.export.install"), y, 0xCCCCCC) + 8;
            y = paragraph(Component.translatable("mite.config.export.saved_only"), y, 0xAAAAAA);
            int buttonWidth = Math.max(80, Math.min(150, (width - 28) / 2));
            addRenderableWidget(Button.builder(Component.translatable("mite.config.export.open_folder"),
                    button -> Util.getPlatform().openPath(file.getParent()))
                    .bounds(width / 2 - buttonWidth - 4, height - 54, buttonWidth, 20).build());
            var copy = addRenderableWidget(Button.builder(Component.translatable("mite.config.export.copy_path"), button -> {
                minecraft.keyboardHandler.setClipboard(file.toString());
                button.setMessage(Component.translatable("mite.config.export.path_copied"));
            }).bounds(width / 2 + 4, height - 54, buttonWidth, 20).build());
            copy.setTooltip(Tooltip.create(Component.literal(file.toString())));
        } else {
            y = paragraph(Component.translatable("mite.config.export.failure_hint"), y, 0xFF8888) + 8;
            y = paragraph(Component.literal(failure == null ? "" : failure), y, 0xCCCCCC);
        }
        contentHeight = y + 8;
        scroll = Math.clamp(scroll, 0, maximumScroll());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private int paragraph(Component text, int y, int color) {
        for (var line : font.split(text, contentWidth)) {
            lines.add(new TextLine(line, y, color));
            y += font.lineHeight + 3;
        }
        return y;
    }

    private int viewportTop() { return 45; }
    private int viewportBottom() { return height - 62; }
    private int maximumScroll() { return Math.max(0, contentHeight - (viewportBottom() - viewportTop())); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.enableScissor(contentX, viewportTop(), contentX + contentWidth, viewportBottom());
        for (var line : lines) graphics.drawString(font, line.text(), contentX, viewportTop() + line.y() - scroll, line.color());
        graphics.disableScissor();
        if (maximumScroll() > 0) {
            int trackHeight = Math.max(1, viewportBottom() - viewportTop());
            int thumbHeight = Math.max(12, trackHeight * trackHeight / contentHeight);
            int thumbTop = viewportTop() + (trackHeight - thumbHeight) * scroll / maximumScroll();
            graphics.fill(width - 12, viewportTop(), width - 9, viewportBottom(), 0x66333333);
            graphics.fill(width - 12, thumbTop, width - 9, thumbTop + thumbHeight, 0xFFAAAAAA);
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0D) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll = Math.clamp(scroll - (int)Math.signum(scrollY) * 28, 0, maximumScroll());
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
