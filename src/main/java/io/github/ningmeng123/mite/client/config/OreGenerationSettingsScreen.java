package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import io.github.ningmeng123.mite.worldgen.R196OreFeature;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

/** Shows the actual generation candidates and pass rates from the active underworld generator. @author NingMeng123 */
public final class OreGenerationSettingsScreen extends Screen {
    private record Entry(Kind kind, ResourceLocation id, Component name) {}
    private record VisibleRow(Entry entry, int y) {}
    private final Screen parent;
    private final boolean editable;
    private final List<Entry> entries = new ArrayList<>();
    private final List<VisibleRow> visible = new ArrayList<>();
    private final Map<Kind, Double> candidates = new EnumMap<>(Kind.class);
    private final Map<Kind, Double> chances = new EnumMap<>(Kind.class);
    private final Map<Kind, String> candidateDrafts = new EnumMap<>(Kind.class);
    private final Map<Kind, String> chanceDrafts = new EnumMap<>(Kind.class);
    private final Set<Kind> invalidCandidates = new HashSet<>();
    private final Set<Kind> invalidChances = new HashSet<>();
    private String query = "";
    private int page;
    private int pageCount = 1;
    private EditBox search;
    private Button save;
    private boolean saveFailed;

    public OreGenerationSettingsScreen(Screen parent) {
        super(Component.translatable("mite.config.ore_generation"));
        this.parent = parent;
        editable = MiteConfigClient.editable();
        for (Kind kind : R196OreRules.UNDERWORLD) {
            var block = R196OreFeature.generationBlock(kind);
            entries.add(new Entry(kind, BuiltInRegistries.BLOCK.getKey(block), block.getName()));
            candidates.put(kind, MiteConfig.underworldOreCandidates(kind));
            chances.put(kind, MiteConfig.underworldOrePassChance(kind) * 100D);
        }
        entries.sort(Comparator.comparing(entry -> entry.id().toString()));
    }

    @Override protected void init() {
        search = addRenderableWidget(new EditBox(font, 12, 38, Math.max(100, width - 24), 20,
                Component.translatable("mite.config.search")));
        search.setMaxLength(200);
        search.setHint(Component.translatable("mite.config.search"));
        search.setValue(query);
        search.setResponder(text -> { query = text; page = 0; rebuildRows(); });
        rebuildRows();
    }

