package io.github.ningmeng123.mite.client.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.GameplayConfigExport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/** Compact entry point for gameplay settings and the offline/live enchantment catalog. @author NingMeng123 */
public final class MiteSettingsScreen extends Screen {
    private final ModContainer container;
    private final Screen parent;
    private final List<AbstractWidget> categories = new ArrayList<>();
    private final List<AbstractWidget> results = new ArrayList<>();
    private List<ConfigSearchIndex.Entry> index = List.of();
    private List<ConfigSearchIndex.Entry> matches = List.of();
    private String query = "";
    private EditBox search;
    private int page;
    private int pageCount = 1;
    private int matchCount;
    public MiteSettingsScreen(ModContainer container, Screen parent) {
        super(Component.translatable("mite.configuration.title"));
        this.container = container;
        this.parent = parent;
    }
    @Override protected void init() {
        categories.clear();
        results.clear();
        index = ConfigSearchIndex.entries(this);
        Component exportLabel = Component.translatable("mite.config.export");
        int exportWidth = Math.min(110, Math.max(78, font.width(exportLabel) + 16));
        var export = addRenderableWidget(Button.builder(exportLabel,
                button -> exportConfiguration()).bounds(width - exportWidth - 8, 8, exportWidth, 20).build());
        export.active = MiteConfigClient.localEditable();
        export.setTooltip(Tooltip.create(Component.translatable(export.active
                ? "mite.config.export.tooltip" : "mite.config.export.remote")));
        // The addon moves both header controls to y=34 when the title needs its own line.
        // Reserve that complete row even at Minecraft's minimum 320x240 GUI size.
        int y = menuTop();
        var ores = addRenderableWidget(Button.builder(Component.translatable("mite.config.ore_generation"),
                button -> minecraft.setScreen(new OreGenerationSettingsScreen(this))).bounds(width / 2 - 130, y, 128, 20).build());
        ores.setTooltip(Tooltip.create(Component.translatable("mite.config.ore_generation.tooltip")));
        var gravel = addRenderableWidget(Button.builder(Component.translatable("mite.config.gravel_drops"),
                button -> minecraft.setScreen(new GravelDropSettingsScreen(this))).bounds(width / 2 + 2, y, 128, 20).build());
        gravel.setTooltip(Tooltip.create(Component.translatable("mite.config.gravel_drops.tooltip")));
        var monsters = addRenderableWidget(Button.builder(Component.translatable("mite.config.monster_formulas"),
                button -> minecraft.setScreen(new MonsterGrowthSettingsScreen(this))).bounds(width / 2 - 130, y + 24, 260, 20).build());
        monsters.setTooltip(Tooltip.create(Component.translatable("mite.config.monster_formulas.tooltip")));
        var spawnRates = addRenderableWidget(Button.builder(Component.translatable("mite.config.monster_spawning"),
                button -> minecraft.setScreen(new MonsterSpawnSettingsScreen(this, Mite.MOD_ID,
                        () -> io.github.ningmeng123.mite.config.MiteConfig.effectiveSnapshot().monsterSpawnRates(),
                        () -> io.github.ningmeng123.mite.config.MiteConfig.effectiveSnapshot().monsterSpawnWeights(),
                        (rates, weights) -> {
                            if (!io.github.ningmeng123.mite.config.MiteConfig.saveMonsterSpawnSettings(rates, weights))
                                throw new IllegalArgumentException("Invalid monster spawn weights or multipliers");
                        }, null,
                        () -> io.github.ningmeng123.mite.config.MiteConfig.factorySnapshot().monsterSpawnRates(),
                        () -> io.github.ningmeng123.mite.config.MiteConfig.factorySnapshot().monsterSpawnWeights()))).bounds(width / 2 - 130, y + 48, 260, 20).build());
        spawnRates.setTooltip(Tooltip.create(Component.translatable("mite.config.monster_spawning.tooltip")));
        var general = addRenderableWidget(Button.builder(Component.translatable("mite.config.gameplay"),
                button -> minecraft.setScreen(gameplayScreen())).bounds(width / 2 - 130, y + 96, 260, 20).build());
        general.active = MiteConfigClient.editable();
        general.setTooltip(Tooltip.create(Component.translatable(general.active ? "mite.config.gameplay.tooltip" : "mite.config.server_owned")));
        var enchantments = addRenderableWidget(Button.builder(Component.translatable("mite.config.registry_enchantments"),
                button -> minecraft.setScreen(new EnchantmentSettingsScreen(this))).bounds(width / 2 - 130, y + 72, 260, 20).build());
        enchantments.setTooltip(Tooltip.create(Component.translatable("mite.config.registry_enchantments.tooltip")));
        categories.addAll(List.of(ores, gravel, monsters, spawnRates, general, enchantments));
        int searchWidth = Math.min(260, width - 32);
        search = addRenderableWidget(new EditBox(font, (width - searchWidth) / 2, y - 26, searchWidth, 20,
                Component.translatable("mite.config.search_all")));
        search.setMaxLength(200);
        search.setHint(Component.translatable("mite.config.search_all"));
        search.setTooltip(Tooltip.create(Component.translatable("mite.config.search_all.tooltip")));
        search.setValue(query);
        search.setResponder(text -> { query = text; page = 0; updateResults(); });
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(width / 2 - 100, height - 30, 200, 20).build());
        updateResults();
    }

    private void updateResults() {
        if (getFocused() != search) setFocused(null);
        results.forEach(this::removeWidget);
        results.clear();
        boolean searching = !query.isBlank();
        categories.forEach(widget -> widget.visible = !searching);
        if (!searching) return;
        matches = index.stream().filter(entry -> entry.matches(query)).toList();
        matchCount = matches.size();
        int rows = Math.max(1, (height - 64 - menuTop()) / 34);
        pageCount = Math.max(1, (matchCount + rows - 1) / rows);
        page = Math.clamp(page, 0, pageCount - 1);
        int resultWidth = Math.min(360, width - 32);
        int left = (width - resultWidth) / 2;
        for (int i = page * rows; i < Math.min(matchCount, (page + 1) * rows); i++) {
            var entry = matches.get(i);
            int y = menuTop() + (i - page * rows) * 34;
            String name = entry.label().getString();
            var button = Button.builder(Component.literal(font.plainSubstrByWidth(name, resultWidth - 12)),
                    ignored -> { if (entry.available()) minecraft.setScreen(entry.open().get()); })
                    .bounds(left, y, resultWidth, 20).build();
            Component detail = Component.literal(entry.category().getString() + "\n" + entry.id() + "\n")
                    .append(entry.description());
            if (!entry.available()) detail = detail.copy().append("\n").append(Component.translatable("mite.config.server_owned"));
            button.setTooltip(Tooltip.create(entry.label().copy().append("\n").append(detail)));
            button.active = entry.available();
            results.add(addRenderableWidget(button));
        }
        results.add(addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; updateResults(); })
                .bounds(left, height - 56, 24, 20).build()));
        results.get(results.size() - 1).active = page > 0;
        results.add(addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; updateResults(); })
                .bounds(left + resultWidth - 24, height - 56, 24, 20).build()));
        results.get(results.size() - 1).active = page + 1 < pageCount;
    }
    private void exportConfiguration() {
        if (!MiteConfigClient.localEditable()) return;
        try {
            var bundle = GameplayConfigExport.exportBundle(minecraft.gameDirectory.toPath());
            minecraft.setScreen(ConfigExportResultScreen.success(this, bundle));
        } catch (IOException | RuntimeException exception) {
            Mite.LOGGER.warn("Cannot export MITE gameplay settings", exception);
            minecraft.setScreen(new ConfigExportResultScreen(this, null,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }
    private Screen gameplayScreen() {
        if (MiteConfigClient.remoteServer()) return new ServerGameplaySettingsScreen(this);
        return new ConfigurationScreen(container, this, GameplaySectionScreen::new);
    }

    /** Native sections bypass the value filter, so remove groups with dedicated editors here. */
    private static final class GameplaySectionScreen extends ConfigurationScreen.ConfigurationSectionScreen {
        private GameplaySectionScreen(Screen parent, ModConfig.Type type, ModConfig config, Component title) {
            super(parent, type, config, title, GameplaySectionScreen::filterValue);
        }

        private static boolean hasDedicatedEditor(String key) {
            return switch (key) {
                case "underworld_ores", "overworld_ores", "nether_ores",
                        "underworld_generation", "overworld_generation", "nether_generation", "monster_spawning", "gravel_drops" -> true;
                default -> false;
            };
        }

        private static Element filterValue(Context context, String key, Element original) {
            // Formula edits go through the dedicated page's full validation and trial calculations.
            if (context.keylist().equals(java.util.List.of("monsters"))
                    && !key.endsWith("_multiplier")) return null;
            return original;
        }

        @Override protected Element createSection(String key, UnmodifiableConfig subconfig, UnmodifiableConfig subsection) {
            if (context.keylist().isEmpty() && hasDedicatedEditor(key)) return null;
            return super.createSection(key, subconfig, subsection);
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        if (!query.isBlank()) {
            int resultWidth = Math.min(360, width - 32);
            int rows = Math.max(1, (height - 64 - menuTop()) / 34);
            for (int i = page * rows; i < Math.min(matches.size(), (page + 1) * rows); i++)
                graphics.drawString(font, font.plainSubstrByWidth(matches.get(i).category().getString(), resultWidth),
                        (width - resultWidth) / 2, menuTop() + (i - page * rows) * 34 + 22, 0xAAAAAA);
            if (matchCount == 0) graphics.drawCenteredString(font, Component.translatable("mite.config.search_all.empty"),
                    width / 2, menuTop() + 12, 0xCCCCCC);
            graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, matchCount),
                    width / 2, height - 50, 0xCCCCCC);
            return;
        }
        int hintY = menuTop() + 124;
        int availableLines = Math.max(0, (height - 36 - hintY) / font.lineHeight);
        var hint = Component.translatable(!MiteConfigClient.editable() ? "mite.config.server_owned"
                : MiteConfigClient.remoteServer() ? "mite.config.remote.scope"
                : height < 280 ? "mite.config.scope_compact" : "mite.config.scope");
        for (var line : font.split(hint, Math.min(360, width - 24)).stream().limit(availableLines).toList()) {
            graphics.drawString(font, line, Math.max(12, width / 2 - 180), hintY, 0xCCCCCC);
            hintY += font.lineHeight;
        }
    }
    private int menuTop() { return Math.max(88, height / 2 - 78); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
