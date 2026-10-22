package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings.Option;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
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
import net.neoforged.fml.ModList;

/** Live natural-spawn controls with persistent, validated page drafts. @author NingMeng123 */
public final class MonsterSpawningRulesScreen extends Screen {
    private record VisibleRow(Option option, int y) {}
    private final Screen parent;
    private final boolean editable;
    private final Map<String, Double> values = new HashMap<>();
    private final Map<Option, String> drafts = new EnumMap<>(Option.class);
    private final Set<Option> invalid = EnumSet.noneOf(Option.class);
    private final List<VisibleRow> visible = new ArrayList<>();
    private int page;
    private int pageCount;
    private Button save;
    private Button status;
    private Component failure;

    public MonsterSpawningRulesScreen(Screen parent) {
        super(Component.translatable("mite.config.spawning_rules.title"));
        this.parent = parent;
        editable = MiteConfigClient.editable();
        values.putAll(MiteConfig.monsterSpawningSettings().values());
    }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        clearWidgets();
        visible.clear();
        int rows = Math.max(1, (height - 140) / 44);
        var options = Option.values();
        pageCount = Math.max(1, (options.length + rows - 1) / rows);
        page = Math.clamp(page, 0, pageCount - 1);
        for (int index = page * rows; index < Math.min(options.length, (page + 1) * rows); index++) {
            Option option = options[index];
            int y = 60 + (index - page * rows) * 44;
            visible.add(new VisibleRow(option, y));
            var box = addRenderableWidget(new EditBox(font, width - 104, y - 2, 92, 20, label(option)));
            box.setMaxLength(32);
            box.setValue(drafts.getOrDefault(option, number(values.getOrDefault(option.key(), option.defaultValue()))));
            box.setEditable(editable);
            box.setTextColor(invalid.contains(option) ? 0xFFFF5555 : EditBox.DEFAULT_TEXT_COLOR);
            box.setTooltip(Tooltip.create(description(option)));
            box.setResponder(text -> {
                drafts.put(option, text);
                failure = null;
                try {
                    double value = Double.parseDouble(text.trim());
                    if (!option.valid(value)) throw new NumberFormatException();
                    values.put(option.key(), value);
                    invalid.remove(option);
                    box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                } catch (NumberFormatException exception) {
                    invalid.add(option);
                    box.setTextColor(0xFFFF5555);
                }
                updateStatus();
            });
        }
        status = addRenderableWidget(Button.builder(Component.empty(), button -> {})
                .bounds(12, height - 78, width - 24, 18).build());
        int pageWidth = Math.min(75, (width - 36) / 4);
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuild(); })
                .bounds(12, height - 54, pageWidth, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuild(); })
                .bounds(width - pageWidth - 12, height - 54, pageWidth, 20).build());
        next.active = page + 1 < pageCount;
        int actionWidth = Math.min(100, (width - 32) / 3);
        int actionLeft = (width - actionWidth * 3 - 8) / 2;
        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), button -> save())
                .bounds(actionLeft, height - 28, actionWidth, 20).build());
        var reset = addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
            values.clear();
            values.putAll(MonsterSpawningSettings.DEFAULTS.values());
            drafts.clear();
            invalid.clear();
            failure = null;
            rebuild();
        }).bounds(actionLeft + actionWidth + 4, height - 28, actionWidth, 20).build());
        reset.active = editable;
        reset.setTooltip(Tooltip.create(Component.translatable("mite.config.spawning_rules.reset.tooltip")));
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(actionLeft + actionWidth * 2 + 8, height - 28, actionWidth, 20).build());
        updateStatus();
    }

    private void updateStatus() {
        if (save == null || status == null) return;
        save.active = editable && invalid.isEmpty() && failure == null;
        Component message = failure != null ? failure : !editable ? Component.translatable("mite.config.server_owned")
                : !invalid.isEmpty() ? Component.translatable("mite.config.spawning_rules.invalid", invalid.size())
                : Component.translatable("mite.config.spawning_rules.live");
        status.setMessage(message);
        status.setTooltip(Tooltip.create(message.copy().append("\n").append(scopeHint())));
        save.setTooltip(Tooltip.create(message));
    }

    private void save() {
        if (!MiteConfigClient.editable() || !invalid.isEmpty()) return;
        try {
            if (!MiteConfig.saveMonsterSpawningSettings(Map.copyOf(values))) {
                failure = Component.translatable("mite.config.spawning_rules.invalid", 1);
                updateStatus();
                return;
            }
            minecraft.setScreen(parent);
        } catch (RuntimeException exception) {
            failure = Component.literal(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            updateStatus();
        }
    }

    private static Component label(Option option) {
        return Component.translatable("mite.config.spawning_rules." + option.key());
    }

    private static Component description(Option option) {
        return label(option).copy().append("\n").append(Component.translatable("mite.config.spawning_rules." + option.key() + ".tooltip"));
    }

    private static Component scopeHint() {
        return Component.translatable(ModList.get().isLoaded("modern_ite")
                ? "mite.config.spawning_rules.ite_hint" : "mite.config.spawning_rules.hint");
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 11, 0xFFFFFF);
        int hintY = 31;
        for (var line : font.split(scopeHint(), width - 24).stream().limit(2).toList()) {
            graphics.drawString(font, line, 12, hintY, 0xBBBBBB);
            hintY += font.lineHeight + 1;
        }
        for (var row : visible) {
            Option option = row.option();
            graphics.drawString(font, font.plainSubstrByWidth(label(option).getString(), width - 128), 12, row.y() + 3, 0xFFFFFF);
            var range = Component.translatable("mite.config.spawning_rules.range", number(option.minimum()), number(option.maximum()),
                    number(option.defaultValue()), Component.translatable(option.wholeNumber()
                            ? "mite.config.spawning_rules.integer" : "mite.config.spawning_rules.decimal"));
            graphics.drawString(font, font.plainSubstrByWidth(range.getString(), width - 24), 12, row.y() + 23, 0x999999);
        }
        graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, Option.values().length),
                width / 2, height - 48, 0xCCCCCC);
        for (var row : visible) if (mouseX >= 12 && mouseX < width - 112 && mouseY >= row.y() && mouseY < row.y() + 36)
            graphics.renderTooltip(font, description(row.option()), mouseX, mouseY);
    }

    private static String number(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
