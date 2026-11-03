package io.github.ningmeng123.mite.gametest.client;

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
import io.github.ningmeng123.mite.client.config.MiteSettingsScreen;
import io.github.ningmeng123.mite.client.config.SingleConfigValueScreen;
import io.github.ningmeng123.mite.config.MiteConfig;
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
import org.joml.Matrix4f;

/** Opt-in production settings search, native GPU rendering and disposable configuration round trip. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class SettingsSearchClientAudit {
    private static boolean started, finished, changingLanguage;
    private static int ticks, saveTicks;
    private static String originalLanguage;
    private static Double originalReach, originalReduction;
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
        check(search(home).getBottom() <= button(home, "mite.config.ore_generation").getY(),
                "The actual search field is above the category menu at minimum size");
        search(home).setValue("攻击距离");
        check(matches(home).stream().anyMatch(entry -> entry.id().equals("mite:combat.weapon_reach_multiplier")),
                "Chinese configuration label search finds the actual reach setting");
        bounds(home); noOverlap(home); capture(mc, home, 320, 240, "settings-search-chinese-zh-cn");
        search(home).setValue("qxyzz_no_configuration_729");
        check(matches(home).isEmpty(), "An unmatched search produces zero real results");
        bounds(home); noOverlap(home); capture(mc, home, 320, 240, "settings-search-empty-zh-cn");
        search(home).setValue("   ");
        check(button(home, "mite.config.ore_generation").visible, "Whitespace clears searching and restores categories");
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
        check(button(home, "mite.config.ore_generation").visible, "Clearing the search restores category navigation");
        bounds(home); noOverlap(home); capture(mc, home, 854, 480, "settings-home-large-zh-cn");

        search(home).setValue("minecraft:sharpness");
        openOnlyResult(home, "enchantments:minecraft:sharpness");
        check(mc.screen instanceof EnchantmentSettingsScreen, "An enchantment result opens its dedicated production editor");
        check(editBoxes(mc.screen).stream().anyMatch(box -> box.getValue().equals("minecraft:sharpness")),
                "The dedicated enchantment editor is already filtered to the selected result");
        mc.screen.onClose();
        check(mc.screen == home, "Closing a result returns to the same search homepage");

        nativeResetAndSave(mc, home);
        boolean ite = ModList.get().isLoaded("modern_ite");
        report.addProperty("modern_ite_loaded", ite);
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
