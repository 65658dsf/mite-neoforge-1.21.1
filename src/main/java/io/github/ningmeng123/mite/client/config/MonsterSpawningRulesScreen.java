package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings.Option;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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
import net.neoforged.fml.ModList;

/** Live natural-spawn controls with persistent, validated page drafts. @author NingMeng123 */
public final class MonsterSpawningRulesScreen extends Screen {
    private record Control(String key, String translationKey, Object defaultValue, double minimum,
                           double maximum, boolean wholeNumber, Option baseOption) {
        static Control base(Option option) {
            return new Control(option.key(), "mite.config.spawning_rules." + option.key(), option.defaultValue(),
                    option.minimum(), option.maximum(), option.wholeNumber(), option);
        }
        static Control addon(MiteExtensions.SpawningControl option) {
            Object value = option.defaultValue();
            if (!(value instanceof Boolean || value instanceof Number))
                throw new IllegalArgumentException("Unsupported spawning control: " + option.key());
            return new Control(option.key(), option.translationKey(), value, option.minimum(), option.maximum(),
                    value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long, null);
        }
        boolean toggle() { return defaultValue instanceof Boolean; }
        boolean preset() { return baseOption == Option.REFERENCE_RULES; }
        boolean valid(double value) {
            return Double.isFinite(value) && value >= minimum && value <= maximum && (!wholeNumber || value == Math.rint(value));
        }
        Object typed(double value) {
            if (defaultValue instanceof Byte) return (byte)value;
            if (defaultValue instanceof Short) return (short)value;
            if (defaultValue instanceof Integer) return (int)value;
            if (defaultValue instanceof Long) return (long)value;
            if (defaultValue instanceof Float) return (float)value;
            return value;
        }
    }
    private record VisibleRow(Control control, int y) {}
    private final Screen parent;
    private final boolean editable;
    private final boolean addonControls;
    private final List<Control> options;
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, String> drafts = new HashMap<>();
    private final Set<String> invalid = new HashSet<>();
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
        var supplied = List.copyOf(MiteExtensions.policy().spawningControls());
        addonControls = !supplied.isEmpty();
        if (addonControls) {
            options = supplied.stream().map(Control::addon).toList();
            var snapshot = MiteExtensions.policy().spawningControlValues();
            for (var option : options) values.put(option.key(), snapshot.getOrDefault(option.key(), option.defaultValue()));
        } else {
            options = Arrays.stream(Option.values()).filter(option -> option != Option.REFERENCE_RULES
                    || !ModList.get().isLoaded("modern_ite")).map(Control::base).toList();
            values.putAll(MiteConfig.monsterSpawningSettings().values());
        }
    }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        setFocused(null);
        clearWidgets();
        visible.clear();
        int rows = Math.max(1, (height - 140) / 44);
        pageCount = Math.max(1, (options.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pageCount - 1);
        for (int index = page * rows; index < Math.min(options.size(), (page + 1) * rows); index++) {
            Control option = options.get(index);
            int y = 60 + (index - page * rows) * 44;
            visible.add(new VisibleRow(option, y));
            String key = option.key();
            if (option.preset() || option.toggle()) {
                int controlWidth = option.preset() ? 144 : 92;
                var toggle = addRenderableWidget(Button.builder(toggleLabel(option), button -> {
                    values.put(key, option.preset() ? reference() ? 0D : 1D : !Boolean.TRUE.equals(values.get(key)));
                    failure = null;
                    if (option.preset()) {
                        drafts.clear();
                        invalid.clear();
                        rebuild();
                    } else {
                        button.setMessage(toggleLabel(option));
                        updateStatus();
                    }
                }).bounds(width - controlWidth - 12, y - 2, controlWidth, 20).build());
                toggle.active = editable;
                toggle.setTooltip(Tooltip.create(description(option)));
                continue;
            }
            var box = addRenderableWidget(new EditBox(font, width - 104, y - 2, 92, 20, label(option)));
            box.setMaxLength(32);
            box.setValue(drafts.getOrDefault(key, number(((Number)values.getOrDefault(key, option.defaultValue())).doubleValue())));
            boolean iteOwnsCap = !addonControls && ModList.get().isLoaded("modern_ite") && switch (option.baseOption()) {
                case BASE_CAP, BLOOD_CAP, DAILY_CAP_INCREASE, MAXIMUM_CAP -> true;
                default -> false;
            };
            box.setEditable(editable && (addonControls || !reference() || MiteExtensions.policy().configurableHostileSpawningSettings()) && !iteOwnsCap);
            box.setTextColor(invalid.contains(key) ? 0xFFFF5555 : EditBox.DEFAULT_TEXT_COLOR);
            box.setTooltip(Tooltip.create(description(option)));
            box.setResponder(text -> {
                drafts.put(key, text);
                failure = null;
                try {
                    double value = Double.parseDouble(text.trim());
                    if (!option.valid(value)) throw new NumberFormatException();
                    values.put(key, option.typed(value));
                    invalid.remove(key);
                    box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                } catch (NumberFormatException exception) {
                    invalid.add(key);
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
            for (var option : options) values.put(option.key(), option.defaultValue());
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

    private Component toggleLabel(Control option) {
        return Component.translatable(option.preset() ? reference() ? "mite.config.spawning_rules.reference_mode"
                : "mite.config.spawning_rules.custom_mode" : Boolean.TRUE.equals(values.get(option.key())) ? "options.on" : "options.off");
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

    private boolean reference() {
        return MiteExtensions.policy().referenceSpawningSettings(((Number)values.getOrDefault(Option.REFERENCE_RULES.key(), 1D)).doubleValue() == 1D);
    }

    private void save() {
        if (!MiteConfigClient.editable() || !invalid.isEmpty()) return;
        try {
            boolean saved;
            if (addonControls) {
                saved = MiteExtensions.policy().saveSpawningControls(Map.copyOf(values));
            } else {
                var numeric = new HashMap<String, Double>();
                values.forEach((key, value) -> numeric.put(key, ((Number)value).doubleValue()));
                saved = MiteConfig.saveMonsterSpawningSettings(Map.copyOf(numeric));
            }
            if (!saved) {
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

    private static Component label(Control option) { return Component.translatable(option.translationKey()); }

    private static Component description(Control option) {
        return label(option).copy().append("\n").append(Component.translatable(option.translationKey() + ".tooltip"));
    }

    private Component scopeHint() {
        return Component.translatable(addonControls ? "mite.config.spawning_rules.addon_hint" : ModList.get().isLoaded("modern_ite")
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
            Control option = row.control();
            graphics.drawString(font, font.plainSubstrByWidth(label(option).getString(), width - (option.preset() ? 180 : 128)), 12, row.y() + 3, 0xFFFFFF);
            Component range = option.preset() ? Component.translatable("mite.config.spawning_rules.reference_rules.tooltip")
                    : option.toggle() ? Component.translatable("mite.config.spawning_rules.boolean", Component.translatable(Boolean.TRUE.equals(option.defaultValue()) ? "options.on" : "options.off"))
                    : Component.translatable("mite.config.spawning_rules.range", number(option.minimum()), number(option.maximum()),
                    number(((Number)option.defaultValue()).doubleValue()), Component.translatable(option.wholeNumber()
                            ? "mite.config.spawning_rules.integer" : "mite.config.spawning_rules.decimal"));
            graphics.drawString(font, font.plainSubstrByWidth(range.getString(), width - 24), 12, row.y() + 23, 0x999999);
        }
        graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, options.size()),
                width / 2, height - 48, 0xCCCCCC);
        for (var row : visible) if (mouseX >= 12 && mouseX < width - 112 && mouseY >= row.y() && mouseY < row.y() + 36)
            graphics.renderTooltip(font, font.split(description(row.control()), Math.max(100, width - 32)), mouseX, mouseY);
    }

    private static String number(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
