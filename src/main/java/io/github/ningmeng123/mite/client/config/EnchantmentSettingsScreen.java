package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.config.EnchantmentCatalog;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Edits offline registry metadata and refreshes it from a loaded world. @author NingMeng123 */
public final class EnchantmentSettingsScreen extends Screen {
    private record Entry(String id, int defaultMaxLevel, Component name, boolean registered) {}
    private record VisibleRow(Entry entry, int y) {}
    private final Screen parent;
    private final boolean editable;
    private final boolean worldLoaded;
    private final List<Entry> entries = new ArrayList<>();
    private final List<VisibleRow> visible = new ArrayList<>();
    private final Map<String, Integer> caps;
    private final Set<String> disabled;
    private final Map<String, Double> chances;
    private final Set<String> invalidMaximums = new HashSet<>();
    private final Set<String> invalidChances = new HashSet<>();
    private final Map<String, String> maximumDrafts = new HashMap<>();
    private final Map<String, String> chanceDrafts = new HashMap<>();
    private String query = "";
    private int page;
    private int pageCount = 1;
    private EditBox search;
    private Button save;
    private boolean restoring;

    public EnchantmentSettingsScreen(Screen parent) {
        super(Component.translatable("mite.config.registry_enchantments"));
        this.parent = parent;
        editable = MiteConfigClient.editable();
        var snapshot = MiteConfig.effectiveSnapshot();
        caps = new HashMap<>(snapshot.caps());
        disabled = new HashSet<>(snapshot.disabled());
        chances = new HashMap<>(snapshot.enchantmentChances());
        var mc = net.minecraft.client.Minecraft.getInstance();
        worldLoaded = mc.level != null;
        Set<String> registered = new HashSet<>();
        if (worldLoaded) {
            EnchantmentCatalog.observe(mc.level.registryAccess());
            mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).entrySet()
                    .forEach(entry -> registered.add(entry.getKey().location().toString()));
        }
        Map<String, Entry> known = new HashMap<>();
        EnchantmentCatalog.entries().forEach(entry -> known.put(entry.id(),
                new Entry(entry.id(), entry.defaultMaxLevel(), entry.name(), registered.contains(entry.id()))));
        // Keep hand-written and temporarily unloaded IDs visible without inventing an original maximum.
        Set<String> configured = new HashSet<>(caps.keySet());
        configured.addAll(disabled);
        configured.addAll(chances.keySet());
        configured.forEach(id -> known.putIfAbsent(id, new Entry(id, 0, Component.literal(id), registered.contains(id))));
        entries.addAll(known.values());
        entries.sort(Comparator.comparing(Entry::id));
    }

    @Override protected void init() {
        search = addRenderableWidget(new EditBox(font, 12, 38, Math.max(100, width - 24), 20, Component.translatable("mite.config.search")));
        search.setMaxLength(200);
        search.setHint(Component.translatable("mite.config.search"));
        search.setValue(query);
        search.setResponder(text -> { query = text; page = 0; rebuildRows(); });
        rebuildRows();
    }

    private List<Entry> filtered() {
        String filter = query.toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> entry.id().contains(filter)
                || entry.name().getString().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }
    private void rebuildRows() {
        // Retain the search box so typing does not lose keyboard focus.
        // A scrolled-away edit box must not keep receiving keystrokes after its row is removed.
        if (getFocused() != search) setFocused(null);
        for (var child : List.copyOf(children())) if (child != search) removeWidget(child);
        visible.clear();
        List<Entry> filtered = filtered();
        int rows = Math.max(1, (height - 142) / 54);
        pageCount = Math.max(1, (filtered.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pageCount - 1);
        int controlsX = width - 132;
        int maximumX = maximumX();
        int chanceX = chanceX();
        for (int index = page * rows; index < Math.min(filtered.size(), (page + 1) * rows); index++) {
            Entry entry = filtered.get(index);
            String id = entry.id();
            int y = 78 + (index - page * rows) * 54;
            visible.add(new VisibleRow(entry, y));
            var toggle = addRenderableWidget(Button.builder(toggleLabel(id), button -> {
                if (!disabled.add(id)) disabled.remove(id);
                button.setMessage(toggleLabel(id));
                button.setTooltip(toggleTooltip(id));
            }).bounds(controlsX, y, 58, 20).build());
            toggle.active = editable;
            toggle.setTooltip(toggleTooltip(id));
            var maximum = addRenderableWidget(new EditBox(font, maximumX, y + 26, 48, 20, Component.translatable("mite.config.maximum")));
            maximum.setMaxLength(10);
            maximum.setEditable(editable);
            // Initial display is metadata, not an explicit override. Only user edits enter the responder.
            maximum.setValue(maximumDrafts.getOrDefault(id, displayedMaximum(entry)));
            maximum.setTextColor(invalidMaximums.contains(id) ? 0xFFFF5555 : EditBox.DEFAULT_TEXT_COLOR);
            refreshMaximumTooltip(maximum, entry);
            maximum.setResponder(text -> {
                if (restoring) return;
                // Keep even invalid input while paging, filtering or rebuilding after a resize.
                maximumDrafts.put(id, text);
                try {
                    int level = Integer.parseInt(text);
                    if (level < 0 || level > 255) throw new NumberFormatException();
                    if (level == 0) caps.remove(id); else caps.put(id, level);
                    invalidMaximums.remove(id);
                    maximum.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                } catch (NumberFormatException ignored) {
                    invalidMaximums.add(id);
                    maximum.setTextColor(0xFFFF5555);
                }
                refreshMaximumTooltip(maximum, entry);
                refreshSave();
            });
            var chance = addRenderableWidget(new EditBox(font, chanceX, y + 26, Math.min(70, width - chanceX - 12), 20,
                    Component.translatable("mite.config.enchantment_chance")));
            chance.setMaxLength(24);
            chance.setEditable(editable);
            chance.setValue(chanceDrafts.getOrDefault(id, displayedChance(id)));
            chance.setTextColor(invalidChances.contains(id) ? 0xFFFF5555 : EditBox.DEFAULT_TEXT_COLOR);
            refreshChanceTooltip(chance);
            chance.setResponder(text -> {
                if (restoring) return;
                chanceDrafts.put(id, text);
                try {
                    double probability = Double.parseDouble(text.trim());
                    if (!Double.isFinite(probability) || probability < 0D || probability > 100D) throw new NumberFormatException();
                    if (probability == 100D) chances.remove(id); else chances.put(id, probability);
                    invalidChances.remove(id);
                    chance.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                } catch (NumberFormatException ignored) {
                    invalidChances.add(id);
                    chance.setTextColor(0xFFFF5555);
                }
                refreshSave();
            });
            var reset = addRenderableWidget(Button.builder(Component.translatable("mite.config.reset"), button -> {
                caps.remove(id); disabled.remove(id); chances.remove(id);
                invalidMaximums.remove(id); invalidChances.remove(id);
                maximumDrafts.remove(id); chanceDrafts.remove(id);
                restoring = true;
                try { maximum.setValue(displayedMaximum(entry)); chance.setValue(displayedChance(id)); }
                finally { restoring = false; }
                maximum.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                chance.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                refreshMaximumTooltip(maximum, entry);
                toggle.setMessage(toggleLabel(id));
                toggle.setTooltip(toggleTooltip(id));
                refreshSave();
            }).bounds(controlsX + 64, y, 56, 20).build());
            reset.active = editable;
            reset.setTooltip(Tooltip.create(Component.translatable(editable ? "mite.config.enchantment_reset.tooltip" : "mite.config.server_owned")));
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuildRows(); }).bounds(12, height - 65, 30, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuildRows(); }).bounds(width - 42, height - 65, 30, 20).build());
        next.active = page < pageCount - 1;
        save = addRenderableWidget(Button.builder(Component.translatable("mite.config.save"), button -> {
            if (editable && MiteConfigClient.editable() && invalidCount() == 0) {
                MiteConfig.saveEnchantments(caps, disabled, chances);
                minecraft.setScreen(parent);
            }
        }).bounds(width / 2 - 104, height - 29, 100, 20).build());
        refreshSave();
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).bounds(width / 2 + 4, height - 29, 100, 20).build());
    }
    private String displayedMaximum(Entry entry) {
        return Integer.toString(caps.getOrDefault(entry.id(), entry.defaultMaxLevel()));
    }
    private String displayedChance(String id) {
        double chance = chances.getOrDefault(id, 100D);
        String plain = BigDecimal.valueOf(chance).stripTrailingZeros().toPlainString();
        // Tiny valid configured values must not be truncated to a misleading zero by the input's length limit.
        return plain.length() <= 24 ? plain : Double.toString(chance);
    }
    private int maximumX() { return Math.max(50, 18 + font.width(Component.translatable("mite.config.maximum"))); }
    private int chanceLabelX() { return maximumX() + 60; }
    private int chanceX() { return chanceLabelX() + font.width(Component.translatable("mite.config.enchantment_chance")) + 6; }
    private void refreshChanceTooltip(EditBox chance) {
        Component tooltip = Component.translatable("mite.config.enchantment_chance.tooltip");
        if (!editable) tooltip = tooltip.copy().append("\n").append(Component.translatable("mite.config.server_owned"));
        chance.setTooltip(Tooltip.create(tooltip));
    }
    private void refreshMaximumTooltip(EditBox maximum, Entry entry) {
        Component original = entry.defaultMaxLevel() > 0
                ? Component.literal(Integer.toString(entry.defaultMaxLevel()))
                : Component.translatable("mite.config.enchantment_unknown_maximum");
        maximum.setTooltip(Tooltip.create(Component.translatable("mite.config.maximum.tooltip", original)
                .append("\n").append(Component.translatable(caps.containsKey(entry.id())
                        ? "mite.config.enchantment_override" : "mite.config.enchantment_automatic"))));
    }
    private Component sourceLabel(Entry entry) {
        return Component.translatable(entry.defaultMaxLevel() == 0 ? "mite.config.enchantment_unresolved"
                : entry.registered() ? "mite.config.enchantment_registered"
                : worldLoaded ? "mite.config.enchantment_cached" : "mite.config.enchantment_offline");
    }
    private void refreshSave() {
        if (save == null) return;
        save.active = editable && MiteConfigClient.editable() && invalidCount() == 0;
        Set<String> invalid = new HashSet<>(invalidMaximums);
        invalid.addAll(invalidChances);
        save.setTooltip(invalid.isEmpty() ? null : Tooltip.create(Component.translatable("mite.config.invalid_enchantment_entries", invalidCount())
                .append("\n").append(invalid.stream().sorted().findFirst().orElse(""))));
    }
    private int invalidCount() { return invalidMaximums.size() + invalidChances.size(); }
    private Component toggleLabel(String id) {
        boolean off = disabled.contains(id);
        return Component.translatable(off ? "mite.config.enchantment_disabled" : "mite.config.enchantment_enabled")
                .withStyle(off ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GREEN);
    }
    private Tooltip toggleTooltip(String id) {
        String key = !editable ? "mite.config.server_owned" : disabled.contains(id)
                ? "mite.config.enchantment_enable_hint" : "mite.config.enchantment_disable_hint";
        return Tooltip.create(Component.translatable(key).append("\n").append(id));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        int controlsX = width - 132;
        graphics.drawString(font, Component.translatable("mite.config.enchantment_name"), 12, 64, 0xBBBBBB);
        for (VisibleRow row : visible) {
            var entry = row.entry();
            graphics.drawString(font, font.plainSubstrByWidth(entry.name().getString(), controlsX - 22), 12, row.y() + 1, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(entry.id(), controlsX - 22), 12, row.y() + 12,
                    worldLoaded && !entry.registered() || entry.defaultMaxLevel() == 0 ? 0xBBAA66 : 0x999999);
            graphics.drawString(font, Component.translatable("mite.config.maximum"), 12, row.y() + 32, 0xBBBBBB);
            graphics.drawString(font, Component.translatable("mite.config.enchantment_chance"), chanceLabelX(), row.y() + 32, 0xBBBBBB);
            if (mouseX >= 12 && mouseX < controlsX - 5 && mouseY >= row.y() && mouseY < row.y() + 24) {
                List<FormattedCharSequence> tooltip = new ArrayList<>();
                int tooltipWidth = Math.min(300, width - 24);
                tooltip.addAll(font.split(entry.name(), tooltipWidth));
                tooltip.addAll(font.split(Component.literal(entry.id()), tooltipWidth));
                tooltip.addAll(font.split(sourceLabel(entry), tooltipWidth));
                graphics.renderTooltip(font, tooltip, mouseX, mouseY);
            }
        }
        graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, filtered().size()), width / 2, height - 59, 0xCCCCCC);
        Component hint = invalidCount() > 0 ? Component.translatable("mite.config.invalid_enchantment_entries", invalidCount())
                : Component.translatable(editable ? "mite.config.enchantment_hint" : "mite.config.server_owned");
        graphics.drawCenteredString(font, font.plainSubstrByWidth(hint.getString(), width - 24), width / 2, height - 42,
                invalidCount() == 0 ? 0xBBBBBB : 0xFF5555);
        if (mouseY >= height - 44 && mouseY < height - 31)
            graphics.renderTooltip(font, font.split(hint, Math.min(360, width - 24)), mouseX, mouseY);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0D) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int previous = page;
        page = Math.clamp(page + (scrollY > 0 ? -1 : 1), 0, pageCount - 1);
        if (page != previous) rebuildRows();
        return true;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
