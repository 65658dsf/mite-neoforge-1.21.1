package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MonsterArmorSettings;
import io.github.ningmeng123.mite.rules.MonsterArmorRules;
import io.github.ningmeng123.mite.rules.MonsterArmorRules.Row;
import io.github.ningmeng123.mite.rules.MonsterArmorRules.Settings;
import java.math.BigDecimal;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Editors hold a draft until the local game or dedicated server accepts it. @author NingMeng123 */
public final class MonsterArmorSettingsScreen extends Screen {
    private static final String KEY = "mite.config.monster_armor";
    private static final int FIRST_ROW = 52;
    private static final int ROW_HEIGHT = 24;
    private static final int DAYS = 0, ORDINARY = 1, BLOOD = 2;
    private static final String[] FIELD_KEYS = {"days", "ordinary", "blood"};
    private final Screen parent;
    private final boolean available;
    private final String[][] drafts = new String[MonsterArmorRules.MATERIALS.size()][FIELD_KEYS.length];
    private final int[] columns = new int[5];
    private int page;
    private int pageSize;
    private int pageCount;
    private boolean saving;
    private Button save;
    private Component failure;
    private Component status;

    public MonsterArmorSettingsScreen(Screen parent) {
        super(Component.translatable(KEY));
        this.parent = parent;
        var initial = MiteConfigClient.monsterArmorSettings();
        available = initial != null;
        if (available) loadDraft(initial);
    }

