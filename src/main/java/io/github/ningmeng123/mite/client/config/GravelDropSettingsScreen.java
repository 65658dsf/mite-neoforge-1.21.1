package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.GravelDropSettings;
import io.github.ningmeng123.mite.config.GravelDropSettings.Rates;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Product probabilities for ordinary and Nether gravel. @author NingMeng123 */
public final class GravelDropSettingsScreen extends Screen {
    enum Gravel {
        ORDINARY("ordinary", "minecraft:gravel"),
        NETHER("nether", "mite:nether_gravel");

        final String key;
        final String blockItem;

        Gravel(String key, String blockItem) {
            this.key = key;
            this.blockItem = blockItem;
        }

        Component label() {
            return itemName(blockItem);
        }

        Map<String, Double> rates(Rates rates) {
            return this == ORDINARY ? rates.ordinary() : rates.nether();
        }
    }

    private static final class Draft {
        final Map<String, Double> defaults;
        final Map<String, Double> values = new HashMap<>();
        final Map<String, String> text = new HashMap<>();
        final Set<String> invalid = new HashSet<>();
        final List<String> items;

        Draft(Map<String, Double> values, Map<String, Double> defaults) {
            this.values.putAll(values);
            this.defaults = defaults;
            items = defaults.keySet().stream().filter(values::containsKey).toList();
        }

        double total() {
            return values.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        boolean valid() {
            return invalid.isEmpty() && Math.abs(total() - 100D) <= 0.000001D;
        }

        void reset() {
            values.replaceAll((id, value) -> defaults.getOrDefault(id, value));
            text.clear();
            invalid.clear();
        }
    }

    private record Row(Component label, int y) {}

    private static final int FIRST_ROW = 78;
    private static final int ROW_HEIGHT = 24;
    private final Screen parent;
    private final Map<Gravel, Draft> drafts;
    private final List<Row> rows = new ArrayList<>();
    private Gravel gravel;
    private String selectedProduct;
    private int page;
    private int pageCount;
    private boolean saving;
    private Component failure;
    private Button save;
    private Button balance;

    public GravelDropSettingsScreen(Screen parent) {
        this(parent, Gravel.ORDINARY, null);
    }

    GravelDropSettingsScreen(Screen parent, Gravel gravel, String product) {
        super(Component.translatable("mite.config.gravel_drops"));
        this.parent = parent;
        this.gravel = gravel;
        selectedProduct = product;
        Rates rates = MiteConfigClient.gravelDropRates();
        Rates defaults = GravelDropSettings.defaults(MiteConfigClient.gravelDropsUseWholeFlint());
        drafts = rates == null ? Map.of() : Map.of(
                Gravel.ORDINARY, new Draft(rates.ordinary(), defaults.ordinary()),
                Gravel.NETHER, new Draft(rates.nether(), defaults.nether()));
    }

    static Component itemName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return Component.literal(id);
        return BuiltInRegistries.ITEM.getOptional(location)
                .map(item -> Component.translatable(item.getDescriptionId()))
                .orElseGet(() -> Component.literal(id));
    }

    @Override protected void init() {
        rebuild();
    }

    private void rebuild() {
        setFocused(null);
        clearWidgets();
        rows.clear();
        if (drafts.isEmpty()) {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds(width / 2 - 75, height - 28, 150, 20).build());
            return;
        }

        Draft draft = drafts.get(gravel);
        boolean editable = MiteConfigClient.editable() && !saving;
        int tabWidth = Math.min(140, (width - 32) / 2);
        for (Gravel type : Gravel.values()) {
            int x = type == Gravel.ORDINARY ? width / 2 - tabWidth - 2 : width / 2 + 2;
            addRenderableWidget(Button.builder(type.label(), button -> {
                gravel = type;
                page = 0;
                rebuild();
            }).bounds(x, 28, tabWidth, 20).build()).active = type != gravel && !saving;
        }

        int pageSize = Math.max(1, (height - 90 - FIRST_ROW) / ROW_HEIGHT);
        if (selectedProduct != null) {
            page = Math.max(0, draft.items.indexOf(selectedProduct)) / pageSize;
            selectedProduct = null;
        }
        pageCount = Math.max(1, (draft.items.size() + pageSize - 1) / pageSize);
        page = Math.clamp(page, 0, pageCount - 1);
        int fieldWidth = rateFieldWidth();
        for (int index = page * pageSize; index < Math.min(draft.items.size(), (page + 1) * pageSize); index++) {
            String id = draft.items.get(index);
            int y = FIRST_ROW + (index - page * pageSize) * ROW_HEIGHT;
            Component label = itemName(id);
            rows.add(new Row(label, y));
            var field = addRenderableWidget(new EditBox(font, width - fieldWidth - 34, y, fieldWidth, 20, label));
            field.setMaxLength(32);
            field.setValue(draft.text.getOrDefault(id, number(draft.values.get(id), fieldWidth - 8)));
            field.moveCursorToStart(false);
            field.setEditable(editable);
            field.setTextColor(draft.invalid.contains(id) ? 0xFFFF5555 : EditBox.DEFAULT_TEXT_COLOR);
            field.setTooltip(Tooltip.create(Component.translatable("mite.config.gravel_drops.default",
                    displayNumber(draft.defaults.getOrDefault(id, draft.values.get(id))), id)));
            field.setResponder(text -> edit(draft, id, field, text));
        }

