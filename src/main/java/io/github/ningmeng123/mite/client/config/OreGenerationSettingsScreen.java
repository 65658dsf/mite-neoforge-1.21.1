package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.config.GameplayConfigEdits;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import io.github.ningmeng123.mite.worldgen.R196OreFeature;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/** Per-dimension multipliers and absolute ore heights. @author NingMeng123 */
public final class OreGenerationSettingsScreen extends Screen {
    private record Mineral(Kind kind, boolean deepslate) {}
    private record Entry(Dimension dimension, Mineral mineral, ResourceLocation id, Component name, double base, double chance) {
        Kind kind() { return mineral.kind(); }
    }
    private record VisibleRow(Entry entry, int y) {}
    private static final class Draft {
        final Map<Kind, Double> multipliers = new EnumMap<>(Kind.class);
        final Map<Kind, Double> deepslateMultipliers = new EnumMap<>(Kind.class);
        final Map<Mineral, String> text = new HashMap<>();
        final Set<Mineral> invalid = new HashSet<>();
        final Map<Mineral, String> heights = new HashMap<>();
        final Map<Mineral, String> heightText = new HashMap<>();
        final Set<Mineral> invalidHeights = new HashSet<>();

        double multiplier(Mineral mineral) {
            double override = mineral.deepslate() ? deepslateMultipliers.get(mineral.kind()) : -1D;
            return override < 0D ? multipliers.get(mineral.kind()) : override;
        }
    }
    private final Screen parent;
    private final boolean editable;
    private final Map<Dimension, Draft> drafts = new EnumMap<>(Dimension.class);
    private final Map<Dimension, List<Entry>> entries = new EnumMap<>(Dimension.class);
    private final Map<Dimension, Button> tabs = new EnumMap<>(Dimension.class);
    private final List<VisibleRow> visible = new ArrayList<>();
    private Dimension dimension = Dimension.OVERWORLD;
    private String query = "";
    private int page, pageCount = 1;
    private EditBox search;
    private Button save;
    private boolean saveFailed;
    private boolean saving;
    private Component failure;

    public OreGenerationSettingsScreen(Screen parent) {
        this(parent, Dimension.OVERWORLD, "");
    }

    public OreGenerationSettingsScreen(Screen parent, Dimension dimension, String query) {
        super(Component.translatable("mite.config.ore_generation"));
        this.parent = parent;
        this.dimension = dimension;
        this.query = query == null ? "" : query;
        editable = MiteConfigClient.editable();
        for (var selected : Dimension.values()) {
            if (selected == Dimension.NETHER && !MiteConfigClient.netherOreSettingsAvailable()) continue;
            Draft draft = new Draft();
            List<Entry> minerals = new ArrayList<>();
            for (Kind kind : R196OreRules.configurableKinds(selected)) {
                var block = R196OreFeature.generationBlock(kind);
                var mineral = new Mineral(kind, false);
                addDraft(draft, mineral, selected);
                if (block != Blocks.AIR) minerals.add(new Entry(selected, mineral, BuiltInRegistries.BLOCK.getKey(block), block.getName(),
                        MiteConfig.oreBaseCandidates(kind, selected), MiteConfig.orePassChance(kind, selected)));
                if (selected != Dimension.NETHER && R196OreRules.hasDeepslateOre(kind)) {
                    var deepBlock = R196OreFeature.generationBlock(kind, true);
                    var deepMineral = new Mineral(kind, true);
                    addDraft(draft, deepMineral, selected);
                    minerals.add(new Entry(selected, deepMineral, BuiltInRegistries.BLOCK.getKey(deepBlock), deepBlock.getName(),
                            MiteConfig.oreBaseCandidates(kind, selected), MiteConfig.orePassChance(kind, selected)));
                }
            }
            minerals.sort(Comparator.comparing(entry -> entry.id().toString()));
            drafts.put(selected, draft);
            entries.put(selected, List.copyOf(minerals));
        }
    }

    private static void addDraft(Draft draft, Mineral mineral, Dimension dimension) {
        // Store the displayed absolute multiplier, including for individually editable deepslate variants.
        (mineral.deepslate() ? draft.deepslateMultipliers : draft.multipliers).put(mineral.kind(),
                MiteConfig.oreMultiplier(mineral.kind(), dimension, mineral.deepslate()));
        var override = MiteConfig.oreHeightOverride(mineral.kind(), dimension, mineral.deepslate());
        draft.heights.put(mineral, override == null ? "" : override.toString());
        draft.heightText.put(mineral, MiteConfig.oreHeightRange(mineral.kind(), dimension, mineral.deepslate()).toString());
    }

