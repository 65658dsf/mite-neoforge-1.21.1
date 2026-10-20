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
    private record VisibleRow(MonsterSpawnCatalog.Entry entry, int y) {}
    private final Screen parent;
    private final boolean editable;
    private final Consumer<Map<String, Double>> saveValues;
    private final List<MonsterSpawnCatalog.Entry> entries = new ArrayList<>();
    private final List<VisibleRow> visible = new ArrayList<>();
    private final Map<String, Double> values;
    private final Map<String, String> drafts = new HashMap<>();
    private final Set<String> invalid = new HashSet<>();
    private String query = "";
    private int page;
    private int pageCount;
    private EditBox search;
    private Button save;
    private Component failure;

    public MonsterSpawnSettingsScreen(Screen parent, String ownerModId,
            Supplier<Map<String, Double>> values, Consumer<Map<String, Double>> save) {
        this(parent, ownerModId, values, save, null);
    }

    public MonsterSpawnSettingsScreen(Screen parent, String ownerModId,
            Supplier<Map<String, Double>> values, Consumer<Map<String, Double>> save,
            Function<EntityType<?>, List<Integer>> nativeWeights) {
        super(Component.translatable("mite.config.monster_spawn_title", ModList.get().getModContainerById(ownerModId)
                .map(container -> container.getModInfo().getDisplayName()).orElse(ownerModId)));
        this.parent = parent;
        this.editable = MiteConfigClient.editable();
        this.saveValues = save;
        this.values = new HashMap<>(values.get());
        var level = Minecraft.getInstance().level;
        var known = new HashMap<String, MonsterSpawnCatalog.Entry>();
        for (var entry : MonsterSpawnCatalog.entries(ownerModId, level == null ? RegistryAccess.EMPTY : level.registryAccess())) {
            List<Integer> weights = nativeWeights == null ? entry.baseWeights() : nativeWeights.apply(entry.type());
            known.put(entry.id(), new MonsterSpawnCatalog.Entry(entry.id(), entry.name(), entry.type(),
                    weights == null ? List.of() : weights.stream().distinct().sorted().toList()));
        }
        this.values.keySet().stream().filter(id -> MonsterSpawnCatalog.ownedBy(ownerModId, id)).forEach(id ->
                known.putIfAbsent(id, new MonsterSpawnCatalog.Entry(id, Component.literal(id), null, List.of())));
        entries.addAll(known.values());
        entries.sort(Comparator.comparing(MonsterSpawnCatalog.Entry::id));
    }

    @Override protected void init() {
        search = addRenderableWidget(new EditBox(font, 12, 33, Math.max(100, width - 24), 20,
                Component.translatable("mite.config.monster_spawn_search")));
        search.setMaxLength(200);
        search.setHint(Component.translatable("mite.config.monster_spawn_search"));
        search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; rebuildRows(); });
        rebuildRows();
    }

    private void rebuildRows() {
        if (getFocused() != search) setFocused(null);
        for (var child : List.copyOf(children())) if (child != search) removeWidget(child);
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
            var box = new EditBox(font, width - 124, y, 61, 20, Component.translatable("mite.config.monster_spawn_percent"));
            box.setMaxLength(32);
            box.setValue(drafts.getOrDefault(entry.id(), number(this.values.getOrDefault(entry.id(), 1D) * 100D)));
            box.setEditable(editable);
            box.setTextColor(invalid.contains(entry.id()) ? 0xFFFF5555 : EditBox.DEFAULT_TEXT_COLOR);
            box.setTooltip(Tooltip.create(Component.translatable("mite.config.monster_spawn_percent.tooltip")));
            box.setResponder(value -> {
                drafts.put(entry.id(), value);
                failure = null;
                try {
                    double percent = Double.parseDouble(value);
                    if (!Double.isFinite(percent) || percent < 0D || percent > 10000D) throw new NumberFormatException();
                    if (percent == 100D) this.values.remove(entry.id());
                    else this.values.put(entry.id(), percent / 100D);
                    invalid.remove(entry.id());
                    box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                } catch (NumberFormatException exception) {
                    invalid.add(entry.id());
                    box.setTextColor(0xFFFF5555);
                }
                updateSave();
            });
            addRenderableWidget(box);
            var reset = addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
                this.values.remove(entry.id());
                drafts.remove(entry.id());
                invalid.remove(entry.id());
                failure = null;
                rebuildRows();
            }).bounds(width - 58, y, 46, 20).build());
            reset.active = editable;
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
        save.active = editable && invalid.isEmpty() && failure == null;
        save.setTooltip(Tooltip.create(failure != null ? failure : Component.translatable(!editable
                ? "mite.config.server_owned" : invalid.isEmpty() ? "mite.config.monster_spawn_save_hint" : "mite.config.monster_spawn_invalid")));
    }

    private void save() {
        if (!MiteConfigClient.editable() || !invalid.isEmpty()) return;
        try {
            saveValues.accept(Map.copyOf(values));
            minecraft.setScreen(parent);
        } catch (RuntimeException exception) {
            failure = Component.literal(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            updateSave();
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 11, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("mite.config.monster_spawn_name"), 12, 60, 0xCCCCCC);
        graphics.drawString(font, Component.translatable("mite.config.monster_spawn_percent"), width - 124, 60, 0xCCCCCC);
        for (var row : visible) {
            var entry = row.entry();
            int nameWidth = Math.max(20, width - 146);
            graphics.drawString(font, font.plainSubstrByWidth(entry.name().getString(), nameWidth), 12, row.y() + 2, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(entry.id(), nameWidth), 12, row.y() + 14, 0x999999);
            Component baseline = entry.baseWeights().isEmpty() ? Component.translatable("mite.config.monster_spawn_conditional")
                    : Component.translatable("mite.config.monster_spawn_baseline", entry.baseWeights().stream()
                            .map(Object::toString).collect(java.util.stream.Collectors.joining(" / ")));
            graphics.drawString(font, font.plainSubstrByWidth(baseline.getString(), width - 24), 12, row.y() + 30, 0xBBBBBB);
        }
        int y = height - 86;
        for (var line : font.split(Component.translatable("mite.config.monster_spawn_hint"), width - 24).stream().limit(2).toList()) {
            graphics.drawString(font, line, 12, y, 0xAAAAAA);
            y += font.lineHeight + 2;
        }
        graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, entries.size()),
                width / 2, height - 48, 0xCCCCCC);
        for (var row : visible) if (mouseX >= 12 && mouseX < width - 130 && mouseY >= row.y() && mouseY < row.y() + 28)
            graphics.renderTooltip(font, Component.literal(row.entry().id()).append("\n")
                    .append(Component.translatable("mite.config.monster_spawn_details")), mouseX, mouseY);
    }

    private static String number(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
