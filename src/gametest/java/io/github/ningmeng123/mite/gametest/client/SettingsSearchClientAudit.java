package io.github.ningmeng123.mite.gametest.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.config.ConfigSearchIndex;
import io.github.ningmeng123.mite.client.config.EnchantmentSettingsScreen;
import io.github.ningmeng123.mite.client.config.GravelDropSettingsScreen;
import io.github.ningmeng123.mite.client.config.MiteSettingsScreen;
import io.github.ningmeng123.mite.client.config.OreGenerationSettingsScreen;
import io.github.ningmeng123.mite.client.config.SingleConfigValueScreen;
import io.github.ningmeng123.mite.config.GravelDropSettings;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen.Context;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.joml.Matrix4f;

/** Opt-in production settings search, native GPU rendering and disposable configuration round trip. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class SettingsSearchClientAudit {
    private static boolean started, finished, changingLanguage;
    private static int ticks, saveTicks;
    private static String originalLanguage;
    private static Double originalReach, originalReduction;
    private static GravelDropSettings.Rates originalGravelRates;
    private static final List<Runnable> restoreSettings = new ArrayList<>();
    private static Path configFile;
    private static byte[] originalFile;
    private static JsonObject report;

    private SettingsSearchClientAudit() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.settingsSearchAudit") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            check(mc.level == null && mc.getConnection() == null && mc.getSingleplayerServer() == null,
                    "Search GUI audit only runs disconnected at the title screen");
            check(++ticks < 2400, "Timed out waiting for the real settings search GUI audit");
            if (mc.getOverlay() != null) return;
            if (started) {
                if (++saveTicks < 12) return;
                finish(mc, null);
                return;
            }
            if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
            if (!(mc.screen instanceof TitleScreen)) return;
            if (!changingLanguage && !mc.getLanguageManager().getSelected().equals("zh_cn")) {
                changingLanguage = true;
                originalLanguage = mc.getLanguageManager().getSelected();
                mc.getLanguageManager().setSelected("zh_cn"); mc.options.languageCode = "zh_cn";
                mc.reloadResourcePacks();
                return;
            }
            started = true;
            report = new JsonObject(); report.addProperty("author", "NingMeng123");
            report.addProperty("minecraft_instance_created", true); report.addProperty("world_created", false);
            report.addProperty("actual_screens_initialized_and_rendered", true);
            report.addProperty("language", mc.getLanguageManager().getSelected()); report.add("screenshots", new JsonArray());
            check(MiteConfig.SPEC.isLoaded(), "The real COMMON configuration is loaded");
            configFile = FMLPaths.CONFIGDIR.get().resolve("mite-common.toml").toAbsolutePath().normalize();
            Path game = mc.gameDirectory.toPath().toAbsolutePath().normalize();
            check(configFile.startsWith(game) && game.toString().contains("verification"),
                    "Only the disposable verification game directory may be edited");
            originalFile = Files.exists(configFile) ? Files.readAllBytes(configFile) : null;
            originalReach = MiteConfig.WEAPON_REACH_MULTIPLIER.get();
            originalReduction = MiteConfig.WEAPON_REACH_REDUCTION.get();
            audit(mc);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void audit(Minecraft mc) throws Exception {
        var container = ModList.get().getModContainerById(Mite.MOD_ID).orElseThrow();
        Screen home = IConfigScreenFactory.getForMod(container.getModInfo()).orElseThrow().createScreen(container, mc.screen);
        check(home instanceof MiteSettingsScreen, "The production config factory opens the actual search homepage");
        mc.setScreen(home); home.init(mc, 320, 240);
        bounds(home); noOverlap(home); capture(mc, home, 320, 240, "settings-home-zh-cn");
        check(search(home).getBottom() <= button(home, "mite.config.category.world").getY(),
                "The actual search field is above the category menu at minimum size");
        boolean ite = ModList.get().isLoaded("modern_ite");
        boolean legacyIte = ModList.get().getModContainerById("modern_ite")
                .flatMap(addon -> addon.getCustomExtension(IConfigScreenFactory.class)).isPresent();
        report.addProperty("modern_ite_loaded", ite);
        report.addProperty("legacy_ite_config_factory_present", legacyIte);
        auditCategories(mc, home, ite && !legacyIte);
        search(home).setValue("攻击距离");
        check(matches(home).stream().anyMatch(entry -> entry.id().equals("mite:combat.weapon_reach_multiplier")),
                "Chinese configuration label search finds the actual reach setting");
        bounds(home); noOverlap(home); capture(mc, home, 320, 240, "settings-search-chinese-zh-cn");
        search(home).setValue("qxyzz_no_configuration_729");
        check(matches(home).isEmpty(), "An unmatched search produces zero real results");
        bounds(home); noOverlap(home); capture(mc, home, 320, 240, "settings-search-empty-zh-cn");
        search(home).setValue("   ");
        check(button(home, "mite.config.category.world").visible, "Whitespace clears searching and restores categories");
        search(home).setValue("mite:");
        check(((Number)field(home, "pageCount")).intValue() > 1, "A broad query has multiple actual result pages");
        press(home, ">");
        check(((Number)field(home, "page")).intValue() == 1 && search(home).getValue().equals("mite:"),
                "Paging changes real results and preserves the search input");
        bounds(home); noOverlap(home); capture(mc, home, 320, 240, "settings-search-second-page-zh-cn");
        home.init(mc, 854, 480);
        check(search(home).getValue().equals("mite:"), "Window resize preserves the actual search input");
        bounds(home); noOverlap(home); capture(mc, home, 854, 480, "settings-search-large-zh-cn");
        search(home).setValue("");
        check(button(home, "mite.config.category.world").visible, "Clearing the search restores category navigation");
        bounds(home); noOverlap(home); capture(mc, home, 854, 480, "settings-home-large-zh-cn");

        search(home).setValue("minecraft:sharpness");
        openOnlyResult(home, "enchantments:minecraft:sharpness");
        check(mc.screen instanceof EnchantmentSettingsScreen, "An enchantment result opens its dedicated production editor");
        check(editBoxes(mc.screen).stream().anyMatch(box -> box.getValue().equals("minecraft:sharpness")),
                "The dedicated enchantment editor is already filtered to the selected result");
        mc.screen.onClose();
        check(mc.screen == home, "Closing a result returns to the same search homepage");

        auditGravelDropSettings(mc, home, ite);
        auditNetherOreSettings(mc, home, ite);
        nativeResetAndSave(mc, home);
        auditScopedReset(mc, home);
        if (ite) {
            mc.setScreen(home); home.init(mc, 854, 480);
            search(home).setValue("longer_day_time_end_day");
            check(matches(home).stream().anyMatch(entry -> entry.id().equals("modern_ite:GameMechanics.longer_day_time_end_day")),
                    "The installed ITE provider exposes the new double-daylight end date");
            openOnlyResult(home, "modern_ite:GameMechanics.longer_day_time_end_day");
            check(mc.screen != home, "The double-daylight end-date search result opens its actual editor");
            bounds(mc.screen); capture(mc, mc.screen, 854, 480, "settings-double-daylight-end-zh-cn");
            mc.screen.onClose();
            report.addProperty("ite_double_daylight_end_date_search_and_navigation", true);
        }
        report.addProperty("minimum_and_large_widget_bounds_without_overlap", true);
        report.addProperty("chinese_key_whitespace_clear_and_unmatched_search", true);
        report.addProperty("multiple_result_pages_and_resize_preserve_query", true);
        report.addProperty("dedicated_result_navigation_preselects_entry", true);
    }

    private static void auditCategories(Minecraft mc, Screen home, boolean ite) throws Exception {
        check(widgets(home).stream().filter(Button.class::isInstance).filter(widget -> widget.visible)
                        .anyMatch(widget -> widget.getMessage().equals(Component.translatable("mite.config.category.survival"))) == ite,
                "The Survival category is available when the ITE extension contributes settings");
        check(button(home, "mite.config.export").visible, "Configuration export stays available from the homepage");
        List<String> groups = new ArrayList<>(List.of("world", "creatures", "combat", "system"));
        if (ite) groups.add("survival");
        for (String group : groups) {
            press(home, "mite.config.category." + group);
            Screen category = mc.screen;
            check(category != home, "The homepage opens its category: " + group);
            for (int[] size : new int[][] {{320, 240}, {854, 480}}) {
                category.init(mc, size[0], size[1]);
                bounds(category); noOverlap(category);
                capture(mc, category, size[0], size[1], "settings-category-" + group + "-zh-cn");
            }
            category.onClose();
            check(mc.screen == home, "Closing a category returns to the settings homepage");
            home.init(mc, 320, 240);
        }
        report.addProperty("group_navigation_and_layout_at_minimum_and_large_sizes", true);
    }

    private static void auditGravelDropSettings(Minecraft mc, Screen home, boolean ite) throws Exception {
        originalGravelRates = GravelDropSettings.localRates();
        rememberSettings(MiteConfig.SPEC.getValues().get("gravel_drops"));
        home.init(mc, 320, 240);
        search(home).setValue("gravel_drops:ordinary/mite:flint_chip");
        check(matches(home).size() == (ite ? 0 : 1), "Search only exposes the flint chip when it can drop separately");
        search(home).setValue("");
        press(home, "mite.config.category.world");
        Screen world = mc.screen;
        press(world, "mite.config.gravel_drops");
        Screen gravel = mc.screen;
        check(gravel instanceof GravelDropSettingsScreen, "The gravel settings category opens its dedicated editor");
        gravel.init(mc, 320, 240);
        bounds(gravel);
        noOverlap(gravel);
        capture(mc, gravel, 320, 240, "settings-gravel-drops-zh-cn");
        press(gravel, "mite.config.reset");
        EditBox flint = input(gravel, "item.minecraft.flint");
        flint.setValue("101");
        check(!button(gravel, "mite.config.save").active, "An out-of-range gravel drop chance prevents saving");
        flint.setValue("14");
        check(!button(gravel, "mite.config.save").active, "A gravel draft must total 100 before saving");
        press(gravel, "mite.config.gravel_drops.balance");
        press(gravel, "mite.config.save");
        check(mc.screen == world && GravelDropSettings.localRates().ordinary().get("minecraft:flint") == 14D,
                "The balanced draft saves the new flint chance and returns to the World category");
        var disk = new TomlParser().parse(Files.readString(configFile));
        check(disk.get("gravel_drops.rates") instanceof List<?> entries && !entries.isEmpty(),
                "The configured rates persist to the COMMON file");

        world.onClose();
        check(mc.screen == home, "Closing the World category restores the homepage search");
        search(home).setValue("gravel_drops:nether/minecraft:gold_nugget");
        openOnlyResult(home, "gravel_drops:nether/minecraft:gold_nugget");
        gravel = mc.screen;
        gravel.init(mc, 320, 240);
        check(!button(gravel, "block.mite.nether_gravel").active
                        && Double.parseDouble(input(gravel, "item.minecraft.gold_nugget").getValue())
                                == GravelDropSettings.defaults(ite).nether().get("minecraft:gold_nugget"),
                "Selecting a result opens the Nether tab with the requested product visible");
        press(gravel, "mite.config.reset");
        press(gravel, "mite.config.save");
        check(mc.screen == home && GravelDropSettings.localRates().equals(GravelDropSettings.defaults(ite)),
                "Restoring defaults and saving resets both types of gravel");
        report.addProperty("gravel_drop_visibility_validation_balance_save_and_reset", true);
    }

    private static void auditNetherOreSettings(Minecraft mc, Screen home, boolean ite) throws Exception {
        for (String section : List.of("overworld_ores", "underworld_ores", "nether_ores",
                "overworld_generation", "underworld_generation", "nether_generation")) {
            rememberSettings(MiteConfig.SPEC.getValues().get(section));
        }

        mc.setScreen(home);
        home.init(mc, 320, 240);
        String quartzSetting = "mite:nether_ores.quartz";
        search(home).setValue(quartzSetting);
        openOnlyResult(home, quartzSetting);
        Screen ore = mc.screen;
        check(ore instanceof OreGenerationSettingsScreen, "A Nether ore result opens the dedicated ore editor");
        ore.init(mc, 320, 240);
        EditBox search = oreInput(ore, "ore_search");
        check(search.getValue().equals("minecraft:nether_quartz_ore"), "Homepage search selects quartz by its registered block ID");
        String currentNether = Component.translatable("mite.config.ore_dimension.selected",
                Component.translatable("mite.config.ore_dimension.nether")).getString();
        check(widgets(ore).stream().anyMatch(widget -> widget instanceof Button tab && !tab.active
                        && tab.getMessage().getString().equals(currentNether)), "The Nether tab is selected on result navigation");
        bounds(ore);
        noOverlap(ore);
        capture(mc, ore, 320, 240, "settings-ore-nether-zh-cn");

        oreInput(ore, "ore_search").setValue("modern_ite:nether_adamantium_ore");
        check(editBoxes(ore).size() == (ite ? 3 : 1),
                "Nether adamantium is selectable when Modern ITE supplies the block");
        if (ite) {
            oreInput(ore, "ore_multiplier").setValue("0");
            oreInput(ore, "ore_height_range").setValue("[28,48]");
        }
        oreInput(ore, "ore_search").setValue("minecraft:nether_quartz_ore");
        oreInput(ore, "ore_multiplier").setValue("2.5");
        oreInput(ore, "ore_height_range").setValue("[40,24]");
        check(!button(ore, "mite.config.save").active, "An inverted Nether height range disables Save");

        press(ore, "mite.config.ore_dimension.overworld");
        oreInput(ore, "ore_search").setValue("minecraft:coal_ore");
        oreInput(ore, "ore_multiplier").setValue("0.75");
        press(ore, "mite.config.ore_dimension.underworld");
        oreInput(ore, "ore_search").setValue("minecraft:coal_ore");
        oreInput(ore, "ore_multiplier").setValue("1.25");
        check(!button(ore, "mite.config.save").active, "The invalid hidden Nether draft prevents saving the other dimensions");

        press(ore, "mite.config.ore_dimension.nether");
        oreInput(ore, "ore_search").setValue("minecraft:nether_quartz_ore");
        check(oreInput(ore, "ore_multiplier").getValue().equals("2.5")
                        && oreInput(ore, "ore_height_range").getValue().equals("[40,24]"),
                "Switching tabs and searching preserves the valid and invalid Nether drafts");
        press(ore, "mite.config.reset");
        check(Double.parseDouble(oreInput(ore, "ore_multiplier").getValue()) == MiteConfig.factoryOreMultiplier(Kind.QUARTZ, Dimension.NETHER, false)
                        && oreInput(ore, "ore_height_range").getValue().equals(MiteConfig.factoryOreHeight(Kind.QUARTZ, Dimension.NETHER, false).toString()),
                "Restoring quartz replaces both draft values with factory defaults");
        oreInput(ore, "ore_multiplier").setValue("2.5");
        oreInput(ore, "ore_height_range").setValue("[24,40]");
        ore.init(mc, 854, 480);
        check(oreInput(ore, "ore_multiplier").getValue().equals("2.5") && oreInput(ore, "ore_height_range").getValue().equals("[24,40]"),
                "The Nether quartz draft survives changing the screen size");
        bounds(ore);
        noOverlap(ore);
        capture(mc, ore, 854, 480, "settings-ore-nether-draft-zh-cn");
        press(ore, "mite.config.save");
        check(mc.screen == home, "Saving the ore editor returns to the originating settings search");
        var disk = new TomlParser().parse(Files.readString(configFile));
        check(((Number)disk.get("nether_ores.quartz")).doubleValue() == 2.5D
                        && ((Number)disk.get("overworld_ores.coal")).doubleValue() == .75D
                        && ((Number)disk.get("underworld_ores.coal")).doubleValue() == 1.25D,
                "Save persists distinct ore rates for all three dimensions to TOML");
        check("[24,40]".equals(disk.get("nether_generation.quartz.height_range")),
                "Save persists the Nether height to TOML");
        var quartzHeight = MiteConfig.oreHeightOverride(Kind.QUARTZ, Dimension.NETHER, false);
        check(new OreHeightRange(24, 40).equals(quartzHeight),
                "The saved Nether height becomes the effective generation range: " + quartzHeight);
        if (ite) {
            check(MiteConfig.oreMultiplier(Kind.NETHER_ADAMANTIUM, Dimension.NETHER, false) == 0D
                            && new OreHeightRange(28, 48)
                                    .equals(MiteConfig.oreHeightOverride(Kind.NETHER_ADAMANTIUM, Dimension.NETHER, false)),
                    "Saving quartz also commits the hidden Nether adamantium draft");
        }
        home.init(mc, 854, 480);
        openOnlyResult(home, quartzSetting);
        check(oreInput(mc.screen, "ore_multiplier").getValue().equals("2.5")
                        && oreInput(mc.screen, "ore_height_range").getValue().equals("[24,40]"),
                "The freshly opened Nether page reads the saved multiplier and height");
        mc.screen.onClose();
        report.addProperty("nether_ore_search_tab_draft_reset_save_and_reopen", true);
        report.addProperty("nether_adamantium_availability_matches_modern_ite", true);
    }

    private static void rememberSettings(UnmodifiableConfig section) {
        for (var entry : section.entrySet()) {
            if (entry.getValue() instanceof UnmodifiableConfig subsection) rememberSettings(subsection);
            else if (entry.getValue() instanceof ModConfigSpec.ConfigValue<?> value) rememberSetting(value);
        }
    }

    private static void auditScopedReset(Minecraft mc, Screen home) throws Exception {
        rememberSetting(MiteConfig.MONSTER_GROWTH);
        rememberSetting(MiteConfig.MONSTER_HEALTH_DAILY_PERCENT);
        MiteConfig.MONSTER_GROWTH.set(MiteConfig.MONSTER_GROWTH.getDefault() == 1.7D ? 1.5D : 1.7D);
        double daily = MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.getDefault() + 1D;
        MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.set(daily);
        search(home).setValue("");
        press(home, "mite.config.category.creatures");
        Screen creatures = mc.screen;
        press(creatures, "mite.config.monsters");
        Screen growth = mc.screen;
        check(growth instanceof SingleConfigValueScreen, "Growth Multipliers opens its scoped native configuration screen");
        var contextField = ConfigurationSectionScreen.class.getDeclaredField("context");
        contextField.setAccessible(true);
        Context context = (Context) contextField.get(growth);
        var keys = context.entries().stream().map(UnmodifiableConfig.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        check(context.keylist().equals(List.of("monsters"))
                        && keys.equals(Set.of("growth_multiplier", "health_growth_multiplier", "damage_growth_multiplier"))
                        && context.valueSpecs().keySet().equals(keys),
                "The editor scopes both entries and definitions to the three growth multipliers");
        growth.init(mc, 320, 240);
        bounds(growth); capture(mc, growth, 320, 240, "settings-growth-multipliers-zh-cn");
        Button reset = nativeButton(growth, "resetButton");
        check(reset.active, "The scoped reset is available after changing a multiplier");
        reset.onPress();
        check(MiteConfig.MONSTER_GROWTH.get().equals(MiteConfig.MONSTER_GROWTH.getDefault())
                        && MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.get() == daily,
                "Reset restores multipliers and preserves the daily growth used by the separate formula editor");
        nativeButton(growth, "doneButton").onPress();
        check(mc.screen == creatures, "Saving the scoped editor returns to its Creatures category");
        creatures.onClose();
        check(mc.screen == home, "Closing the Creatures category returns to the homepage");
        report.addProperty("native_section_reset_preserves_unrelated_settings", true);
    }

    private static <T> void rememberSetting(ModConfigSpec.ConfigValue<T> value) {
        T original = value.get();
        restoreSettings.add(() -> value.set(original));
    }

    private static void nativeResetAndSave(Minecraft mc, Screen home) throws Exception {
        MiteConfig.WEAPON_REACH_MULTIPLIER.set(1.7D);
        MiteConfig.WEAPON_REACH_REDUCTION.set(.37D);
        MiteConfig.SPEC.save();
        MiteConfig.refreshEnchantmentRules();
        mc.setScreen(home); home.init(mc, 320, 240);
        search(home).setValue("weapon_reach_multiplier");
        openOnlyResult(home, "mite:combat.weapon_reach_multiplier");
        Screen selected = mc.screen;
        check(selected instanceof SingleConfigValueScreen, "A scalar search result opens the production single-value native editor");
        selected.init(mc, 320, 240);
        bounds(selected); capture(mc, selected, 320, 240, "settings-native-single-value-zh-cn");
        Button reset = nativeButton(selected, "resetButton");
        check(reset.active, "The real native Reset button is active for a non-default result");
        reset.onPress();
        check(MiteConfig.WEAPON_REACH_MULTIPLIER.get().equals(MiteConfig.WEAPON_REACH_MULTIPLIER.getDefault()),
                "The native Reset button restores the selected setting");
        check(MiteConfig.WEAPON_REACH_REDUCTION.get() == .37D,
                "Resetting one search result preserves its non-default sibling setting");
        List<EditBox> inputs = editBoxes(selected);
        check(inputs.size() == 1, "Only the selected numeric configuration input exists in the actual native context");
        inputs.getFirst().setValue("1.25");
        check(MiteConfig.WEAPON_REACH_MULTIPLIER.get() == 1.25D, "The native input applies its valid number");
        nativeButton(selected, "doneButton").onPress();
        check(mc.screen == home, "Native Done saves and returns to the search homepage");
        String toml = Files.readString(configFile);
        check(toml.contains("weapon_reach_multiplier = 1.25") && toml.contains("weapon_reach_reduction = 0.37"),
                "Native Done persists the selected setting and its untouched sibling to real TOML");
        home.init(mc, 854, 480);
        check(search(home).getValue().equals("weapon_reach_multiplier"), "Returning from native saving keeps the original query");
        openOnlyResult(home, "mite:combat.weapon_reach_multiplier");
        Screen reopened = mc.screen; reopened.init(mc, 854, 480);
        check(editBoxes(reopened).size() == 1 && Double.parseDouble(editBoxes(reopened).getFirst().getValue()) == 1.25D,
                "A newly constructed native page reads back the saved setting");
        bounds(reopened); capture(mc, reopened, 854, 480, "settings-native-saved-reopened-zh-cn");
        reopened.onClose();
        report.addProperty("native_single_value_reset_preserves_nondefault_sibling", true);
        report.addProperty("native_input_done_save_toml_and_fresh_screen_readback", true);
    }

    private static Object field(Screen screen, String name) throws Exception {
        var field = screen.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(screen);
    }
    @SuppressWarnings("unchecked") private static List<ConfigSearchIndex.Entry> matches(Screen screen) throws Exception {
        return (List<ConfigSearchIndex.Entry>)field(screen, "matches");
    }
    private static void openOnlyResult(Screen home, String id) throws Exception {
        check(matches(home).size() == 1 && matches(home).getFirst().id().equals(id), "A precise query selects only " + id);
        Button result = (Button)((List<?>)field(home, "results")).getFirst();
        check(result.active && result.visible, "The selected search result is an available real button");
        result.onPress();
    }
    private static List<AbstractWidget> widgets(Screen screen) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
    }
    private static List<EditBox> editBoxes(Screen screen) {
        List<EditBox> boxes = new ArrayList<>();
        Set<GuiEventListener> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var child : screen.children()) collectBoxes(child, boxes, visited);
        return boxes;
    }
    private static void collectBoxes(GuiEventListener listener, List<EditBox> result, Set<GuiEventListener> visited) {
        if (!visited.add(listener)) return;
        if (listener instanceof EditBox box) result.add(box);
        if (listener instanceof ContainerEventHandler container)
            for (var child : container.children()) collectBoxes(child, result, visited);
    }
    private static EditBox search(Screen screen) {
        return widgets(screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getMessage().getString().equals(Component.translatable("mite.config.search_all").getString())).findFirst().orElseThrow();
    }
    private static EditBox oreInput(Screen screen, String key) {
        return input(screen, "mite.config." + key);
    }
    private static EditBox input(Screen screen, String key) {
        String label = Component.translatable(key).getString();
        return editBoxes(screen).stream().filter(box -> box.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static Button nativeButton(Screen screen, String field) throws Exception {
        var member = ConfigurationSectionScreen.class.getDeclaredField(field); member.setAccessible(true); return (Button)member.get(screen);
    }
    private static Button button(Screen screen, String key) {
        String label = key.equals("<") || key.equals(">") ? key : Component.translatable(key).getString();
        return widgets(screen).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(value -> value.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static void press(Screen screen, String key) { Button button = button(screen, key); check(button.active, "Active button " + key); button.onPress(); }
    private static void bounds(Screen screen) {
        for (AbstractWidget widget : widgets(screen)) if (widget.visible)
            check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width && widget.getBottom() <= screen.height,
                    "Visible native widget fits " + screen.width + "x" + screen.height + ": " + widget.getMessage().getString());
    }
    private static void noOverlap(Screen screen) {
        var visible = widgets(screen).stream().filter(widget -> widget.visible).toList();
        for (int i = 0; i < visible.size(); i++) for (int j = i + 1; j < visible.size(); j++) {
            var a = visible.get(i); var b = visible.get(j);
            check(a.getRight() <= b.getX() || b.getRight() <= a.getX() || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(),
                    "Homepage widgets do not overlap: " + a.getMessage().getString() + " / " + b.getMessage().getString());
        }
    }

    private static void capture(Minecraft mc, Screen screen, int width, int height, String name) throws Exception {
        TextureTarget target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        var originalTarget = mc.getMainRenderTarget();
        var targetField = Minecraft.class.getDeclaredField("mainRenderTarget"); targetField.setAccessible(true);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var window = mc.getWindow(); int oldWidth = window.getWidth(), oldHeight = window.getHeight();
        double scale = window.getGuiScale(); int originalBlur = mc.options.menuBackgroundBlurriness().get();
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try {
            targetField.set(mc, target); mc.options.menuBackgroundBlurriness().set(0);
            window.setWidth(width); window.setHeight(height); window.setGuiScale(1); screen.init(mc, width, height);
            target.setClearColor(.06F, .09F, .12F, 1F); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            float farPlane = ClientHooks.getGuiFarPlane();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width, height, 0, 1000, farPlane), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.translation(0, 0, 10000F - farPlane); RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1); Lighting.setupFor3DItems();
            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            screen.render(graphics, -1, -1, 0); graphics.flush(); mc.renderBuffers().bufferSource().endBatch();
            Path path = output().getParent().resolve(name + "-" + width + "x" + height + ".png");
            Files.createDirectories(path.getParent()); int brightTitlePixels = 0;
            try (var image = Screenshot.takeScreenshot(target)) {
                image.writeToFile(path);
                for (int y = 10; y < Math.min(31, height); y++) for (int x = 16; x < width - 16; x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    if ((pixel & 255) > 220 && (pixel >>> 8 & 255) > 220 && (pixel >>> 16 & 255) > 220) brightTitlePixels++;
                }
            }
            check(brightTitlePixels >= 20, "Real native title pixels are present: " + name);
            JsonObject shot = new JsonObject(); shot.addProperty("screen", screen.getClass().getName());
            shot.addProperty("width", width); shot.addProperty("height", height); shot.addProperty("screenshot", path.toString());
            shot.addProperty("native_title_pixels", brightTitlePixels); report.getAsJsonArray("screenshots").add(shot);
        } finally {
            mc.renderBuffers().bufferSource().endBatch(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); RenderSystem.enableDepthTest(); Lighting.setupFor3DItems();
            window.setWidth(oldWidth); window.setHeight(oldHeight); window.setGuiScale(scale);
            mc.options.menuBackgroundBlurriness().set(originalBlur); targetField.set(mc, originalTarget);
            originalTarget.bindWrite(true); target.destroyBuffers();
        }
    }

    private static void finish(Minecraft mc, Throwable failure) {
        if (finished) return;
        finished = true;
        if (report == null) report = new JsonObject();
        try {
            if (originalReach != null) {
                MiteConfig.WEAPON_REACH_MULTIPLIER.set(originalReach);
                MiteConfig.WEAPON_REACH_REDUCTION.set(originalReduction);
                if (originalGravelRates != null) check(GravelDropSettings.save(originalGravelRates),
                        "Original in-memory gravel rates restored");
                restoreSettings.forEach(Runnable::run);
                MiteConfig.SPEC.save();
                MiteConfig.refreshEnchantmentRules();
                if (originalFile == null) Files.deleteIfExists(configFile); else Files.write(configFile, originalFile);
                check(MiteConfig.WEAPON_REACH_MULTIPLIER.get().equals(originalReach)
                        && MiteConfig.WEAPON_REACH_REDUCTION.get().equals(originalReduction), "Original in-memory scalar settings restored");
                check(originalFile == null ? !Files.exists(configFile) : Arrays.equals(originalFile, Files.readAllBytes(configFile)),
                        "The isolated COMMON configuration file is restored byte for byte");
                report.addProperty("original_configuration_restored_byte_exact", true);
            }
        } catch (Throwable restoreFailure) {
            if (failure == null) failure = restoreFailure; else failure.addSuppressed(restoreFailure);
        } finally {
            if (originalLanguage != null) { mc.getLanguageManager().setSelected(originalLanguage); mc.options.languageCode = originalLanguage; }
        }
        report.addProperty("status", failure == null ? "passed_real_settings_search_gui_render_save_restore" : "failed");
        if (failure != null) { report.addProperty("failure", failure.toString()); Mite.LOGGER.error("Settings search GUI audit failed", failure); }
        try { Files.createDirectories(output().getParent()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8); }
        catch (Exception writeFailure) { Mite.LOGGER.error("Cannot write settings search GUI audit", writeFailure); }
        mc.stop();
    }
    private static Path output() {
        return Path.of(System.getProperty("mite.settingsSearchAuditOutput", "build/verification/settings-search/client-audit.json")).toAbsolutePath().normalize();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
