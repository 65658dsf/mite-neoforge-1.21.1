package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.config.MonsterSpawnCatalog;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.ModList;

/** Shared registry browser; the owning mod supplies authoritative values and its save operation. @author NingMeng123 */
public final class MonsterSpawnSettingsScreen extends Screen {
    private static final int MAX_NUMBER_LENGTH = 32;
    private record VisibleRow(MonsterSpawnCatalog.Entry entry, int y) {}
    private final Screen parent;
    private final boolean editable;
    private final boolean hasSpawningRules;
    private final boolean weightMode;
    private final BiConsumer<Map<String, Double>, Map<String, Double>> saveValues;
    private final List<MonsterSpawnCatalog.Entry> entries = new ArrayList<>();
    private final List<VisibleRow> visible = new ArrayList<>();
    private final Map<String, Double> values;
    private final Map<String, Double> weightValues;
    private final Map<String, Double> defaultRates, defaultWeights;
    private final Map<String, String> drafts = new HashMap<>();
    private final Map<String, String> weightDrafts = new HashMap<>();
    private final Set<String> invalid = new HashSet<>();
    private final Set<String> invalidWeights = new HashSet<>();
    private String query = "";
    private int page;
    private int pageCount;
    private EditBox search;
    private Button save;
    private Button spawningRules;
    private Component failure;

    public MonsterSpawnSettingsScreen(Screen parent, String ownerModId,
            Supplier<Map<String, Double>> values, Consumer<Map<String, Double>> save) {
        this(parent, ownerModId, values, save, null);
    }

    public MonsterSpawnSettingsScreen(Screen parent, String ownerModId,
            Supplier<Map<String, Double>> values, Consumer<Map<String, Double>> save,
            Function<EntityType<?>, List<Integer>> nativeWeights) {
        this(parent, ownerModId, values, null, (rates, weights) -> save.accept(rates), nativeWeights);
    }

    public MonsterSpawnSettingsScreen(Screen parent, String ownerModId,
            Supplier<Map<String, Double>> rates, Supplier<Map<String, Double>> weights,
            BiConsumer<Map<String, Double>, Map<String, Double>> save,
            Function<EntityType<?>, List<Integer>> nativeWeights) {
        this(parent, ownerModId, rates, weights, save, nativeWeights, Map::of, Map::of);
    }

    public MonsterSpawnSettingsScreen(Screen parent, String ownerModId,
            Supplier<Map<String, Double>> rates, Supplier<Map<String, Double>> weights,
            BiConsumer<Map<String, Double>, Map<String, Double>> save,
            Function<EntityType<?>, List<Integer>> nativeWeights,
            Supplier<Map<String, Double>> defaultRates, Supplier<Map<String, Double>> defaultWeights) {
        super(Component.translatable("mite.config.monster_spawn_title", ModList.get().getModContainerById(ownerModId)
                .map(container -> container.getModInfo().getDisplayName()).orElse(ownerModId)));
        this.parent = parent;
        this.editable = MiteConfigClient.editable();
        this.hasSpawningRules = "mite".equals(ownerModId);
        this.weightMode = weights != null;
        this.saveValues = save;
        this.defaultRates = Map.copyOf(defaultRates.get());
        this.defaultWeights = Map.copyOf(defaultWeights.get());
        this.values = new HashMap<>(rates.get());
        this.weightValues = new HashMap<>(weights == null ? Map.of() : weights.get());
        var level = Minecraft.getInstance().level;
        var known = new HashMap<String, MonsterSpawnCatalog.Entry>();
        for (var entry : MonsterSpawnCatalog.entries(ownerModId, level == null ? RegistryAccess.EMPTY : level.registryAccess())) {
            List<Integer> nativePoolWeights = nativeWeights == null ? entry.baseWeights() : nativeWeights.apply(entry.type());
            known.put(entry.id(), new MonsterSpawnCatalog.Entry(entry.id(), entry.name(), entry.type(),
                    nativePoolWeights == null ? List.of() : nativePoolWeights.stream().distinct().sorted().toList()));
        }
        this.values.keySet().stream().filter(id -> MonsterSpawnCatalog.ownedBy(ownerModId, id)).forEach(id ->
                known.putIfAbsent(id, new MonsterSpawnCatalog.Entry(id, Component.literal(id), null, List.of())));
        this.weightValues.keySet().stream().filter(id -> MonsterSpawnCatalog.ownedBy(ownerModId, id)).forEach(id ->
                known.putIfAbsent(id, new MonsterSpawnCatalog.Entry(id, Component.literal(id), null, List.of())));
        entries.addAll(known.values());
        entries.sort(Comparator.comparing(MonsterSpawnCatalog.Entry::id));
    }

