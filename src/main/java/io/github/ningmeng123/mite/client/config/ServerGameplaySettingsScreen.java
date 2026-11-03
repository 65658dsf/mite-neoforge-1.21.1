package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.config.MiteConfig;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Drafts server gameplay values independently of the client's config file. @author NingMeng123 */
public final class ServerGameplaySettingsScreen extends Screen {
    private record Row(String path, ModConfigSpec.ValueSpec spec, int y) {
        String translation() {
            return spec.getTranslationKey() == null ? "mite.config." + path.substring(path.lastIndexOf('.') + 1)
                    : spec.getTranslationKey();
        }
        Component label() { return Component.translatable(translation()); }
    }
    private final Screen parent;
    private final Map<String, Object> values;
    private final Map<String, String> drafts = new HashMap<>();
    private final Set<String> invalid = new HashSet<>();
    private final List<String> paths;
    private final List<Row> rows = new ArrayList<>();
    private int page;
    private int pageCount;
    private boolean pending;
    private Component failure;
    private Button save;
    private Button status;

    public ServerGameplaySettingsScreen(Screen parent) {
        this(parent, null);
    }

    public ServerGameplaySettingsScreen(Screen parent, String selectedPath) {
        super(Component.translatable("mite.config.gameplay"));
        this.parent = parent;
        values = new HashMap<>(MiteConfigClient.gameplayValues());
        if (selectedPath != null) values.keySet().removeIf(path -> !path.equals(selectedPath));
        paths = values.keySet().stream().sorted().toList();
    }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        setFocused(null);
        clearWidgets();
        rows.clear();
        int count = Math.max(1, (height - 138) / 44);
        pageCount = Math.max(1, (paths.size() + count - 1) / count);
        page = Math.clamp(page, 0, pageCount - 1);
        for (int i = page * count; i < Math.min(paths.size(), (page + 1) * count); i++) {
            String path = paths.get(i);
            ModConfigSpec.ValueSpec spec = MiteConfig.SPEC.getSpec().get(path);
            Row row = new Row(path, spec, 58 + (i - page * count) * 44);
            rows.add(row);
            if (values.get(path) instanceof Boolean) {
                addRenderableWidget(Button.builder(toggle(path), button -> {
                    values.put(path, !Boolean.TRUE.equals(values.get(path)));
                    failure = null;
                    button.setMessage(toggle(path));
                    updateStatus();
                }).bounds(width - 104, row.y(), 92, 20).tooltip(Tooltip.create(
                        Component.translatable(row.translation() + ".tooltip"))).build()).active = !pending && MiteConfigClient.editable();
            } else {
                var field = addRenderableWidget(new EditBox(font, width - 104, row.y(), 92, 20, row.label()));
                field.setMaxLength(32);
                field.setValue(drafts.getOrDefault(path, number((Number) values.get(path))));
                field.setEditable(!pending && MiteConfigClient.editable());
                field.setTextColor(invalid.contains(path) ? 0xFFFF5555 : EditBox.DEFAULT_TEXT_COLOR);
                field.setTooltip(Tooltip.create(Component.translatable(row.translation() + ".tooltip")));
                field.setResponder(text -> {
                    drafts.put(path, text);
                    failure = null;
                    try {
                        double value = Double.parseDouble(text.trim());
                        if (!Double.isFinite(value) || !spec.test(value)) throw new NumberFormatException();
                        values.put(path, value);
                        invalid.remove(path);
                        field.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                    } catch (NumberFormatException exception) {
                        invalid.add(path);
                        field.setTextColor(0xFFFF5555);
                    }
                    updateStatus();
                });
            }
        }
        status = addRenderableWidget(Button.builder(Component.empty(), button -> {})
                .bounds(12, height - 78, width - 24, 18).build());
        int pageWidth = Math.min(75, (width - 36) / 4);
        addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuild(); })
                .bounds(12, height - 54, pageWidth, 20).build()).active = !pending && page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuild(); })
                .bounds(width - pageWidth - 12, height - 54, pageWidth, 20).build()).active = !pending && page + 1 < pageCount;
        int actionWidth = Math.min(100, (width - 32) / 3);
        int left = (width - actionWidth * 3 - 8) / 2;
        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), button -> save())
                .bounds(left, height - 28, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
            paths.forEach(path -> values.put(path, ((ModConfigSpec.ValueSpec) MiteConfig.SPEC.getSpec().get(path)).getDefault()));
            drafts.clear();
            invalid.clear();
            failure = null;
            rebuild();
        }).bounds(left + actionWidth + 4, height - 28, actionWidth, 20).build()).active = !pending && MiteConfigClient.editable();
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + actionWidth * 2 + 8, height - 28, actionWidth, 20).build());
        updateStatus();
    }

    private Component toggle(String path) { return Component.translatable(Boolean.TRUE.equals(values.get(path)) ? "options.on" : "options.off"); }

    private void updateStatus() {
        if (save == null || status == null) return;
        save.active = !pending && MiteConfigClient.editable() && invalid.isEmpty() && !paths.isEmpty();
        Component message = pending ? Component.translatable("mite.config.remote.saving") : failure != null ? failure
                : !MiteConfigClient.editable() ? Component.translatable("mite.config.server_owned")
                : !invalid.isEmpty() ? Component.translatable("mite.config.remote.invalid")
                : Component.translatable("mite.config.remote.scope");
        status.setMessage(message);
        status.setTooltip(Tooltip.create(message));
    }

    private void save() {
        if (pending || !MiteConfigClient.editable() || !invalid.isEmpty() || paths.isEmpty()) return;
        pending = true;
        failure = null;
        rebuild();
        MiteConfigClient.saveRemote("gameplay", Map.copyOf(values), error -> {
            pending = false;
            failure = error;
            if (minecraft.screen != this) return;
            if (error == null) minecraft.setScreen(parent);
            else rebuild();
        });
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 11, 0xFFFFFF);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("mite.config.remote.scope").getString(), width - 24), 12, 31, 0xBBBBBB);
        for (var row : rows) {
            graphics.drawString(font, font.plainSubstrByWidth(row.label().getString(), width - 128), 12, row.y() + 3, 0xFFFFFF);
            Component detail = row.spec().getRange() == null ? Component.translatable("mite.config.spawning_rules.boolean",
                    Component.translatable(Boolean.TRUE.equals(row.spec().getDefault()) ? "options.on" : "options.off"))
                    : Component.translatable("mite.config.remote.range", row.spec().getRange().toString(), number((Number) row.spec().getDefault()));
            graphics.drawString(font, font.plainSubstrByWidth(detail.getString(), width - 24), 12, row.y() + 23, 0x999999);
            if (mouseX >= 12 && mouseX < width - 112 && mouseY >= row.y() && mouseY < row.y() + 36)
                graphics.renderTooltip(font, font.split(Component.translatable(row.translation() + ".tooltip"), Math.max(100, width - 32)), mouseX, mouseY);
        }
        graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, paths.size()), width / 2, height - 48, 0xCCCCCC);
    }

    private static String number(Number value) { return BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros().toPlainString(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
