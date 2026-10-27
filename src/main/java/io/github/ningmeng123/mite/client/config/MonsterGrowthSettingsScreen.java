package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Editable arithmetic with the same validation and blood-moon simulation used by the server. @author NingMeng123 */
public final class MonsterGrowthSettingsScreen extends Screen {
    private record ScrollingWidget(AbstractWidget widget, int y) {}
    private record TextLine(FormattedCharSequence text, int y, int color) {}
    private final Screen parent;
    private final boolean editable;
    private final Map<String, String> drafts = new HashMap<>();
    private final List<ScrollingWidget> scrollingWidgets = new ArrayList<>();
    private final List<TextLine> lines = new ArrayList<>();
    private final List<TextLine> previewLines = new ArrayList<>();
    private final Map<String, EditBox> fields = new HashMap<>();
    private boolean health = true;
    private boolean skeleton;
    private int scroll;
    private int contentHeight;
    private int previewY;
    private int panelX;
    private int panelWidth;
    private Button save;
    private Button status;
    private MonsterGrowthRules.Settings validSettings;
    private MonsterGrowthRules.Result preview;
    private MonsterGrowthRules.Result lastBloodMoonInput;
    private long previewDay;
    private double previewInitial;
    private double previewRate;
    private Component failure;

    public MonsterGrowthSettingsScreen(Screen parent) {
        super(Component.translatable("mite.config.monster_formulas"));
        this.parent = parent;
        editable = MiteConfigClient.editable();
        copySettings(MiteConfig.monsterGrowthSettings());
        drafts.put("health_native", "20");
        var level = net.minecraft.client.Minecraft.getInstance().level;
        drafts.put("preview_day", Long.toString(level == null ? 1L : Math.max(1L, Math.floorDiv(level.getDayTime(), 24000L) + 1L)));
    }