    @Override protected void init() {
        int searchY = 33;
        search = addRenderableWidget(new EditBox(font, 12, searchY, Math.max(100, width - (hasSpawningRules ? 142 : 24)), 20,
                Component.translatable("mite.config.monster_spawn_search")));
        search.setMaxLength(200);
        search.setHint(Component.translatable("mite.config.monster_spawn_search"));
        search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; rebuildRows(); });
        if (hasSpawningRules) {
            spawningRules = addRenderableWidget(Button.builder(Component.translatable("mite.config.spawning_rules.button"),
                    button -> minecraft.setScreen(new MonsterSpawningRulesScreen(this)))
                    .bounds(width - 124, searchY, 112, 20).build());
            spawningRules.setTooltip(Tooltip.create(Component.translatable("mite.config.spawning_rules.button.tooltip")));
        }
        rebuildRows();
    }

    private void rebuildRows() {
        if (getFocused() != search) setFocused(null);
        for (var child : List.copyOf(children())) if (child != search && child != spawningRules) removeWidget(child);
        visible.clear();
        String filter = query.toLowerCase(Locale.ROOT);
        var filtered = entries.stream().filter(entry -> entry.id().contains(filter)
                || entry.name().getString().toLowerCase(Locale.ROOT).contains(filter)).toList();
        int rows = Math.max(1, (height - 166) / 52);
        pageCount = Math.max(1, (filtered.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pageCount - 1);
        int end = Math.min(filtered.size(), (page + 1) * rows);
        for (int index = page * rows; index < end; index++) {
            var entry = filtered.get(index);
            int y = 75 + (index - page * rows) * 52;
            visible.add(new VisibleRow(entry, y));
            boolean editingWeight = weightMode;
            var activeValues = editingWeight ? weightValues : values;
            var activeDrafts = editingWeight ? weightDrafts : drafts;
            var activeInvalid = editingWeight ? invalidWeights : invalid;
            String columnKey = editingWeight ? "mite.config.monster_spawn_weight" : "mite.config.monster_spawn_percent";
            var box = new EditBox(font, width - 124, y, 61, 20, Component.translatable(columnKey));
            box.setMaxLength(MAX_NUMBER_LENGTH);
            box.setValue(activeDrafts.getOrDefault(entry.id(), editingWeight
                    ? weightValues.containsKey(entry.id()) ? number(weightValues.get(entry.id()) * values.getOrDefault(entry.id(), 1D)) : ""
                    : number(values.getOrDefault(entry.id(), 1D) * 100D)));
            if (editingWeight) box.setHint(Component.translatable(supportsWeight(entry)
                    ? "mite.config.monster_spawn_weight_inherit" : "mite.config.monster_spawn_weight_unavailable"));
            box.setEditable(editable && (!editingWeight || supportsWeight(entry)));
            box.setTextColor(activeInvalid.contains(entry.id()) ? 0xFFFF5555 : EditBox.DEFAULT_TEXT_COLOR);
            box.setTooltip(Tooltip.create(Component.translatable(editingWeight && !supportsWeight(entry)
                    ? "mite.config.monster_spawn_weight_unavailable.tooltip" : columnKey + ".tooltip")));
            box.setResponder(value -> {
                activeDrafts.put(entry.id(), value);
                failure = null;
                try {
                    if (editingWeight && value.isBlank()) setWeight(entry.id(), null);
                    else {
                        double number = Double.parseDouble(value.trim());
                        if (!Double.isFinite(number) || number < 0D || number > (editingWeight ? 1000000D : 10000D))
                            throw new NumberFormatException();
                        if (editingWeight) setWeight(entry.id(), number);
                        else if (number == 100D) activeValues.remove(entry.id());
                        else activeValues.put(entry.id(), number / 100D);
                    }
                    activeInvalid.remove(entry.id());
                    box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                } catch (NumberFormatException exception) {
                    activeInvalid.add(entry.id());
                    box.setTextColor(0xFFFF5555);
                }
                updateSave();
            });
            addRenderableWidget(box);
            var reset = addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
                if (editingWeight) {
                    setWeight(entry.id(), defaultWeights.get(entry.id()));
                    if (defaultRates.containsKey(entry.id())) values.put(entry.id(), defaultRates.get(entry.id()));
                } else if (defaultRates.containsKey(entry.id())) activeValues.put(entry.id(), defaultRates.get(entry.id()));
                else activeValues.remove(entry.id());
                activeDrafts.remove(entry.id());
                activeInvalid.remove(entry.id());
                failure = null;
                rebuildRows();
            }).bounds(width - 58, y, 46, 20).build());
            reset.active = editable;
            reset.setTooltip(Tooltip.create(Component.translatable("mite.config.monster_spawn_reset.tooltip")));
        }
        int pageButtonWidth = Math.min(90, Math.max(60, width / 4));
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuildRows(); })
                .bounds(12, height - 54, pageButtonWidth, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuildRows(); })
                .bounds(width - 12 - pageButtonWidth, height - 54, pageButtonWidth, 20).build());
        next.active = page + 1 < pageCount;
        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), button -> save())
                .bounds(width / 2 - 104, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 4, height - 28, 100, 20).build());
        updateSave();
    }

    private void updateSave() {
        if (save == null) return;
        save.active = editable && validDrafts() && failure == null;
        save.setTooltip(Tooltip.create(failure != null ? failure : Component.translatable(!editable
                ? "mite.config.server_owned" : validDrafts() ? "mite.config.monster_spawn_save_hint"
                        : weightMode ? "mite.config.monster_spawn_weight_invalid" : "mite.config.monster_spawn_invalid")));
    }

    private void save() {
        if (!MiteConfigClient.editable() || !validDrafts()) return;
        try {
            saveValues.accept(Map.copyOf(values), Map.copyOf(weightValues));
            minecraft.setScreen(parent);
        } catch (RuntimeException exception) {
            failure = Component.literal(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            updateSave();
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 11, 0xFFFFFF);
        int headingY = 60;
        graphics.drawString(font, Component.translatable("mite.config.monster_spawn_name"), 12, headingY, 0xCCCCCC);
        graphics.drawString(font, Component.translatable(weightMode ? "mite.config.monster_spawn_weight" : "mite.config.monster_spawn_percent"), width - 124, headingY, 0xCCCCCC);
        for (var row : visible) {
            var entry = row.entry();
            int nameWidth = Math.max(20, width - 146);
            graphics.drawString(font, font.plainSubstrByWidth(entry.name().getString(), nameWidth), 12, row.y() + 2, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(entry.id(), nameWidth), 12, row.y() + 14, 0x999999);
            Component baseline = !supportsWeight(entry) ? Component.translatable("mite.config.monster_spawn_conditional")
                    : weightMode ? Component.translatable("mite.config.monster_spawn_effective_weight", effectiveWeights(entry),
                            entry.baseWeights().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" / ")))
                    : Component.translatable("mite.config.monster_spawn_baseline", entry.baseWeights().stream()
                            .map(Object::toString).collect(java.util.stream.Collectors.joining(" / ")));
            graphics.drawString(font, font.plainSubstrByWidth(baseline.getString(), width - 24), 12, row.y() + 30, 0xBBBBBB);
        }
        int y = height - 86;
        for (var line : font.split(Component.translatable(weightMode ? "mite.config.monster_spawn_weight_hint" : "mite.config.monster_spawn_hint"), width - 24).stream().limit(2).toList()) {
            graphics.drawString(font, line, 12, y, 0xAAAAAA);
            y += font.lineHeight + 2;
        }
        graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, entries.size()),
                width / 2, height - 48, 0xCCCCCC);
        for (var row : visible) if (mouseX >= 12 && mouseX < width - 130 && mouseY >= row.y() && mouseY < row.y() + 28)
            graphics.renderTooltip(font, Component.literal(row.entry().id()).append("\n")
                    .append(Component.translatable("mite.config.monster_spawn_details")), mouseX, mouseY);
    }

    /** Editing or resetting a direct weight replaces this entity's legacy multiplier. */
    private void setWeight(String id, Double weight) {
        values.remove(id);
        if (weight == null) weightValues.remove(id);
        else weightValues.put(id, weight);
    }

    private String effectiveWeights(MonsterSpawnCatalog.Entry entry) {
        double multiplier = values.getOrDefault(entry.id(), 1D);
        if (weightValues.containsKey(entry.id())) return number(weightValues.get(entry.id()) * multiplier);
        return entry.baseWeights().stream().map(weight -> number(weight * multiplier)).distinct()
                .collect(java.util.stream.Collectors.joining(" / "));
    }

    private boolean validDrafts() { return invalid.isEmpty() && invalidWeights.isEmpty(); }
    private static boolean supportsWeight(MonsterSpawnCatalog.Entry entry) { return entry.baseWeights().stream().anyMatch(weight -> weight > 0); }
    private static String number(double value) {
        String plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        // Very small valid weights must not be truncated to zero by EditBox.setValue on reopening.
        return plain.length() <= MAX_NUMBER_LENGTH ? plain : Double.toString(value);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