    private List<Entry> filtered() {
        String filter = query.toLowerCase(Locale.ROOT).trim();
        return entries.stream().filter(entry -> entry.id().toString().contains(filter)
                || entry.name().getString().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    private int controlsX() { return Math.max(118, width - 194); }

    private void rebuildRows() {
        // Keep the search box and every draft, including invalid text, while filtering, paging or resizing.
        for (var child : List.copyOf(children())) if (child != search) removeWidget(child);
        visible.clear();
        List<Entry> filtered = filtered();
        int rows = Math.max(1, (height - 148) / 54);
        pageCount = Math.max(1, (filtered.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pageCount - 1);
        int controlsX = controlsX();
        for (int index = page * rows; index < Math.min(filtered.size(), (page + 1) * rows); index++) {
            Entry entry = filtered.get(index);
            Kind kind = entry.kind();
            int y = 78 + (index - page * rows) * 54;
            visible.add(new VisibleRow(entry, y));
            EditBox count = numberBox(controlsX, y, 62, "mite.config.ore_candidates", entry);
            EditBox chance = numberBox(controlsX + 68, y, 56, "mite.config.ore_chance", entry);
            count.setResponder(text -> updateDraft(kind, text, false, count));
            chance.setResponder(text -> updateDraft(kind, text, true, chance));
            count.setValue(candidateDrafts.getOrDefault(kind, format(candidates.get(kind))));
            chance.setValue(chanceDrafts.getOrDefault(kind, format(chances.get(kind))));
            var reset = addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
                count.setValue(format(R196OreRules.candidateCount(kind, Dimension.UNDERWORLD)));
                chance.setValue(format(R196OreRules.candidateChance(Dimension.UNDERWORLD) * 100D));
            }).bounds(controlsX + 130, y, 52, 20).build());
            reset.active = editable;
            reset.setTooltip(Tooltip.create(baseline(entry)));
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            page--; rebuildRows();
        }).bounds(12, height - 65, 30, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page++; rebuildRows();
        }).bounds(width - 42, height - 65, 30, 20).build());
        next.active = page < pageCount - 1;
        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), button -> {
            if (!editable || !MiteConfigClient.editable() || invalidCount() != 0) return;
            if (MiteConfig.saveUnderworldOreGeneration(candidates, chances)) minecraft.setScreen(parent);
            else { saveFailed = true; refreshSave(); }
        }).bounds(width / 2 - 104, height - 29, 100, 20).build());
        refreshSave();
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 4, height - 29, 100, 20).build());
    }

    private EditBox numberBox(int x, int y, int boxWidth, String label, Entry entry) {
        var box = addRenderableWidget(new EditBox(font, x, y, boxWidth, 20, Component.translatable(label)));
        box.setMaxLength(24);
        box.setEditable(editable);
        box.setTooltip(Tooltip.create(Component.translatable(label).append("\n").append(baseline(entry))
                .append("\n").append(Component.translatable("mite.config.ore_actual_hint"))));
        return box;
    }

    private void updateDraft(Kind kind, String text, boolean percentage, EditBox box) {
        (percentage ? chanceDrafts : candidateDrafts).put(kind, text);
        Set<Kind> invalid = percentage ? invalidChances : invalidCandidates;
        try {
            double number = Double.parseDouble(text.trim());
            if (!Double.isFinite(number) || number < 0D || number > (percentage ? 100D : 100_000D))
                throw new NumberFormatException();
            (percentage ? chances : candidates).put(kind, number);
            invalid.remove(kind);
            box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
        } catch (NumberFormatException ignored) {
            invalid.add(kind);
            box.setTextColor(0xFFFF5555);
        }
        saveFailed = false;
        refreshSave();
    }

    private int invalidCount() {
        Set<Kind> invalid = new HashSet<>(invalidCandidates);
        invalid.addAll(invalidChances);
        return invalid.size();
    }

    private void refreshSave() {
        if (save == null) return;
        int invalid = invalidCount();
        save.active = editable && MiteConfigClient.editable() && invalid == 0;
        save.setTooltip(invalid == 0 && !saveFailed ? null
                : Tooltip.create(Component.translatable("mite.config.ore_invalid", Math.max(1, invalid))));
    }

    private static Component baseline(Entry entry) {
        double originalCandidates = R196OreRules.candidateCount(entry.kind(), Dimension.UNDERWORLD);
        double originalChance = R196OreRules.candidateChance(Dimension.UNDERWORLD) * 100D;
        return Component.translatable("mite.config.ore_baseline", format(originalCandidates), format(originalChance),
                format(originalCandidates * originalChance / 100D));
    }

    private static String format(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() : Double.toString(value);
    }

    private Component expected(Entry entry) {
        Kind kind = entry.kind();
        String value = invalidCandidates.contains(kind) || invalidChances.contains(kind) ? "?"
                : format(candidates.get(kind) * chances.get(kind) / 100D);
        return Component.translatable("mite.config.ore_expected", value);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        int controlsX = controlsX();
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("mite.config.ore_generation").getString(), controlsX - 22),
                12, 64, 0xBBBBBB);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("mite.config.ore_candidates").getString(), 64),
                controlsX, 64, 0xBBBBBB);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("mite.config.ore_chance").getString(), 58),
                controlsX + 68, 64, 0xBBBBBB);
        for (VisibleRow row : visible) {
            Entry entry = row.entry();
            graphics.drawString(font, font.plainSubstrByWidth(entry.name().getString(), controlsX - 22),
                    12, row.y() + 1, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(entry.id().toString(), controlsX - 22),
                    12, row.y() + 12, 0x999999);
            graphics.drawString(font, font.plainSubstrByWidth(expected(entry).getString(), width - 24),
                    12, row.y() + 26, invalidCandidates.contains(entry.kind()) || invalidChances.contains(entry.kind()) ? 0xFF5555 : 0xCCCCCC);
            graphics.drawString(font, font.plainSubstrByWidth(baseline(entry).getString(), width - 24),
                    12, row.y() + 38, 0x999999);
            if (mouseX >= 12 && mouseX < controlsX - 5 && mouseY >= row.y() && mouseY < row.y() + 24)
                graphics.renderTooltip(font, List.of(entry.name().getVisualOrderText(),
                        Component.literal(entry.id().toString()).getVisualOrderText(), expected(entry).getVisualOrderText(),
                        baseline(entry).getVisualOrderText(), Component.translatable("mite.config.ore_actual_hint").getVisualOrderText()), mouseX, mouseY);
        }
        graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, filtered().size()),
                width / 2, height - 59, 0xCCCCCC);
        int invalid = invalidCount();
        Component hint = invalid > 0 || saveFailed ? Component.translatable("mite.config.ore_invalid", Math.max(1, invalid))
                : Component.translatable(editable ? "mite.config.ore_actual_hint" : "mite.config.server_owned");
        graphics.drawCenteredString(font, font.plainSubstrByWidth(hint.getString(), width - 24), width / 2,
                height - 42, invalid > 0 || saveFailed ? 0xFF5555 : 0xBBBBBB);
        if (mouseY >= height - 44 && mouseY < height - 31)
            graphics.renderTooltip(font, font.split(hint, Math.min(360, width - 24)), mouseX, mouseY);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0D) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int previous = page;
        page = Math.clamp(page + (scrollY > 0D ? -1 : 1), 0, pageCount - 1);
        if (page != previous) rebuildRows();
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
