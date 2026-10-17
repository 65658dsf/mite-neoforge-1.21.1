package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in checks of actually baked models, stitched sprites and original pixel-art resources. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class SimplySwordsClientAudit {
    private static final List<String> MATERIALS = List.of("rusted_iron", "copper", "silver",
            "ancient_metal", "mithril", "adamantium");
    private static volatile Set<ModelResourceLocation> optionalModels = Set.of();
    private static volatile boolean baked;
    private static boolean finished;
    private static JsonObject pendingReport;
    private static ContactSheetScreen contactSheet;
    private static boolean capturing;
    private SimplySwordsClientAudit() {}

    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event) {
            if (!Boolean.getBoolean("mite.simplySwordsClientAudit")) return;
            Set<ModelResourceLocation> loaded = new HashSet<>();
            for (var id : event.getModels().keySet()) if (optional(id.id())) loaded.add(id);
            optionalModels = Set.copyOf(loaded);
            baked = true;
        }
    }

    @SubscribeEvent public static void afterTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mite.simplySwordsClientAudit") || !baked || finished) return;
        Minecraft minecraft = Minecraft.getInstance();
        // The completed reload has published its model manager and item cache before the overlay disappears.
        if (minecraft.getOverlay() != null) return;
        finished = true;
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("simply_swords_loaded", SimplySwordsCompat.enabled());
        report.addProperty("actual_model_baking_completed", baked);
        report.addProperty("scope", "engine baked models, item cache, stitched sprites, decoded PNG structure and a native GUI item-render contact sheet");
        report.addProperty("world_created", minecraft.level != null);
        report.addProperty("title_screen_visible", minecraft.screen instanceof TitleScreen);
        report.addProperty("screen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        report.addProperty("game_ui_automated", SimplySwordsCompat.enabled());
        report.addProperty("audit_owned_screen_only", true);
        JsonArray failures = new JsonArray(); report.add("failures", failures);
        JsonArray models = new JsonArray(); report.add("models", models);
        try {
            check(minecraft.level == null, "The client audit must start outside a played world");
            if (SimplySwordsCompat.enabled()) auditPresent(minecraft, report, models, failures);
            else auditAbsent(report);
            report.addProperty("status", failures.isEmpty() ? "passed" : "failed");
        } catch (Exception failure) {
            failures.add(failure.toString());
            report.addProperty("status", "failed");
        }
        if (!SimplySwordsCompat.enabled() || minecraft.level != null) {
            report.addProperty("safe_client_shutdown_requested", true);
            write(report);
            minecraft.stop();
            return;
        }
        report.addProperty("screenshot_status", "pending_native_screen_render");
        report.addProperty("safe_client_shutdown_requested", false);
        write(report);
        pendingReport = report;
        contactSheet = new ContactSheetScreen();
        minecraft.setScreen(contactSheet);
    }

    @SubscribeEvent public static void afterFrame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mite.simplySwordsClientAudit") || pendingReport == null || contactSheet == null || capturing) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != contactSheet || minecraft.getOverlay() != null || contactSheet.renderedFrames < 2) return;
        capturing = true;
        try {
            Path screenshot = Path.of(System.getProperty("mite.simplySwordsClientAuditScreenshotOutput",
                    output().getParent().resolveSibling("reports").resolve("simplyswords-in-game.png").toString()))
                    .toAbsolutePath().normalize();
            Files.createDirectories(screenshot.getParent());
            // RenderFrame.Post follows GameRenderer.render, including the screen's final GuiGraphics flush.
            try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                image.writeToFile(screenshot);
                pendingReport.addProperty("screenshot_width", image.getWidth());
                pendingReport.addProperty("screenshot_height", image.getHeight());
            }
            pendingReport.addProperty("screenshot_status", "saved_native_render_target");
            pendingReport.addProperty("screenshot", screenshot.toString());
            pendingReport.addProperty("screenshot_rendered_frames", contactSheet.renderedFrames);
            pendingReport.addProperty("screenshot_item_rows", 7);
            pendingReport.addProperty("screenshot_item_columns", SimplySwordsCompat.families().size());
            pendingReport.addProperty("screenshot_render_method", "GuiGraphics.renderItem via native ItemRenderer, including three-dimensional greathammer");
        } catch (Exception failure) {
            pendingReport.getAsJsonArray("failures").add("Native rendered contact sheet capture: " + failure);
            pendingReport.addProperty("status", "failed");
            pendingReport.addProperty("screenshot_status", "failed");
            Mite.LOGGER.error("Cannot capture the Simply Swords in-game contact sheet", failure);
        } finally {
            pendingReport.addProperty("safe_client_shutdown_requested", true);
            write(pendingReport);
            // Native Minecraft shutdown releases the render target and other resources normally.
            minecraft.stop();
        }
    }

    private static void auditAbsent(JsonObject report) {
        long registered = BuiltInRegistries.ITEM.keySet().stream().filter(SimplySwordsClientAudit::added).count();
        check(registered == 0, "Absent Simply Swords must register no conditional weapons");
        check(optionalModels.isEmpty(), "Absent Simply Swords must request no optional top-level baked models: " + optionalModels);
        report.addProperty("registered_added_weapons", registered);
        report.addProperty("requested_optional_top_level_models", optionalModels.size());
        report.addProperty("no_optional_models_referenced", true);
        report.addProperty("checked_models", 0);
    }

    private static void auditPresent(Minecraft minecraft, JsonObject report, JsonArray models, JsonArray failures) throws Exception {
        long registered = BuiltInRegistries.ITEM.keySet().stream().filter(SimplySwordsClientAudit::added).count();
        check(registered == 90, "Exactly ninety additional weapons must be registered before model verification");
        Map<String, Set<String>> materialHashes = new LinkedHashMap<>();
        Map<String, ReferenceTexture> dimensions = new HashMap<>();
        for (String family : SimplySwordsCompat.families()) {
            ResourceLocation reference = id("simplyswords", "textures/item/iron_" + family + ".png");
            try (var stream = minecraft.getResourceManager().open(reference); NativeImage image = NativeImage.read(stream)) {
                byte[] alpha = new byte[image.getWidth() * image.getHeight()];
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                    alpha[y * image.getWidth() + x] = (byte)(image.getPixelRGBA(x, y) >>> 24);
                int partial = 0;
                for (byte value : alpha) if ((value & 255) > 0 && (value & 255) < 255) partial++;
                dimensions.put(family, new ReferenceTexture(image.getWidth(), image.getHeight(), alpha, partial));
            }
            materialHashes.put(family, new HashSet<>());
        }
        for (String material : MATERIALS) for (String family : SimplySwordsCompat.families()) {
            JsonObject facts = new JsonObject(); models.add(facts);
            ResourceLocation weaponId = id("mite", SimplySwordsCompat.path(material, family));
            facts.addProperty("item", weaponId.toString());
            facts.addProperty("material", material); facts.addProperty("family", family);
            try {
                auditModel(minecraft, weaponId, family, facts);
                String hash = auditTexture(minecraft.getResourceManager(), weaponId, family, dimensions.get(family), facts);
                check(materialHashes.get(family).add(hash), "Different metal variants must have different decoded pixels: " + weaponId);
                facts.addProperty("status", "passed");
            } catch (Exception failure) {
                facts.addProperty("status", "failed"); facts.addProperty("failure", failure.toString());
                failures.add(weaponId + ": " + failure);
            }
        }
        JsonObject distinct = new JsonObject();
        for (var entry : materialHashes.entrySet()) {
            distinct.addProperty(entry.getKey(), entry.getValue().size());
            if (entry.getValue().size() != 6) failures.add("Expected six distinct material textures for " + entry.getKey());
        }
        report.add("distinct_pixel_hashes_per_family", distinct);
        report.addProperty("registered_added_weapons", registered);
        report.addProperty("checked_models", models.size());
        report.addProperty("checked_original_reference_dimensions", dimensions.size());
        report.addProperty("all_materials_distinct", materialHashes.values().stream().allMatch(hashes -> hashes.size() == 6));
    }

    private static void auditModel(Minecraft minecraft, ResourceLocation weaponId, String family, JsonObject facts) {
        Item item = BuiltInRegistries.ITEM.getOptional(weaponId).orElse(Items.AIR);
        check(item != Items.AIR, "The model belongs to a registered weapon");
        ModelResourceLocation modelId = ModelResourceLocation.inventory(weaponId);
        check(optionalModels.contains(modelId), "The real baking event contains the weapon inventory model");
        var manager = minecraft.getModelManager();
        BakedModel model = manager.getModel(modelId);
        check(model != null && model != manager.getMissingModel(), "The real baked model is not missing");
        BakedModel cached = minecraft.getItemRenderer().getItemModelShaper().getItemModel(item);
        check(cached == model, "The actual item-renderer cache resolves the verified weapon model");
        TextureAtlasSprite particle = model.getParticleIcon();
        check(!missing(particle), "The real baked model particle sprite is not missing");
        ResourceLocation expectedSprite = id("mite", "item/" + weaponId.getPath());
        check(particle.contents().name().equals(expectedSprite), "Particle sprite resolves to this material's local artwork");
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, RandomSource.create(0L)));
        for (Direction face : Direction.values()) quads.addAll(model.getQuads(null, face, RandomSource.create(0L)));
        check(!quads.isEmpty(), "The baked item has drawable geometry");
        Set<ResourceLocation> sprites = new HashSet<>();
        for (BakedQuad quad : quads) {
            check(!missing(quad.getSprite()), "Every drawable face has a stitched sprite");
            sprites.add(quad.getSprite().contents().name());
        }
        check(sprites.contains(expectedSprite), "Actual model faces use the custom local metal texture");
        if (family.equals("greathammer")) check(model.isGui3d() && quads.size() > 6,
                "The great hammer retains the full three-dimensional Simply Swords geometry");
        facts.addProperty("inventory_model", modelId.toString());
        facts.addProperty("model_class", model.getClass().getName());
        facts.addProperty("item_renderer_cache_matches", true);
        facts.addProperty("particle_sprite", particle.contents().name().toString());
        facts.addProperty("gui_3d", model.isGui3d()); facts.addProperty("quad_count", quads.size());
        JsonArray used = new JsonArray(); sprites.stream().map(ResourceLocation::toString).sorted().forEach(used::add);
        facts.add("actual_quad_sprites", used);
    }

    private static String auditTexture(ResourceManager resources, ResourceLocation weaponId, String family,
            ReferenceTexture expected, JsonObject facts) throws Exception {
        ResourceLocation png = id("mite", "textures/item/" + weaponId.getPath() + ".png");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Set<Integer> colors = new HashSet<>();
        int transparent = 0, opaque = 0, partial = 0, alphaMismatches = 0;
        try (var stream = resources.open(png); NativeImage image = NativeImage.read(stream)) {
            check(image.getWidth() == expected.width() && image.getHeight() == expected.height(),
                    "Pixel-art canvas must match the engine UV reference for " + family);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixelRGBA(x, y);
                int alpha = pixel >>> 24;
                if (alpha != (expected.alpha()[y * image.getWidth() + x] & 255)) alphaMismatches++;
                if (alpha == 0) transparent++;
                else if (alpha == 255) { opaque++; colors.add(pixel & 0xFFFFFF); }
                else partial++;
                digest.update((byte)pixel); digest.update((byte)(pixel >>> 8));
                digest.update((byte)(pixel >>> 16)); digest.update((byte)alpha);
            }
            check(opaque > 0 && transparent > 0, "Artwork contains a visible weapon and transparent background");
            check(alphaMismatches == 0, "The material variant preserves the exact reference silhouette and transparency");
            check(colors.size() >= 3 && colors.size() <= 256, "Artwork uses a bounded, shaded pixel-art palette");
            facts.addProperty("texture_png", png.toString());
            facts.addProperty("width", image.getWidth()); facts.addProperty("height", image.getHeight());
        }
        String hash = HexFormat.of().formatHex(digest.digest());
        facts.addProperty("opaque_pixels", opaque); facts.addProperty("transparent_pixels", transparent);
        facts.addProperty("reference_alpha_mask_matches", true);
        facts.addProperty("reference_partial_alpha_pixels", expected.partialAlpha());
        facts.addProperty("partial_alpha_pixels", partial); facts.addProperty("opaque_color_count", colors.size());
        facts.addProperty("decoded_rgba_sha256", hash);
        return hash;
    }

    private static boolean missing(TextureAtlasSprite sprite) {
        return sprite == null || sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }
    private static boolean added(ResourceLocation id) {
        return id.getNamespace().equals("mite") && id.getPath().startsWith("simplyswords_");
    }
    private static boolean optional(ResourceLocation id) {
        return added(id) || id.getNamespace().equals("simplyswords");
    }
    private static ResourceLocation id(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static Path output() {
        return Path.of(System.getProperty("mite.simplySwordsClientAuditOutput",
                "build/verification/simplyswords-client-audit.json")).toAbsolutePath().normalize();
    }
    private static void write(JsonObject report) {
        try {
            Path output = output();
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
            Mite.LOGGER.info("MITE_SIMPLYSWORDS_CLIENT_AUDIT {} report={}", report.get("status").getAsString(), output);
        } catch (IOException failure) { Mite.LOGGER.error("Cannot write the Simply Swords client model audit", failure); }
    }

    private static final class ContactSheetScreen extends Screen {
        private static final List<String> LABELS = List.of("原铁", "锈铁", "铜", "银", "远古", "秘银", "艾德曼");
        private static final int CELL = 24;
        private static final int LABEL_WIDTH = 32;
        private final List<List<ItemStack>> rows = new ArrayList<>();
        private int renderedFrames;

        private ContactSheetScreen() {
            super(Component.literal("Simply Swords material render audit"));
            rows.add(SimplySwordsCompat.families().stream()
                    .map(family -> new ItemStack(SimplySwordsCompat.weapon("iron", family))).toList());
            for (String material : MATERIALS) rows.add(SimplySwordsCompat.families().stream()
                    .map(family -> new ItemStack(SimplySwordsCompat.weapon(material, family))).toList());
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF141921);
            int panelWidth = LABEL_WIDTH + SimplySwordsCompat.families().size() * CELL;
            int panelHeight = rows.size() * CELL;
            int left = Math.max(0, (width - panelWidth) / 2);
            int top = Math.max(0, (height - panelHeight) / 2);
            for (int row = 0; row < rows.size(); row++) {
                int y = top + row * CELL;
                graphics.drawString(font, LABELS.get(row), left + 1, y + 8, row == 0 ? 0xFFE8D59A : 0xFFE0E6EE, false);
                for (int column = 0; column < rows.get(row).size(); column++) {
                    int x = left + LABEL_WIDTH + column * CELL;
                    graphics.fill(x, y, x + CELL - 1, y + CELL - 1, row % 2 == 0 ? 0xFF26313F : 0xFF202A36);
                    graphics.renderItem(rows.get(row).get(column), x + 4, y + 4);
                }
            }
            graphics.flush();
            renderedFrames++;
        }

        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public boolean isPauseScreen() { return false; }
    }
    private record ReferenceTexture(int width, int height, byte[] alpha, int partialAlpha) {}
}