    private void copySettings(MonsterGrowthRules.Settings settings) {
        drafts.put("health_base", number(settings.healthBase()));
        drafts.put("damage_base", number(settings.damageBase()));
        drafts.put("skeleton_reference", number(settings.skeletonReference()));
        drafts.put("health_daily_percent", number(settings.healthRate() * 100D));
        drafts.put("damage_daily_percent", number(settings.damageRate() * 100D));
        drafts.put("health_formula", settings.healthFormula());
        drafts.put("damage_formula", settings.damageFormula());
        drafts.put("health_blood_moon_formula", settings.healthBloodMoonFormula());
        drafts.put("damage_blood_moon_formula", settings.damageBloodMoonFormula());
    }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        clearWidgets();
        scrollingWidgets.clear();
        fields.clear();
        lines.clear();
        panelWidth = Math.max(200, Math.min(600, width - 40));
        panelX = (width - panelWidth) / 2;
        int tabWidth = Math.min(150, (width - 28) / 2);
        var healthTab = addRenderableWidget(Button.builder(Component.translatable("mite.config.health_tab"), button -> switchTab(true))
                .bounds(width / 2 - tabWidth - 2, 27, tabWidth, 20).build());
        healthTab.active = !health;
        var damageTab = addRenderableWidget(Button.builder(Component.translatable("mite.config.damage_tab"), button -> switchTab(false))
                .bounds(width / 2 + 2, 27, tabWidth, 20).build());
        damageTab.active = health;
        int y = paragraph(health ? "mite.config.growth_baseline_hint" : "mite.config.fixed_damage_hint", 0, 0xCCCCCC) + 8;
        String prefix = health ? "health" : "damage";
        y = field(prefix + "_base", y, 32, editable, value -> validateDraft());
        if (health) {
            y = field("health_daily_percent", y, 32, editable, value -> validateDraft());
            y = field("skeleton_reference", y, 32, editable, value -> validateDraft());
            y = paragraph("mite.config.growth_recurrence_heading", y + 3, 0xFFFFFF) + 4;
            y = paragraph("mite.config.health_recurrence_initial", y, 0xCCCCCC) + 4;
            y = paragraph("mite.config.growth_recurrence_blood", y, 0xCCCCCC) + 4;
            y = field("health_blood_moon_formula", y, 512, editable, value -> validateDraft());
            y = paragraph("mite.config.growth_recurrence_period", y, 0xCCCCCC) + 4;
            y = field("health_formula", y, 512, editable, value -> validateDraft());
            y = paragraph("mite.config.growth_recurrence_calendar", y, 0xCCCCCC) + 4;
            y = paragraph("mite.config.growth_variables", y + 3, 0xAAAAAA) + 4;
            y = paragraph("mite.config.growth_functions", y, 0xAAAAAA) + 10;
            y = paragraph("mite.config.growth_preview_heading", y, 0xFFFFFF) + 5;
            y = field("health_native", y, 32, true, value -> validateDraft());
            y = field("preview_day", y, 19, true, value -> validateDraft());
            var toggle = Button.builder(skeletonLabel(), button -> {
                skeleton = !skeleton;
                button.setMessage(skeletonLabel());
                validateDraft();
            }).bounds(panelX, 0, panelWidth, 20).build();
            toggle.setTooltip(Tooltip.create(Component.translatable("mite.config.preview_skeleton.tooltip")));
            addScrollingWidget(toggle, y);
            y += 28;
        }
        previewY = y;
        contentHeight = y;
        int actionWidth = Math.min(100, (width - 32) / 3);
        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), button -> save())
                .bounds(width / 2 - actionWidth * 3 / 2 - 4, height - 28, actionWidth, 20).build());
        var reset = addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
            copySettings(MonsterGrowthRules.DEFAULTS);
            rebuild();
        }).bounds(width / 2 - actionWidth / 2, height - 28, actionWidth, 20).build());
        reset.active = editable;
        reset.setTooltip(Tooltip.create(Component.translatable("mite.config.growth_reset.tooltip")));
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + actionWidth / 2 + 4, height - 28, actionWidth, 20).build());
        // A compact status button makes the complete validation error available as a tooltip.
        status = addRenderableWidget(Button.builder(Component.empty(), button -> {})
                .bounds(12, height - 51, Math.max(100, width - 24), 18).build());
        validateDraft();
        updateViewport();
    }

    private void switchTab(boolean showHealth) {
        health = showHealth;
        scroll = 0;
        rebuild();
    }

    private int field(String key, int y, int length, boolean canEdit, Consumer<String> changed) {
        y = paragraph("mite.config." + key, y, 0xFFFFFF);
        var box = new EditBox(font, panelX, 0, panelWidth, 20, Component.translatable("mite.config." + key));
        box.setMaxLength(length);
        box.setValue(drafts.getOrDefault(key, ""));
        box.setEditable(canEdit);
        box.setTooltip(Tooltip.create(Component.translatable("mite.config." + key + ".tooltip")));
        box.setResponder(value -> {
            drafts.put(key, value);
            changed.accept(value);
        });
        fields.put(key, box);
        addScrollingWidget(box, y + 2);
        return y + 30;
    }

    private void addScrollingWidget(AbstractWidget widget, int y) {
        addRenderableWidget(widget);
        scrollingWidgets.add(new ScrollingWidget(widget, y));
    }

    private int paragraph(String key, int y, int color) {
        for (var line : font.split(Component.translatable(key), panelWidth)) {
            lines.add(new TextLine(line, y, color));
            y += font.lineHeight + 2;
        }
        return y;
    }

    private void validateDraft() {
        validSettings = null;
        preview = null;
        lastBloodMoonInput = null;
        failure = null;
        fields.values().forEach(field -> field.setTextColor(EditBox.DEFAULT_TEXT_COLOR));
        try {
            var settings = new MonsterGrowthRules.Settings(
                    numeric("health_base", 0D, 1024D), numeric("damage_base", 0D, 2048D),
                    numeric("skeleton_reference", 0D, 1024D), numeric("health_daily_percent", 0D, 100D) / 100D,
                    numeric("damage_daily_percent", 0D, 100D) / 100D,
                    drafts.get("health_formula"), drafts.get("damage_formula"),
                    drafts.get("health_blood_moon_formula"), drafts.get("damage_blood_moon_formula"));
            var validation = MiteConfig.validateMonsterGrowth(settings);
            if (!validation.valid()) {
                failure = Component.literal(validation.message());
                markInvalid("health_formula");
                markInvalid("health_blood_moon_formula");
            } else {
                validSettings = settings;
                if (health) {
                    double nativeValue = numeric("health_native", .000001D, 1024D);
                    long day;
                    try {
                        day = Long.parseLong(drafts.get("preview_day"));
                        if (day < 1L) throw new NumberFormatException();
                    } catch (NumberFormatException exception) {
                        markInvalid("preview_day");
                        throw new IllegalArgumentException(Component.translatable("mite.config.invalid_preview_day").getString());
                    }
                    double multiplier = MiteConfig.monsterGrowthMultiplier()
                            * MiteConfig.monsterHealthGrowthMultiplier();
                    preview = MonsterGrowthRules.healthPreview(day, nativeValue, skeleton, settings, multiplier);
                    previewDay = day;
                    double configuredBase = settings.healthBase();
                    previewInitial = configuredBase > 0D ? configuredBase : nativeValue;
                    previewRate = settings.healthRate() * multiplier;
                    if (preview.bloodMoons() > 0L && !preview.fallback()) {
                        long previousDay = day - preview.days() - 1L;
                        lastBloodMoonInput = MonsterGrowthRules.healthPreview(previousDay, nativeValue, skeleton, settings, multiplier);
                        if (lastBloodMoonInput.fallback()) lastBloodMoonInput = null;
                    }
                    if (preview.fallback()) {
                        failure = Component.translatable("mite.config.preview_fallback");
                        validSettings = null;
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            failure = Component.literal(exception.getMessage());
            validSettings = null;
        }
        if (save != null) save.active = editable && validSettings != null;
        if (status != null) {
            Component message = failure != null ? failure : Component.translatable(editable ? "mite.config.growth_valid" : "mite.config.server_owned");
            status.setMessage(Component.literal(font.plainSubstrByWidth(message.getString(), Math.max(60, width - 44))));
            status.setTooltip(Tooltip.create(message));
        }
        updatePreviewLines();
        updateViewport();
    }

    private void updatePreviewLines() {
        previewLines.clear();
        int y = previewY;
        if (preview != null && !preview.fallback()) {
            double multiplier = MiteConfig.monsterGrowthMultiplier()
                    * MiteConfig.monsterHealthGrowthMultiplier();
            y = previewParagraph(Component.translatable("mite.config.growth_preview_initial", number(previewInitial)), y, 0xCCCCCC);
            y = previewParagraph(Component.translatable("mite.config.growth_preview_rate", number(previewRate)), y, 0xCCCCCC);
            if (lastBloodMoonInput != null && preview.base() < Double.MAX_VALUE) {
                y = previewParagraph(Component.translatable("mite.config.growth_preview_blood", preview.bloodMoons(),
                        previewDay - preview.days(), drafts.get("health_blood_moon_formula")), y + 3, 0xFFCCCC);
                y = previewParagraph(Component.translatable("mite.config.growth_preview_blood_inputs", number(lastBloodMoonInput.base()),
                        number(lastBloodMoonInput.reference()), lastBloodMoonInput.days() + 1L, lastBloodMoonInput.bloodMoons(),
                        number(preview.base())), y, 0xFFCCCC);
            } else if (preview.bloodMoons() == 0L) {
                y = previewParagraph(Component.translatable("mite.config.growth_preview_no_blood"), y + 3, 0xFFCCCC);
            } else {
                y = previewParagraph(Component.translatable("mite.config.growth_preview_blood_summary", preview.bloodMoons(),
                        number(preview.base())), y + 3, 0xFFCCCC);
            }
            y = previewParagraph(Component.translatable("mite.config.growth_preview_period",
                    drafts.get("health_formula")), y + 3, 0xCCCCCC);
            y = previewParagraph(Component.translatable("mite.config.growth_preview_context", number(preview.base()),
                    number(preview.reference()), preview.days(), preview.bloodMoons(), previewDay, number(multiplier)), y, 0xCCCCCC);
            y = previewParagraph(Component.translatable("mite.config.growth_preview_value", number(preview.value())), y + 3, 0x88FF88);
            if (preview.value() == Double.MAX_VALUE || preview.base() == Double.MAX_VALUE)
                y = previewParagraph(Component.translatable("mite.config.growth_preview_saturated"), y + 3, 0xFFCC88);
        }
        contentHeight = y + 12;
    }

    private int previewParagraph(Component text, int y, int color) {
        for (var line : font.split(text, panelWidth)) {
            previewLines.add(new TextLine(line, y, color));
            y += font.lineHeight + 2;
        }
        return y;
    }

    private double numeric(String key, double minimum, double maximum) {
        try {
            double value = Double.parseDouble(drafts.get(key));
            if (!Double.isFinite(value) || value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            markInvalid(key);
            throw new IllegalArgumentException(Component.translatable("mite.config.invalid_growth_number",
                    Component.translatable("mite.config." + key), number(minimum), number(maximum)).getString());
        }
    }

    private void markInvalid(String key) {
        EditBox box = fields.get(key);
        if (box != null) box.setTextColor(0xFFFF5555);
    }

    private void save() {
        validateDraft();
        if (!editable || validSettings == null) return;
        var result = MiteConfig.saveMonsterGrowth(validSettings);
        if (result.valid()) minecraft.setScreen(parent);
        else {
            failure = Component.literal(result.message());
            validSettings = null;
            save.active = false;
            status.setMessage(Component.translatable("mite.config.growth_invalid"));
            status.setTooltip(Tooltip.create(failure));
        }
    }

    private int viewportTop() { return 55; }
    private int viewportBottom() { return height - 57; }
    private int maximumScroll() { return Math.max(0, contentHeight - (viewportBottom() - viewportTop())); }
    private void updateViewport() {
        scroll = Math.clamp(scroll, 0, maximumScroll());
        for (var row : scrollingWidgets) {
            int y = viewportTop() + row.y() - scroll;
            row.widget().setY(y);
            row.widget().visible = y >= viewportTop() && y + row.widget().getHeight() <= viewportBottom();
            if (!row.widget().visible && row.widget().isFocused()) {
                row.widget().setFocused(false);
                if (getFocused() == row.widget()) setFocused(null);
            }
        }
    }

    private Component skeletonLabel() {
        return Component.translatable("mite.config.preview_skeleton", Component.translatable(skeleton ? "mite.config.enabled" : "mite.config.disabled"));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        graphics.enableScissor(panelX, viewportTop(), panelX + panelWidth, viewportBottom());
        for (var line : lines) graphics.drawString(font, line.text(), panelX, viewportTop() + line.y() - scroll, line.color());
        for (var line : previewLines) graphics.drawString(font, line.text(), panelX, viewportTop() + line.y() - scroll, line.color());
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
        updateViewport();
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private static String number(double value) {
        if (Double.isFinite(value) && (Math.abs(value) >= 1e9D || value != 0D && Math.abs(value) < 1e-6D))
            return String.format(Locale.ROOT, "%.6g", value);
        return Double.isFinite(value) ? BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() : Double.toString(value);
    }
}
