package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.GameplayConfigExport;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Shared configuration entry point for MITE and its optional addons. @author NingMeng123 */
public final class MiteSettingsScreen extends Screen {
    public enum Category {
        WORLD, CREATURES, COMBAT, SURVIVAL, SYSTEM;

        private String key() {
            return "mite.config.category." + name().toLowerCase(Locale.ROOT);
        }

        public Component title() {
            return Component.translatable(key());
        }

        private Component description() {
            return Component.translatable(key() + ".tooltip");
        }

        static Category forConfigSection(String section) {
            return switch (section) {
                case "monsters", "monster_spawning" -> CREATURES;
                case "combat", "enchantments" -> COMBAT;
                case "performance" -> SYSTEM;
                default -> WORLD;
            };
        }
    }

    public record CategoryEntry(Component label, Component description, boolean available, Function<Screen, Screen> open) {}

    private static final List<Function<Category, List<CategoryEntry>>> CATEGORY_PROVIDERS = new ArrayList<>();
    private static final int MENU_TOP = 64;
    private static final int CATEGORY_STEP = 26;
    private static final int RESULT_STEP = 34;
    private final ModContainer container;
    private final Screen parent;
    private final List<Button> categories = new ArrayList<>();
    private final List<AbstractWidget> results = new ArrayList<>();
    private List<ConfigSearchIndex.Entry> matches = List.of();
    private List<ConfigSearchIndex.Entry> index = List.of();
    private String query = "";
    private EditBox search;
    private int menuTop;
    private int page;
    private int pageCount = 1;

    public MiteSettingsScreen(ModContainer container, Screen parent) {
        super(Component.translatable("mite.configuration.title"));
        this.container = container;
        this.parent = parent;
    }

    /** Client-side addons can contribute links while keeping ownership of their editors and values. */
    public static void registerCategoryProvider(Function<Category, List<CategoryEntry>> provider) {
        CATEGORY_PROVIDERS.add(provider);
    }