    @Override protected void init() {
        tabs.clear();
        var dimensions = drafts.keySet().toArray(Dimension[]::new);
        if (!drafts.containsKey(dimension)) dimension = Dimension.OVERWORLD;
        int available = width - 24 - (dimensions.length - 1) * 6;
        for (int i = 0; i < dimensions.length; i++) {
            Dimension selected = dimensions[i];
            int start = i * available / dimensions.length;
            int end = (i + 1) * available / dimensions.length;
            tabs.put(selected, addRenderableWidget(Button.builder(dimensionLabel(selected),
                    button -> changeDimension(selected)).bounds(12 + start + i * 6, 34, end - start, 20).build()));
        }
        search = addRenderableWidget(new EditBox(font, 12, 60, width - 24, 20, Component.translatable("mite.config.ore_search")));
        search.setMaxLength(200);
        search.setHint(Component.translatable("mite.config.ore_search"));
        search.setValue(query);
        search.setResponder(text -> { query = text; page = 0; rebuildRows(); });
        rebuildRows();
    }

    private void changeDimension(Dimension selected) { dimension = selected; page = 0; rebuildRows(); }

    private Component dimensionLabel(Dimension selected) {
        Component label = Component.translatable("mite.config.ore_dimension." + selected.name().toLowerCase(Locale.ROOT));
        return selected == dimension
                ? Component.translatable("mite.config.ore_dimension.selected", label).withStyle(ChatFormatting.YELLOW)
                : label;
    }

