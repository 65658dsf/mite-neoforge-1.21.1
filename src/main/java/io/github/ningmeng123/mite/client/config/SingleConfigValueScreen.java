package io.github.ningmeng123.mite.client.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.RestartType;

/** Opens a selection of config values with the native editor, undo history and persistence. @author NingMeng123 */
public final class SingleConfigValueScreen extends ConfigurationSectionScreen {
    private SingleConfigValueScreen(Context context, Component title) {
        super(context, title);
        needsRestart = context.modConfig().getType() == ModConfig.Type.STARTUP ? RestartType.GAME : RestartType.NONE;
    }

    public static Screen create(Screen parent, ModConfig config, List<String> path, Component title) {
        if (!(config.getSpec() instanceof ModConfigSpec spec) || path.isEmpty())
            throw new IllegalArgumentException("A config value path is required");
        List<String> location = List.copyOf(path);
        UnmodifiableConfig values = spec.getValues();
        UnmodifiableConfig definitions = spec.getSpec();
        for (String key : location.subList(0, location.size() - 1)) {
            if (!(values.valueMap().get(key) instanceof UnmodifiableConfig sectionValues)
                    || !(definitions.valueMap().get(key) instanceof UnmodifiableConfig sectionDefinitions))
                throw new IllegalArgumentException("Unknown config section: " + String.join(".", location));
            values = sectionValues;
            definitions = sectionDefinitions;
        }
        String key = location.getLast();
        var entry = values.entrySet().stream().filter(candidate -> candidate.getKey().equals(key))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown config value: " + String.join(".", location)));
        if (!(entry.getRawValue() instanceof ModConfigSpec.ConfigValue<?>)
                || !(definitions.valueMap().get(key) instanceof ModConfigSpec.ValueSpec definition))
            throw new IllegalArgumentException("Not a config value: " + String.join(".", location));
        // Native reset examines every context entry, including entries hidden by a Filter.
        // Narrow the context itself so resetting this result cannot reset its siblings.
        Context context = new Context(config.getModId(), parent, config, spec, Set.of(entry), Map.of(key, definition),
                List.copyOf(location.subList(0, location.size() - 1)), (ignored, name, original) -> original);
        return new SingleConfigValueScreen(context, title);
    }

    static Screen createSection(Screen parent, ModConfig config, String section, Predicate<String> include, Component title) {
        var spec = (ModConfigSpec) config.getSpec();
        UnmodifiableConfig values = spec.getValues().get(section);
        UnmodifiableConfig definitions = spec.getSpec().get(section);
        var entries = new LinkedHashSet<UnmodifiableConfig.Entry>();
        var valueSpecs = new LinkedHashMap<String, Object>();
        for (var entry : values.entrySet()) {
            String key = entry.getKey();
            if (include.test(key) && entry.getRawValue() instanceof ModConfigSpec.ConfigValue<?>) {
                entries.add(entry);
                valueSpecs.put(key, definitions.valueMap().get(key));
            }
        }
        // Reset and undo operate on the scoped entries, even when the TOML contains other settings.
        var context = new Context(config.getModId(), parent, config, spec, entries, valueSpecs, List.of(section),
                (ignored, name, original) -> original);
        return new SingleConfigValueScreen(context, title);
    }

    @Override protected void repositionElements() {
        List<Button> footer = java.util.stream.Stream.of(undoButton, resetButton, doneButton)
                .filter(java.util.Objects::nonNull).toList();
        footer.forEach(button -> button.setWidth(Button.SMALL_WIDTH));
        super.repositionElements();
        int gap = 8;
        int available = width - 24;
        if (footer.size() * Button.SMALL_WIDTH + gap * (footer.size() - 1) <= available) return;
        int buttonWidth = Math.max(1, (available - gap * (footer.size() - 1)) / footer.size());
        int x = (width - buttonWidth * footer.size() - gap * (footer.size() - 1)) / 2;
        for (Button button : footer) {
            button.setWidth(buttonWidth);
            button.setX(x);
            x += buttonWidth + gap;
        }
    }
}