    @Override protected void init() {
        categories.clear();
        results.clear();
        index = ConfigSearchIndex.entries(this);
        // Older ITE builds add their own header button, including a second row at narrow widths.
        boolean legacyIte = ModList.get().getModContainerById("modern_ite")
                .flatMap(addon -> addon.getCustomExtension(IConfigScreenFactory.class)).isPresent();
        menuTop = legacyIte ? 88 : MENU_TOP;
        int menuWidth = Math.min(300, width - 32);
        int left = (width - menuWidth) / 2;
        search = addRenderableWidget(new EditBox(font, left, menuTop - 29, menuWidth, 20,
                Component.translatable("mite.config.search_all")));
        search.setMaxLength(200);
        search.setHint(Component.translatable("mite.config.search_all"));
        search.setTooltip(Tooltip.create(Component.translatable("mite.config.search_all.tooltip")));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            page = 0;
            matches = index.stream().filter(entry -> entry.matches(query)).toList();
            rebuildResults();
        });

        for (Category category : Category.values()) {
            var entries = new ArrayList<>(baseEntries(category));
            CATEGORY_PROVIDERS.forEach(provider -> entries.addAll(provider.apply(category)));
            if (entries.isEmpty()) continue;
            categories.add(addRenderableWidget(Button.builder(category.title(),
                            button -> minecraft.setScreen(new CategoryScreen(this, category, List.copyOf(entries))))
                    .bounds(left, menuTop + categories.size() * CATEGORY_STEP, menuWidth, 20)
                    .tooltip(Tooltip.create(category.description())).build()));
        }

        int footerWidth = (menuWidth - 8) / 2;
        var export = addRenderableWidget(Button.builder(Component.translatable("mite.config.export"),
                        button -> exportConfiguration()).bounds(left, height - 28, footerWidth, 20).build());
        export.active = MiteConfigClient.localEditable();
        export.setTooltip(Tooltip.create(Component.translatable(export.active
                ? "mite.config.export.tooltip" : "mite.config.export.remote")));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left + footerWidth + 8, height - 28, footerWidth, 20).build());
        matches = query.isBlank() ? List.of() : index.stream().filter(entry -> entry.matches(query)).toList();
        rebuildResults();
    }

    private List<CategoryEntry> baseEntries(Category category) {
        return switch (category) {
            case WORLD -> List.of(
                    entry("mite.config.ore_generation", OreGenerationSettingsScreen::new),
                    entry("mite.config.structures", "structures", key -> true),
                    entry("mite.config.gravel_drops", GravelDropSettingsScreen::new));
            case CREATURES -> List.of(
                    entry("mite.config.spawning_rules.button", MonsterSpawningRulesScreen::new),
                    entry("mite.config.monster_spawning", screen -> ConfigSearchIndex.monsterScreen(screen, "")),
                    entry("mite.config.monster_formulas", MonsterGrowthSettingsScreen::new),
                    entry("mite.config.monsters", "monsters", key -> key.endsWith("_multiplier")));
            case COMBAT -> List.of(
                    entry("mite.config.combat", "combat", key -> true),
                    entry("mite.config.registry_enchantments", EnchantmentSettingsScreen::new),
                    new CategoryEntry(Component.translatable("mite.config.enchantments"),
                            Component.translatable("mite.config.enchantments.tooltip"), MiteConfigClient.localEditable(),
                            screen -> SingleConfigValueScreen.createSection(screen, gameplayConfig(), "enchantments", key -> true,
                                    Component.translatable("mite.config.enchantments"))));
            case SURVIVAL -> List.of();
            case SYSTEM -> List.of(entry("mite.config.performance", "performance", key -> true));
        };
    }

    private static CategoryEntry entry(String key, Function<Screen, Screen> open) {
        return new CategoryEntry(Component.translatable(key), Component.translatable(key + ".tooltip"), true, open);
    }

    private CategoryEntry entry(String key, String section, Predicate<String> include) {
        Component label = Component.translatable(key);
        boolean remote = MiteConfigClient.remoteServer();
        boolean available = !remote || MiteConfigClient.editable() && MiteConfigClient.gameplayValues().keySet().stream()
                .anyMatch(path -> path.startsWith(section + "."));
        return new CategoryEntry(label, Component.translatable(key + ".tooltip"), available,
                screen -> remote ? ServerGameplaySettingsScreen.forSection(screen, section, label)
                        : SingleConfigValueScreen.createSection(screen, gameplayConfig(), section, include, label));
    }

    private ModConfig gameplayConfig() {
        return ModConfigs.getConfigSet(ModConfig.Type.COMMON).stream()
                .filter(config -> config.getModId().equals(container.getModId()) && config.getSpec() == MiteConfig.SPEC)
                .findFirst().orElseThrow();
    }

    private void rebuildResults() {
        if (getFocused() != search) setFocused(null);
        results.forEach(this::removeWidget);
        results.clear();
        boolean searching = !query.isBlank();
        categories.forEach(button -> button.visible = !searching);
        if (!searching) return;
        int rows = resultRows();
        pageCount = Math.max(1, (matches.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pageCount - 1);
        int resultWidth = Math.min(360, width - 32);
        int left = (width - resultWidth) / 2;
        for (int i = page * rows; i < Math.min(matches.size(), (page + 1) * rows); i++) {
            var entry = matches.get(i);
            int y = menuTop + (i - page * rows) * RESULT_STEP;
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
        results.add(addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuildResults(); })
                .bounds(left, height - 54, 24, 20).build()));
        results.getLast().active = page > 0;
        results.add(addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuildResults(); })
                .bounds(left + resultWidth - 24, height - 54, 24, 20).build()));
        results.getLast().active = page + 1 < pageCount;
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

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        if (!query.isBlank()) {
            int resultWidth = Math.min(360, width - 32);
            int rows = resultRows();
            for (int i = page * rows; i < Math.min(matches.size(), (page + 1) * rows); i++)
                graphics.drawString(font, font.plainSubstrByWidth(matches.get(i).category().getString(), resultWidth),
                        (width - resultWidth) / 2, menuTop + (i - page * rows) * RESULT_STEP + 22, 0xAAAAAA);
            if (matches.isEmpty()) graphics.drawCenteredString(font, Component.translatable("mite.config.search_all.empty"),
                    width / 2, menuTop + 12, 0xCCCCCC);
            graphics.drawCenteredString(font, Component.translatable("mite.config.page", page + 1, pageCount, matches.size()),
                    width / 2, height - 48, 0xCCCCCC);
        } else if (MiteConfigClient.remoteServer() && categories.getLast().getBottom() < height - 44) {
            graphics.drawCenteredString(font, Component.translatable("mite.config.menu.server_view"),
                    width / 2, height - 44, 0xCCCCCC);
        }
    }

    private int resultRows() {
        return Math.max(1, (height - 54 - menuTop) / RESULT_STEP);
    }

    @Override public void onClose() {
        minecraft.setScreen(parent);
    }

    private static final class CategoryScreen extends Screen {
        private static final int TOP = 42;
        private static final int STEP = 23;
        private final Screen parent;
        private final List<CategoryEntry> entries;
        private int page;
        private int pageCount;

        private CategoryScreen(Screen parent, Category category, List<CategoryEntry> entries) {
            super(category.title());
            this.parent = parent;
            this.entries = entries;
        }

        @Override protected void init() {
            int rows = Math.max(1, (height - 34 - TOP) / STEP);
            if (entries.size() > rows) rows = Math.max(1, (height - 60 - TOP) / STEP);
            pageCount = (entries.size() + rows - 1) / rows;
            page = Math.clamp(page, 0, pageCount - 1);
            int menuWidth = Math.min(300, width - 32);
            int left = (width - menuWidth) / 2;
            for (int i = page * rows; i < Math.min(entries.size(), (page + 1) * rows); i++) {
                CategoryEntry entry = entries.get(i);
                Component tooltip = entry.available() ? entry.description() : entry.description().copy().append("\n")
                        .append(Component.translatable("mite.config.server_owned"));
                var button = addRenderableWidget(Button.builder(entry.label(),
                                ignored -> minecraft.setScreen(entry.open().apply(this)))
                        .bounds(left, TOP + (i - page * rows) * STEP, menuWidth, 20)
                        .tooltip(Tooltip.create(tooltip)).build());
                button.active = entry.available();
            }
            if (pageCount > 1) {
                addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                        .bounds(left, height - 54, 24, 20).build()).active = page > 0;
                addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                        .bounds(left + menuWidth - 24, height - 54, 24, 20).build()).active = page + 1 < pageCount;
            }
            addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                    .bounds(width / 2 - 100, height - 28, 200, 20).build());
        }

        private void changePage(int step) {
            page += step;
            setFocused(null);
            clearWidgets();
            init();
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
            if (pageCount > 1) graphics.drawCenteredString(font,
                    Component.translatable("mite.config.page", page + 1, pageCount, entries.size()),
                    width / 2, height - 48, 0xCCCCCC);
        }

        @Override public void onClose() {
            minecraft.setScreen(parent);
        }
    }
}
