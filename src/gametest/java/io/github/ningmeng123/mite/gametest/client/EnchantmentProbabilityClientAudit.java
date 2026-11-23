package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.config.EnchantmentSettingsScreen;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

/** Opt-in real title-menu probability editing, GPU rendering and isolated configuration round trip. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class EnchantmentProbabilityClientAudit {
    private static boolean started, finished, changingLanguage;
    private static int ticks, saveTicks;
    private static String originalLanguage, firstId, secondId;
    private static MiteConfig.Snapshot original;
    private static Path configFile;
    private static byte[] originalFile;
    private static JsonObject report;

    private EnchantmentProbabilityClientAudit() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.enchantmentProbabilityAudit") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            check(mc.level == null && mc.getConnection() == null && mc.getSingleplayerServer() == null,
                    "Probability GUI audit only runs disconnected at the title screen");
            check(++ticks < 2400, "Timed out waiting for the real probability GUI audit");
            if (mc.getOverlay() != null) return;
            if (started) {
                if (++saveTicks < 12) return;
                checkSaved();
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
            original = MiteConfig.localSnapshot();
            configFile = FMLPaths.CONFIGDIR.get().resolve("mite-common.toml").toAbsolutePath().normalize();
            Path game = mc.gameDirectory.toPath().toAbsolutePath().normalize();
            check(configFile.startsWith(game) && game.toString().contains("verification"),
                    "Only the disposable verification game directory may be edited");
            originalFile = Files.exists(configFile) ? Files.readAllBytes(configFile) : null;
            audit(mc);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void audit(Minecraft mc) throws Exception {
        var container = ModList.get().getModContainerById(Mite.MOD_ID).orElseThrow();
        Screen home = IConfigScreenFactory.getForMod(container.getModInfo()).orElseThrow().createScreen(container, mc.screen);
        mc.setScreen(home); home.init(mc, 320, 240);
        press(home, "mite.config.category.combat");
        Screen combat = mc.screen;
        press(combat, "mite.config.registry_enchantments");
        check(mc.screen instanceof EnchantmentSettingsScreen, "The production config factory and button open the production enchantment page");
        Screen screen = mc.screen; screen.init(mc, 320, 240);
        check(Component.translatable("mite.config.enchantment_chance").getString().contains("概率"),
                "The visible probability label resolves through native Simplified Chinese language resources");
        check(chances(screen).size() == 1, "The minimum window has one real probability input row");
        bounds(screen); capture(mc, screen, 320, 240, "probability-default-zh-cn");

        chance(screen).setValue("37.5"); firstId = draftIds(screen).getFirst();
        press(screen, ">"); chance(screen).setValue("101");
        secondId = draftIds(screen).stream().filter(id -> !id.equals(firstId)).findFirst().orElseThrow();
        press(screen, "<");
        check(chance(screen).getValue().equals("37.5"), "Paging preserves the first decimal probability draft");
        check(!button(screen, "mite.config.save").active, "A hidden invalid probability disables the actual Save button");
        press(screen, ">"); check(chance(screen).getValue().equals("101"), "The invalid draft survives paging back");
        chance(screen).setValue("12.5"); press(screen, "<");
        check(button(screen, "mite.config.save").active, "Correcting the hidden invalid draft enables saving");

        search(screen).setValue("锋利");
        check(chances(screen).size() == 1, "Chinese search finds the real Sharpness catalog entry");
        chance(screen).setValue("0"); search(screen).setValue("minecraft:sharpness");
        check(chance(screen).getValue().equals("0"), "Switching from Chinese name to resource ID retains the chance draft");
        bounds(screen); capture(mc, screen, 320, 240, "probability-sharpness-zero-zh-cn");
        search(screen).setValue("");
        check(chance(screen).getValue().equals("37.5"), "Leaving search restores the edited first page");
        screen.init(mc, 854, 480);
        check(chances(screen).size() > 1 && chances(screen).getFirst().getValue().equals("37.5"),
                "A larger window shows multiple real chance inputs while keeping drafts");
        bounds(screen); capture(mc, screen, 854, 480, "probability-edited-zh-cn");
        press(screen, "mite.config.save");
        check(mc.screen == combat, "The actual Save callback returns to the Combat category");
        checkSaved();
        combat.init(mc, 854, 480); press(combat, "mite.config.registry_enchantments");
        Screen reopened = mc.screen; reopened.init(mc, 854, 480);
        search(reopened).setValue(firstId);
        check(chance(reopened).getValue().equals("37.5"), "A newly constructed production page reads the saved decimal probability");
        bounds(reopened); capture(mc, reopened, 854, 480, "probability-saved-reopened-zh-cn");
        report.addProperty("native_factory_and_settings_navigation", true);
        report.addProperty("minimum_and_large_widget_bounds", true);
        report.addProperty("probability_inputs_visible", true);
        report.addProperty("chinese_name_and_resource_id_search", true);
        report.addProperty("paging_and_resize_preserve_valid_and_invalid_drafts", true);
        report.addProperty("hidden_invalid_probability_blocks_save", true);
        report.addProperty("decimal_probability_and_zero_saved_to_toml", true);
        report.addProperty("fresh_screen_displays_saved_value", true);
    }

    private static void checkSaved() throws Exception {
        var chances = MiteConfig.localSnapshot().enchantmentChances();
        check(chances.getOrDefault(firstId, 100D) == 37.5D && chances.getOrDefault(secondId, 100D) == 12.5D
                && chances.getOrDefault("minecraft:sharpness", 100D) == 0D, "Real saving publishes all three probability drafts");
        String toml = Files.readString(configFile);
        check(toml.contains(firstId + "=37.5") && toml.contains(secondId + "=12.5")
                && toml.contains("minecraft:sharpness=0.0"), "Actual COMMON TOML contains the saved probabilities");
    }

    private static List<AbstractWidget> widgets(Screen screen) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
    }
    private static List<EditBox> chances(Screen screen) {
        String label = Component.translatable("mite.config.enchantment_chance").getString();
        return widgets(screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getMessage().getString().equals(label)).toList();
    }
    private static EditBox chance(Screen screen) { return chances(screen).getFirst(); }
    private static EditBox search(Screen screen) {
        return widgets(screen).stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getMessage().getString().equals(Component.translatable("mite.config.search").getString())).findFirst().orElseThrow();
    }
    @SuppressWarnings("unchecked") private static List<String> draftIds(Screen screen) throws Exception {
        var field = EnchantmentSettingsScreen.class.getDeclaredField("chanceDrafts"); field.setAccessible(true);
        return ((Map<String, String>)field.get(screen)).keySet().stream().toList();
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
            if (original != null) {
                MiteConfig.saveEnchantments(original.caps(), original.disabled(), original.enchantmentChances());
                if (originalFile == null) Files.deleteIfExists(configFile); else Files.write(configFile, originalFile);
                check(MiteConfig.localSnapshot().equals(original), "The entire original in-memory COMMON snapshot is restored");
                check(originalFile == null ? !Files.exists(configFile) : Arrays.equals(originalFile, Files.readAllBytes(configFile)),
                        "The isolated COMMON configuration file is restored byte for byte");
                report.addProperty("original_configuration_restored_byte_exact", true);
            }
        } catch (Throwable restoreFailure) {
            if (failure == null) failure = restoreFailure; else failure.addSuppressed(restoreFailure);
        } finally {
            if (originalLanguage != null) { mc.getLanguageManager().setSelected(originalLanguage); mc.options.languageCode = originalLanguage; }
        }
        report.addProperty("status", failure == null ? "passed_real_probability_gui_render_save_restore" : "failed");
        if (failure != null) { report.addProperty("failure", failure.toString()); Mite.LOGGER.error("Probability GUI audit failed", failure); }
        try { Files.createDirectories(output().getParent()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8); }
        catch (Exception writeFailure) { Mite.LOGGER.error("Cannot write probability GUI audit", writeFailure); }
        mc.stop();
    }
    private static Path output() {
        return Path.of(System.getProperty("mite.enchantmentProbabilityAuditOutput", "build/verification/probability-gui/client-audit.json")).toAbsolutePath().normalize();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