    private List<Entry> filtered() {
        String filter = query.trim().toLowerCase(Locale.ROOT);
        return entries.get(dimension).stream().filter(entry -> entry.id().toString().contains(filter)
                || entry.name().getString().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    private void rebuildRows() {
        for (var child : List.copyOf(children()))
            if (child != search && !tabs.containsValue(child)) removeWidget(child);
        tabs.forEach((selected, tab) -> {
            tab.active = selected != dimension;
            tab.setMessage(dimensionLabel(selected));
        });
        visible.clear();
        List<Entry> filtered = filtered();
        int rows = Math.max(1, (height - 170) / 78);
        pageCount = Math.max(1, (filtered.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pageCount - 1);
        Draft draft = drafts.get(dimension);
        int controlsX = width - 150;
        for (int index = page * rows; index < Math.min(filtered.size(), (page + 1) * rows); index++) {
            Entry entry = filtered.get(index);
            int y = 100 + (index - page * rows) * 78;
            visible.add(new VisibleRow(entry, y));
            var box = addRenderableWidget(new EditBox(font, controlsX, y, 78, 20, Component.translatable("mite.config.ore_multiplier")));
            box.setMaxLength(24);
            box.setEditable(editable && !saving);
            box.setTooltip(Tooltip.create(Component.translatable("mite.config.ore_multiplier.tooltip")));
            // Initial display must not overwrite invalid text preserved across searches and pages.
            box.setValue(draft.text.getOrDefault(entry.mineral(), format(draft.multiplier(entry.mineral()))));
            if (draft.invalid.contains(entry.mineral())) box.setTextColor(0xFFFF5555);
            box.setResponder(text -> {
                draft.text.put(entry.mineral(), text);
                try {
                    double value = Double.parseDouble(text.trim());
                    if (!Double.isFinite(value) || value < 0D || value > 100D) throw new NumberFormatException();
                    (entry.mineral().deepslate() ? draft.deepslateMultipliers : draft.multipliers).put(entry.kind(), value);
                    draft.invalid.remove(entry.mineral());
                    box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                } catch (NumberFormatException ignored) { draft.invalid.add(entry.mineral()); box.setTextColor(0xFFFF5555); }
                saveFailed = false;
                failure = null;
                refreshSave();
            });
            var heightBox = addRenderableWidget(new EditBox(font, controlsX, y + 50, 138, 20,
                    Component.translatable("mite.config.ore_height_range")));
            heightBox.setMaxLength(32);
            heightBox.setEditable(editable && !saving);
            var heightHelp = Component.translatable("mite.config.ore_height_range.tooltip");
            if (entry.kind() == Kind.ANCIENT_DEBRIS) heightHelp.append("\n")
                    .append(Component.translatable("mite.config.ore_debris_height_hint"));
            heightBox.setTooltip(Tooltip.create(heightHelp));
            heightBox.setValue(draft.heightText.get(entry.mineral()));
            if (draft.invalidHeights.contains(entry.mineral())) heightBox.setTextColor(0xFFFF5555);
            heightBox.setResponder(text -> {
                draft.heightText.put(entry.mineral(), text);
                try {
                    var range = OreHeightRange.parse(text);
                    draft.heights.put(entry.mineral(), range.equals(OreHeightRange.defaults(entry.kind(), entry.dimension())) ? "" : range.toString());
                    draft.invalidHeights.remove(entry.mineral());
                    heightBox.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                } catch (IllegalArgumentException ignored) {
                    draft.invalidHeights.add(entry.mineral());
                    heightBox.setTextColor(0xFFFF5555);
                }
                saveFailed = false;
                failure = null;
                refreshSave();
            });
            var reset = addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
                box.setValue(format(MiteConfig.factoryOreMultiplier(entry.kind(), entry.dimension(), entry.mineral().deepslate())));
                heightBox.setValue(MiteConfig.factoryOreHeight(entry.kind(), entry.dimension(), entry.mineral().deepslate()).toString());
            }).bounds(controlsX + 84, y, 54, 20).build());
            reset.active = editable && !saving;
            reset.setTooltip(Tooltip.create(Component.translatable("mite.config.ore_multiplier_reset")));
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuildRows(); })
                .bounds(12, height - 65, 30, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuildRows(); })
                .bounds(width - 42, height - 65, 30, 20).build());
        next.active = page < pageCount - 1;
        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), button -> {
            if (saving || !editable || !MiteConfigClient.editable() || invalidCount() != 0) return;
            var values = new EnumMap<Dimension, Map<Kind, Double>>(Dimension.class);
            var deepValues = new EnumMap<Dimension, Map<Kind, Double>>(Dimension.class);
            var heights = new EnumMap<Dimension, Map<Kind, String>>(Dimension.class);
            var deepHeights = new EnumMap<Dimension, Map<Kind, String>>(Dimension.class);
            drafts.forEach((selected, draftValues) -> {
                values.put(selected, draftValues.multipliers);
                var regular = new EnumMap<Kind, String>(Kind.class);
                var deep = new EnumMap<Kind, String>(Kind.class);
                draftValues.heights.forEach((mineral, text) -> (mineral.deepslate() ? deep : regular).put(mineral.kind(), text));
                heights.put(selected, regular);
                if (selected != Dimension.NETHER) {
                    deepValues.put(selected, draftValues.deepslateMultipliers);
                    deepHeights.put(selected, deep);
                }
            });
            if (MiteConfigClient.remoteServer()) {
                saving = true;
                failure = null;
                rebuildRows();
                MiteConfigClient.saveRemote("ore_multipliers", new GameplayConfigEdits.OreMultipliers(values, deepValues, heights, deepHeights), error -> {
                    saving = false;
                    failure = error;
                    if (error == null) {
                        if (minecraft.screen == this) minecraft.setScreen(parent);
                    } else rebuildRows();
                });
            } else if (MiteConfig.saveOreMultipliers(values, deepValues, heights, deepHeights)) minecraft.setScreen(parent);
            else { saveFailed = true; refreshSave(); }
        }).bounds(width / 2 - 104, height - 29, 100, 20).build());
        refreshSave();
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 4, height - 29, 100, 20).build());
    }

    private int invalidCount() { return drafts.values().stream().mapToInt(draft -> draft.invalid.size() + draft.invalidHeights.size()).sum(); }
    private void refreshSave() {
        if (save == null) return;
        save.active = editable && MiteConfigClient.editable() && !saving && invalidCount() == 0;
        save.setTooltip(saving ? Tooltip.create(Component.translatable("mite.config.remote.saving")) : failure != null ? Tooltip.create(failure) : invalidCount() == 0 && !saveFailed ? null : Tooltip.create(
                Component.translatable("mite.config.ore_multiplier_invalid", Math.max(1, invalidCount()))));
    }
    private static String format(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private static Component baseline(Entry entry) {
        double count = R196OreRules.configuredCandidateCount(entry.kind(), entry.dimension());
        if (entry.kind() == Kind.NETHER_INFESTED)
            return Component.translatable("mite.config.ore_nether_infested_baseline", format(count));
        double chance = R196OreRules.configuredCandidateChance(entry.kind(), entry.dimension()) * 100D;
        return Component.translatable("mite.config.ore_baseline", format(count), format(chance), format(count * chance / 100D));
    }
    private Component effective(Entry entry) {
        Draft draft = drafts.get(entry.dimension());
        String attempts = draft.invalid.contains(entry.mineral()) ? "?" : format(entry.base() * draft.multiplier(entry.mineral()) * entry.chance());
        if (entry.kind() == Kind.NETHER_ADAMANTIUM)
            return Component.translatable("mite.config.ore_nether_adamantium_effective", attempts);
        return Component.translatable("mite.config.ore_effective", format(entry.base()), format(entry.chance() * 100D), attempts);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        int controlsX = width - 150;
        graphics.drawString(font, Component.translatable("mite.config.ore_mineral"), 12, 86, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("mite.config.ore_multiplier"), controlsX, 86, 0xBBBBBB);
        for (VisibleRow row : visible) {
            Entry entry = row.entry();
            graphics.drawString(font, font.plainSubstrByWidth(entry.name().getString(), controlsX - 24), 12, row.y() + 1, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(entry.id().toString(), controlsX - 24), 12, row.y() + 13, 0x999999);
            graphics.drawString(font, font.plainSubstrByWidth(baseline(entry).getString(), width - 24), 12, row.y() + 27, 0x999999);
            graphics.drawString(font, font.plainSubstrByWidth(effective(entry).getString(), width - 24), 12, row.y() + 39, 0xCCCCCC);
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("mite.config.ore_height_range").getString(), controlsX - 24),
                    12, row.y() + 56, 0xBBBBBB);
            if (mouseX >= 12 && mouseX < controlsX - 4 && mouseY >= row.y() && mouseY < row.y() + 24) {
                var lines = new ArrayList<Component>(List.of(entry.name(), Component.literal(entry.id().toString()), baseline(entry), effective(entry),
                        Component.translatable("mite.config.ore_multiplier.tooltip"), Component.translatable("mite.config.ore_multiplier_hint")));
                if (entry.dimension() == Dimension.OVERWORLD && entry.kind() == Kind.ADAMANTIUM)
                    lines.add(Component.translatable("mite.config.ore_surface_adamantium"));
                if (entry.mineral().deepslate()) lines.add(Component.translatable("mite.config.ore_deepslate_hint"));
                if (R196OreRules.modernUnderworldAddition(entry.kind(), entry.dimension()))
                    lines.add(Component.translatable("mite.config.ore_underworld_modern_hint"));
                if (entry.kind() == Kind.NETHER_ADAMANTIUM)
                    lines.add(Component.translatable("mite.config.ore_nether_adamantium_hint"));
                if (entry.kind() == Kind.ANCIENT_DEBRIS)
                    lines.add(Component.translatable("mite.config.ore_debris_height_hint"));
                graphics.renderTooltip(font, lines.stream().flatMap(line -> font.split(line, Math.min(360, width - 24)).stream()).toList(), mouseX, mouseY);
            }
        }
        graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, filtered().size()),
                width / 2, height - 59, 0xCCCCCC);
        Component hint = saving ? Component.translatable("mite.config.remote.saving") : failure != null ? failure : invalidCount() > 0 || saveFailed
                ? Component.translatable("mite.config.ore_multiplier_invalid", Math.max(1, invalidCount()))
                : Component.translatable(editable ? "mite.config.ore_multiplier_hint" : "mite.config.server_owned");
        graphics.drawCenteredString(font, font.plainSubstrByWidth(hint.getString(), width - 24), width / 2, height - 42,
                invalidCount() > 0 || saveFailed || failure != null ? 0xFF5555 : 0xBBBBBB);
        if (mouseY >= height - 44 && mouseY < height - 31)
            graphics.renderTooltip(font, font.split(hint, Math.min(360, width - 24)), mouseX, mouseY);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0D) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int before = page;
        page = Math.clamp(page + (scrollY > 0D ? -1 : 1), 0, pageCount - 1);
        if (page != before) rebuildRows();
        return true;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