    private void loadDraft(Settings settings) {
        for (int rowIndex = 0; rowIndex < drafts.length; rowIndex++) {
            Row row = settings.rows().get(rowIndex);
            String end = row.endDay() == MonsterArmorRules.OPEN_END ? "∞" : Long.toString(row.endDay());
            drafts[rowIndex][DAYS] = "[" + row.startDay() + "," + end + "]";
            drafts[rowIndex][ORDINARY] = percentText(row.ordinaryPercent());
            drafts[rowIndex][BLOOD] = percentText(row.bloodPercent());
        }
    }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        setFocused(null);
        clearWidgets();
        if (!available) {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds(width / 2 - 75, height - 28, 150, 20).build());
            return;
        }

        int tableWidth = Math.min(540, width - 24);
        columns[0] = (width - tableWidth) / 2;
        columns[1] = columns[0] + tableWidth * 24 / 100;
        columns[2] = columns[0] + tableWidth * 56 / 100;
        columns[3] = columns[0] + tableWidth * 78 / 100;
        columns[4] = columns[0] + tableWidth;
        pageSize = Math.max(1, (height - FIRST_ROW - 77) / ROW_HEIGHT);
        pageCount = (drafts.length + pageSize - 1) / pageSize;
        page = Math.clamp(page, 0, pageCount - 1);
        boolean editable = MiteConfigClient.editable() && !saving;
        for (int rowIndex = page * pageSize; rowIndex < endRow(); rowIndex++) {
            for (int field = 0; field < FIELD_KEYS.length; field++) addField(rowIndex, field, editable);
        }

        if (pageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuild(); })
                    .bounds(columns[0], height - 54, 38, 20).build()).active = !saving && page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuild(); })
                    .bounds(columns[4] - 38, height - 54, 38, 20).build()).active = !saving && page + 1 < pageCount;
        }
        int actionWidth = Math.min(110, (tableWidth - 8) / 3);
        int actionLeft = (width - actionWidth * 3 - 8) / 2;
        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), button -> save())
                .bounds(actionLeft, height - 28, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
            loadDraft(MonsterArmorRules.DEFAULTS);
            failure = null;
            rebuild();
        }).bounds(actionLeft + actionWidth + 4, height - 28, actionWidth, 20)
                .tooltip(Tooltip.create(Component.translatable(KEY + ".reset.tooltip"))).build()).active = editable;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(actionLeft + actionWidth * 2 + 8, height - 28, actionWidth, 20).build()).active = !saving;
        updateStatus();
    }

    private int endRow() { return Math.min(drafts.length, (page + 1) * pageSize); }

    private void addField(int rowIndex, int field, boolean editable) {
        var header = Component.translatable(KEY + "." + FIELD_KEYS[field]);
        int column = field + 1;
        int y = FIRST_ROW + (rowIndex - page * pageSize) * ROW_HEIGHT;
        var box = addRenderableWidget(new EditBox(font, columns[column] + 2, y,
                columns[column + 1] - columns[column] - 4, 20, material(rowIndex).copy().append(" · ").append(header)));
        box.setMaxLength(48);
        box.setValue(drafts[rowIndex][field]);
        box.moveCursorToStart(false);
        box.setEditable(editable);
        box.setTooltip(Tooltip.create(Component.translatable(KEY + (field == DAYS ? ".days.tooltip" : ".rate.tooltip"))));
        box.setResponder(text -> {
            drafts[rowIndex][field] = text;
            failure = null;
            updateStatus();
        });
    }

    private Settings readDraft() {
        var rows = new ArrayList<Row>();
        try {
            for (var values : drafts) {
                String range = values[DAYS].trim();
                if (range.startsWith("[") && range.endsWith("]")) range = range.substring(1, range.length() - 1);
                String[] days = range.split("[,，]", -1);
                if (days.length != 2) return null;
                String end = days[1].trim();
                rows.add(new Row(Long.parseLong(days[0].trim()),
                        end.isEmpty() || end.equals("∞") ? MonsterArmorRules.OPEN_END : Long.parseLong(end),
                        parsePercent(values[ORDINARY]), parsePercent(values[BLOOD])));
            }
            return new Settings(rows);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void updateStatus() {
        boolean editable = MiteConfigClient.editable();
        boolean valid = readDraft() != null;
        save.active = editable && !saving && valid;
        status = saving ? Component.translatable("mite.config.remote.saving") : failure != null ? failure
                : !editable ? Component.translatable("mite.config.server_owned")
                : !valid ? Component.translatable(KEY + ".invalid") : Component.translatable(KEY + ".rate.hint");
        save.setTooltip(Tooltip.create(status));
    }

    private void save() {
        if (!save.active || !MiteConfigClient.editable()) return;
        var settings = readDraft();
        if (settings == null) return;
        if (MiteConfigClient.remoteServer()) {
            saving = true;
            failure = null;
            rebuild();
            MiteConfigClient.saveRemote("monster_armor", settings, error -> {
                saving = false;
                failure = error;
                if (minecraft.screen != this) return;
                if (error == null) onClose();
                else rebuild();
            });
            return;
        }
        try {
            if (MonsterArmorSettings.save(settings)) {
                onClose();
                return;
            }
        } catch (RuntimeException exception) {
            Mite.LOGGER.warn("Cannot save monster armor progression", exception);
        }
        failure = Component.translatable("mite.config.remote.save_failed");
        updateStatus();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 11, 0xFFFFFF);
        if (!available) {
            graphics.drawWordWrap(font, Component.translatable(KEY + ".unavailable"), 12, 38, width - 24, 0xBBBBBB);
            return;
        }

        drawHeader(graphics, 0, "material");
        for (int field = 0; field < FIELD_KEYS.length; field++) drawHeader(graphics, field + 1, FIELD_KEYS[field]);
        for (int rowIndex = page * pageSize; rowIndex < endRow(); rowIndex++) {
            int y = FIRST_ROW + (rowIndex - page * pageSize) * ROW_HEIGHT;
            Component label = material(rowIndex);
            var lines = font.split(label, columns[1] - columns[0] - 6);
            int lineCount = Math.min(2, lines.size());
            int labelY = y + (20 - lineCount * font.lineHeight) / 2;
            for (int line = 0; line < lineCount; line++)
                graphics.drawString(font, lines.get(line), columns[0] + 2, labelY + line * font.lineHeight, 0xFFFFFF);
            if (mouseX >= columns[0] && mouseX < columns[1] && mouseY >= y && mouseY < y + 20)
                graphics.renderTooltip(font, label, mouseX, mouseY);
        }
        graphics.drawCenteredString(font, font.plainSubstrByWidth(status.getString(), width - 24),
                width / 2, height - 71, failure == null && save.active ? 0xBBBBBB : 0xFFBB88);
        if (pageCount > 1) graphics.drawCenteredString(font,
                Component.translatable("mite.config.page", page + 1, pageCount, drafts.length),
                width / 2, height - 48, 0xCCCCCC);
    }

    private void drawHeader(GuiGraphics graphics, int column, String key) {
        String label = Component.translatable(KEY + "." + key).getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label, columns[column + 1] - columns[column] - 4),
                (columns[column] + columns[column + 1]) / 2, FIRST_ROW - 16, 0xCCCCCC);
    }

    private static Component material(int rowIndex) {
        return Component.translatable(KEY + ".material." + MonsterArmorRules.MATERIALS.get(rowIndex));
    }

    private static double parsePercent(String text) {
        String number = text.trim();
        if (number.endsWith("%") || number.endsWith("％")) number = number.substring(0, number.length() - 1).trim();
        return Double.parseDouble(number);
    }

    private static String percentText(double percent) {
        String plain = BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString();
        return (plain.length() <= 40 ? plain : Double.toString(percent)) + "%";
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