        Component balanceLabel = Component.translatable("mite.config.gravel_drops.balance");
        int balanceWidth = Math.min(width / 2 - 16, font.width(balanceLabel) + 16);
        balance = addRenderableWidget(Button.builder(balanceLabel, button -> balance(draft))
                .bounds(width - balanceWidth - 12, height - 78, balanceWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("mite.config.gravel_drops.balance.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuild(); })
                .bounds(12, height - 54, 40, 20).build()).active = !saving && page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuild(); })
                .bounds(width - 52, height - 54, 40, 20).build()).active = !saving && page + 1 < pageCount;
        int actionWidth = Math.min(100, (width - 32) / 3);
        int left = (width - actionWidth * 3 - 8) / 2;
        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), button -> save())
                .bounds(left, height - 28, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
            drafts.values().forEach(Draft::reset);
            failure = null;
            rebuild();
        }).bounds(left + actionWidth + 4, height - 28, actionWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("mite.config.gravel_drops.reset.tooltip"))).build()).active = editable;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + actionWidth * 2 + 8, height - 28, actionWidth, 20).build());
        updateButtons();
    }

    private void edit(Draft draft, String id, EditBox field, String text) {
        draft.text.put(id, text);
        failure = null;
        try {
            double rate = Double.parseDouble(text.trim());
            if (!Double.isFinite(rate) || rate < 0D || rate > 100D) throw new NumberFormatException();
            draft.values.put(id, rate);
            draft.invalid.remove(id);
            field.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
        } catch (NumberFormatException exception) {
            draft.invalid.add(id);
            field.setTextColor(0xFFFF5555);
        }
        updateButtons();
    }

    private double remaining(Draft draft) {
        return 100D - draft.values.entrySet().stream().filter(entry -> !entry.getKey().equals(gravel.blockItem))
                .mapToDouble(Map.Entry::getValue).sum();
    }

    private void balance(Draft draft) {
        draft.values.put(gravel.blockItem, Math.max(0D, remaining(draft)));
        draft.text.remove(gravel.blockItem);
        draft.invalid.remove(gravel.blockItem);
        failure = null;
        rebuild();
    }

    private Component saveStatus() {
        if (saving) return Component.translatable("mite.config.remote.saving");
        if (failure != null) return failure;
        if (!MiteConfigClient.editable()) return Component.translatable("mite.config.server_owned");
        for (Gravel type : Gravel.values()) {
            Draft draft = drafts.get(type);
            if (!draft.invalid.isEmpty()) return Component.translatable("mite.config.gravel_drops.invalid", type.label());
            if (!draft.valid()) return Component.translatable("mite.config.gravel_drops.invalid_total", type.label());
        }
        return Component.translatable("mite.config.gravel_drops.live");
    }

    private void updateButtons() {
        boolean editable = MiteConfigClient.editable() && !saving;
        Draft draft = drafts.get(gravel);
        balance.active = editable && draft.values.containsKey(gravel.blockItem)
                && draft.invalid.stream().allMatch(gravel.blockItem::equals) && remaining(draft) >= -0.000001D;
        save.active = editable && drafts.values().stream().allMatch(Draft::valid);
        save.setTooltip(Tooltip.create(saveStatus()));
    }

    private void save() {
        if (!save.active) return;
        var rates = new Rates(Map.copyOf(drafts.get(Gravel.ORDINARY).values),
                Map.copyOf(drafts.get(Gravel.NETHER).values));
        if (MiteConfigClient.remoteServer()) {
            saving = true;
            failure = null;
            rebuild();
            MiteConfigClient.saveRemote("gravel_drops", rates, error -> {
                saving = false;
                failure = error;
                if (minecraft.screen != this) return;
                if (error == null) onClose();
                else rebuild();
            });
        } else {
            try {
                if (GravelDropSettings.save(rates)) {
                    onClose();
                    return;
                }
            } catch (RuntimeException exception) {
                Mite.LOGGER.warn("Cannot save gravel drop rates", exception);
            }
            failure = Component.translatable("mite.config.gravel_drops.save_failed");
            updateButtons();
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 11, 0xFFFFFF);
        if (drafts.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("mite.config.gravel_drops.unavailable"),
                    12, 38, width - 24, 0xBBBBBB);
            return;
        }
        graphics.drawWordWrap(font, Component.translatable("mite.config.gravel_drops.hint"),
                12, 54, width - 24, 0xBBBBBB);
        for (Row row : rows) {
            graphics.drawString(font, font.plainSubstrByWidth(row.label().getString(), width - rateFieldWidth() - 52),
                    12, row.y() + 6, 0xFFFFFF);
            graphics.drawString(font, "%", width - 29, row.y() + 6, 0xBBBBBB);
        }
        Draft draft = drafts.get(gravel);
        Component total = Component.translatable("mite.config.gravel_drops.total", displayNumber(draft.total()));
        graphics.drawString(font, total, 12, height - 72, draft.valid() ? 0xCCCCCC : 0xFF7777);
        graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, draft.items.size()),
                width / 2, height - 48, 0xCCCCCC);
        if (saving || failure != null || !MiteConfigClient.editable() || !save.active) {
            Component status = saveStatus();
            graphics.drawString(font, font.plainSubstrByWidth(status.getString(), width - 24),
                    12, height - 94, 0xFFBB88);
        }
    }

    private int rateFieldWidth() {
        return Math.min(148, width / 2 - 20);
    }

    private String number(double value, int textWidth) {
        String plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        return plain.length() <= 32 && font.width(plain) <= textWidth ? plain : Double.toString(value);
    }

    private static String displayNumber(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    @Override public void onClose() {
        minecraft.setScreen(parent);
    }
}
