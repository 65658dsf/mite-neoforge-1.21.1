package io.github.ningmeng123.mite.client.config;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.GameplayConfigExport;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/** Compact entry point for gameplay settings and the offline/live enchantment catalog. @author NingMeng123 */
public final class MiteSettingsScreen extends Screen {
    private final ModContainer container;
    private final Screen parent;
    public MiteSettingsScreen(ModContainer container, Screen parent) {
        super(Component.translatable("mite.configuration.title"));
        this.container = container;
        this.parent = parent;
    }
    @Override protected void init() {
        Component exportLabel = Component.translatable("mite.config.export");
        int exportWidth = Math.min(110, Math.max(78, font.width(exportLabel) + 16));
        var export = addRenderableWidget(Button.builder(exportLabel,
                button -> exportConfiguration()).bounds(width - exportWidth - 8, 8, exportWidth, 20).build());
        export.active = MiteConfigClient.editable();
        export.setTooltip(Tooltip.create(Component.translatable(export.active
                ? "mite.config.export.tooltip" : "mite.config.export.remote")));
        int y = Math.max(42, height / 2 - 64);
        var ores = addRenderableWidget(Button.builder(Component.translatable("mite.config.ore_generation"),
                button -> minecraft.setScreen(new OreGenerationSettingsScreen(this))).bounds(width / 2 - 130, y, 260, 20).build());
        ores.setTooltip(Tooltip.create(Component.translatable("mite.config.ore_generation.tooltip")));
        var monsters = addRenderableWidget(Button.builder(Component.translatable("mite.config.monster_formulas"),
                button -> minecraft.setScreen(new MonsterGrowthSettingsScreen(this))).bounds(width / 2 - 130, y + 28, 260, 20).build());
        monsters.setTooltip(Tooltip.create(Component.translatable("mite.config.monster_formulas.tooltip")));
        var general = addRenderableWidget(Button.builder(Component.translatable("mite.config.gameplay"),
                button -> minecraft.setScreen(gameplayScreen())).bounds(width / 2 - 130, y + 84, 260, 20).build());
        general.active = MiteConfigClient.editable();
        general.setTooltip(Tooltip.create(Component.translatable(general.active ? "mite.config.gameplay.tooltip" : "mite.config.server_owned")));
        var enchantments = addRenderableWidget(Button.builder(Component.translatable("mite.config.registry_enchantments"),
                button -> minecraft.setScreen(new EnchantmentSettingsScreen(this))).bounds(width / 2 - 130, y + 56, 260, 20).build());
        enchantments.setTooltip(Tooltip.create(Component.translatable("mite.config.registry_enchantments.tooltip")));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(width / 2 - 100, height - 30, 200, 20).build());
    }
    private void exportConfiguration() {
        if (!MiteConfigClient.editable()) return;
        try {
            var file = GameplayConfigExport.export(minecraft.gameDirectory.toPath());
            minecraft.setScreen(new ConfigExportResultScreen(this, file, null));
        } catch (IOException | RuntimeException exception) {
            Mite.LOGGER.warn("Cannot export MITE gameplay settings", exception);
            minecraft.setScreen(new ConfigExportResultScreen(this, null,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }
    private Screen gameplayScreen() {
        return new ConfigurationScreen(container, this, (context, key, original) -> {
            if (context.keylist().isEmpty() && (key.equals("underworld_ores") || key.equals("underworld_generation"))) return null;
            // Formula edits go through the dedicated page's full validation and trial calculations.
            if (context.keylist().equals(java.util.List.of("monsters")) && !key.endsWith("_multiplier")) return null;
            return original;
        });
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawWordWrap(font, Component.translatable(MiteConfigClient.editable() ? "mite.config.scope" : "mite.config.server_owned"),
                Math.max(12, width / 2 - 180), Math.max(160, height / 2 + 52), Math.min(360, width - 24), 0xCCCCCC);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
