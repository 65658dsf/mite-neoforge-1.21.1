package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.animal.Livestock032View;
import io.github.ningmeng123.mite.entity.animal.LivestockHealth033;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Focused real client render regression; all entities stay outside a disposable world. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class SickLivestockClientAudit {
    private static final JsonObject REPORT = new JsonObject();
    private static boolean baked, started, done;
    private static int waiting, livingDraws;
    private static AuditScreen screen;
    private static Mob drawing;
    private static boolean sawDraw;

    private SickLivestockClientAudit() {}

    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted ignored) {
            if (Boolean.getBoolean("mite.sickLivestockAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.sickLivestockAudit") || !baked || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                check(directory.toString().replace('\\', '/').contains("/build/verification/"),
                        "Sick livestock audit requires an isolated directory under build/verification");
                started = true;
                REPORT.addProperty("author", "NingMeng123");
                REPORT.addProperty("game_directory", directory.toString());
                REPORT.addProperty("existing_worlds_opened", false);
                REPORT.addProperty("client_only_entities_added_to_world", false);
                REPORT.addProperty("visual_verdict", "requires_screenshot_review");
                REPORT.addProperty("entity_model_features_loaded", ModList.get().isLoaded("entity_model_features"));
                REPORT.addProperty("acceleratedrendering_loaded", ModList.get().isLoaded("acceleratedrendering"));
                REPORT.add("failures", new JsonArray());
                REPORT.add("entities", new JsonArray());
                REPORT.add("screenshots", new JsonArray());
                JsonArray packs = new JsonArray();
                mc.getResourceManager().listPacks().forEach(pack -> packs.add(pack.packId()));
                REPORT.add("active_packs", packs);
                mc.options.pauseOnLostFocus = false;
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                String world = "sick-livestock-audit-" + System.currentTimeMillis();
                REPORT.addProperty("disposable_world", world);
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261101L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++waiting < 3600, "Timed out creating or rendering the disposable livestock audit");
            if (screen == null && mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                screen = new AuditScreen(mc);
                mc.setScreen(screen);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent public static void rendered(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity() != drawing) return;
        sawDraw = true;
        livingDraws++;
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post ignored) {
        Minecraft mc = Minecraft.getInstance();
        if (done || screen == null || mc.screen != screen || screen.frames < 4) return;
        try {
            Files.createDirectories(output());
            Path screenshot = output().resolve(screen.page == 0 ? "front.png" : "rear.png");
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(image.getWidth() > 100 && image.getHeight() > 100, "Captured an actual GPU framebuffer");
                image.writeToFile(screenshot);
                REPORT.addProperty("framebuffer_width", image.getWidth());
                REPORT.addProperty("framebuffer_height", image.getHeight());
            }
            REPORT.getAsJsonArray("screenshots").add(screenshot.toString());
            if (++screen.page < 2) { screen.frames = 0; return; }
            REPORT.addProperty("actual_living_renderer_events", livingDraws);
            REPORT.addProperty("status", "passed_real_livestock_texture_and_vertex_color_regressions");
            finish(mc, null);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static final class AuditScreen extends Screen {
        private final List<Row> rows = new ArrayList<>();
        private int frames, page;

        AuditScreen(Minecraft mc) throws Exception {
            super(Component.literal("Sick livestock resource-pack compatibility audit"));
            add(mc, EntityType.COW, "cow", false, false);
            add(mc, EntityType.PIG, "pig", false, false);
            add(mc, EntityType.CHICKEN, "chicken", false, false);
            add(mc, EntityType.SHEEP, "red wool sheep", false, false);
            add(mc, EntityType.SHEEP, "white wool sheep", false, false);
            add(mc, EntityType.PIG, "saddled pig", true, false);
            add(mc, EntityType.SHEEP, "sheared sheep", false, true);
        }

        private void add(Minecraft mc, EntityType<?> type, String label, boolean saddled, boolean sheared) throws Exception {
            Mob entity = (Mob) type.create(mc.level);
            check(entity != null, "Livestock constructor succeeds: " + label);
            entity.setUUID(UUID.nameUUIDFromBytes(("mite-sick-livestock-" + rows.size()).getBytes(StandardCharsets.UTF_8)));
            entity.setId(45000 + rows.size());
            entity.setPos(mc.player.position());
            entity.setYRot(0); entity.yRotO = 0; entity.yBodyRot = entity.yBodyRotO = 0;
            entity.yHeadRot = entity.yHeadRotO = 0;
            entity.tickCount = 60;
            entity.setOnGround(true);
            entity.setNoAi(true);
            if (entity instanceof Pig pig && saddled) pig.equipSaddle(new ItemStack(Items.SADDLE), null);
            if (entity instanceof Sheep sheep) {
                sheep.setSheared(sheared);
                sheep.setColor(label.equals("white wool sheep") ? DyeColor.WHITE : DyeColor.RED);
            }
            health(entity, 0);
            ResourceLocation normal = texture(entity);
            boolean custom = customized(renderer(entity).getModel());
            String expectation = System.getProperty("mite.sickLivestockAuditExpectCustom", "auto");
            if (!expectation.equals("auto")) check(custom == Boolean.parseBoolean(expectation),
                    "Actual customized model matches the requested fixture: " + label);
            JsonObject fact = new JsonObject();
            fact.addProperty("label", label);
            fact.addProperty("entity", EntityType.getKey(type).toString());
            fact.addProperty("uuid", entity.getUUID().toString());
            fact.addProperty("same_entity_used_for_all_health_phases", true);
            fact.addProperty("emf_customized", custom);
            fact.addProperty("renderer_class", renderer(entity).getClass().getName());
            fact.addProperty("model_class", renderer(entity).getModel().getClass().getName());
            fact.addProperty("saddled", saddled);
            fact.addProperty("sheared", sheared);
            fact.add("normal_body", textureFact(normal));
            fact.add("phases", new JsonArray());
            if (Boolean.getBoolean("mite.sickLivestockRequireExtendedAtlas") && (entity instanceof Pig || type == EntityType.COW)) {
                JsonObject atlas = fact.getAsJsonObject("normal_body");
                check(atlas.get("width").getAsInt() == 64 && atlas.get("height").getAsInt() == 64,
                        "FA+Details cow/pig fixture actually uses its extended 64x64 atlas: " + label);
            }
            REPORT.getAsJsonArray("entities").add(fact);
            rows.add(new Row(entity, label, normal, custom, saddled, sheared,
                    renderer(entity), renderer(entity).getModel(), fact));
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try {
                graphics.fill(0, 0, width, height, 0xFF17202B);
                graphics.drawCenteredString(font, "Livestock: healthy / sick / recovered (same entity and model)", width / 2, 7, 0xFFF0F3FA);
                int labelWidth = Math.min(120, width / 5), cellWidth = (width - labelWidth - 8) / 3;
                int rowHeight = (height - 30) / rows.size();
                for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                    Row row = rows.get(rowIndex);
                    int y = 27 + rowIndex * rowHeight;
                    graphics.drawString(font, row.label, 5, y + rowHeight / 2, 0xFFE5E9F4, false);
                    for (int phase = 0; phase < 3; phase++) {
                        health(row.entity, phase);
                        int x = labelWidth + phase * cellWidth;
                        graphics.fill(x, y, x + cellWidth - 4, y + rowHeight - 3, 0xFF2D3948);
                        drawing = row.entity;
                        sawDraw = false;
                        try {
                            InventoryScreen.renderEntityInInventory(graphics, x + cellWidth / 2F, y + rowHeight - 5F,
                                    Math.min(cellWidth * .32F, rowHeight * .52F), new Vector3f(),
                                    new Quaternionf().rotationZ((float) Math.PI).rotateX(.25F)
                                            .rotateY((page == 0 ? (float) Math.PI : 0F) + .6F), null, row.entity);
                            check(sawDraw, "Actual InventoryScreen renderer completed: " + row.label + "/" + phase);
                            if (page == 0 && frames == 0) verifyRenderedPhase(row, phase);
                        } finally { drawing = null; }
                    }
                    if (page == 0 && frames == 0) verifyRecoveryDelay(row);
                }
                graphics.flush();
                frames++;
            } catch (Throwable failure) { finish(Minecraft.getInstance(), failure); }
        }

        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public boolean isPauseScreen() { return true; }
    }

    private static void health(Mob entity, int phase) {
        Livestock032View view = (Livestock032View) entity;
        view.mite$setLivestockNeeds(new LivestockRules032.Needs(phase == 1 ? .1F : 1F, 1F, 1F));
        view.mite$setRecoveryTicks(0);
        check((LivestockHealth033.sickBodyTexture(entity) != null) == (phase == 1), "Fixture has the intended synchronized health");
    }

    private static void verifyRenderedPhase(Row row, int phase) throws Exception {
        boolean sick = phase == 1;
        ResourceLocation body = texture(row.entity);
        EntityModel<?> model = renderer(row.entity).getModel();
        check(renderer(row.entity) == row.normalRenderer && model == row.normalModel,
                "Health transitions reuse the same real renderer and model objects: " + row.label);
        check(customized(model) == row.custom, "Model customization survives the health transition: " + row.label);
        if (row.custom || !sick) check(body.equals(row.normal), "Health transition preserves/restores the active pack atlas: " + row.label);
        else check(!body.equals(row.normal), "Native sick animals still select their source sickness skin: " + row.label);
        JsonObject sample = new JsonObject();
        sample.addProperty("phase", List.of("healthy", "sick", "recovered").get(phase));
        sample.add("body", textureFact(body));
        row.fact.getAsJsonArray("phases").add(sample);
        Capture capture = capture(row.entity);
        sample.add("observed_real_renderer_vertex_buffers", capture.json());
        Colors primary = primary(capture);
        check(primary.colors.stream().allMatch(color -> color == -1),
                "Body vertices retain neutral white; sickness is represented by the actual gray atlas: " + row.label);
        check(primary.texture != null, "The actual body atlas is identified: " + row.label);
        sample.add("rendered_body", textureFact(primary.texture));
        if (phase == 0) row.fact.addProperty("normal_rendered_body", primary.texture.toString());
        else if (sick) {
            ResourceLocation source = graySource(row);
            sample.addProperty("gray_source_atlas", source.toString());
            sample.addProperty("gray_source_kind", row.custom ? "active_etf_selected_body" : "legacy_sick_body");
            sample.add("gray_body_pixels", verifyGray(primary.texture, source,
                    !row.custom && row.entity.getType() == EntityType.CHICKEN));
            row.fact.addProperty("sick_rendered_body", primary.texture.toString());
            row.fact.addProperty("sick_gray_pixel_hash", sample.getAsJsonObject("gray_body_pixels").get("pixel_hash").getAsString());
        } else check(primary.texture.toString().equals(row.fact.get("normal_rendered_body").getAsString()),
                "Recovery restores the actual ETF-selected healthy atlas: " + row.label);
        if (row.saddled) {
            List<Colors> saddle = capture.buffers.stream().filter(buffer -> buffer.type.contains("saddle") && buffer.vertices > 0).toList();
            check(!saddle.isEmpty(), "Saddled pig emits an actual saddle render layer");
            check(saddle.stream().anyMatch(buffer -> buffer.colors.contains(-1)), "Sickness keeps the saddle's original white vertex color");
        }
        if (row.entity instanceof Sheep sheep) {
            List<Colors> wool = capture.buffers.stream().filter(buffer ->
                    (buffer.type.contains("sheep_fur") || buffer.type.contains("sheep_wool")) && buffer.vertices > 0).toList();
            check(row.sheared ? wool.isEmpty() : !wool.isEmpty(), "Actual wool layer matches the sheared state");
            if (!row.sheared) {
                JsonArray atlases = new JsonArray();
                for (Colors buffer : wool) {
                    check(buffer.texture != null, "The actual wool texture is identified");
                    atlases.add(buffer.texture.toString());
                    boolean customPart = buffer.texture.getPath().contains("sheep_wool");
                    int original = customPart ? -1 : Sheep.getColor(sheep.getColor());
                    check(buffer.colors.stream().allMatch(color -> color == original),
                            "Sickness leaves independent wool vertex colors unchanged: " + row.label + "/" + buffer.texture);
                }
                sample.add("wool_atlases", atlases);
                if (phase == 0) row.fact.add("normal_wool_atlases", atlases);
                else check(atlases.equals(row.fact.getAsJsonArray("normal_wool_atlases")),
                        "Sickness preserves the white/red wool atlas selected by the resource pack");
                if (wool.stream().anyMatch(buffer -> buffer.texture.getPath().contains("sheep_wool"))) {
                    Colors colored = wool.stream().filter(buffer -> buffer.texture.getPath().contains("sheep_wool")).findFirst().orElseThrow();
                    JsonObject woolImage = woolAtlasFact(colored.texture);
                    sample.add("wool_atlas_pixels", woolImage);
                    boolean redPixels = woolImage.get("red_dominant_pixels").getAsInt() > 0;
                    check(redPixels == (sheep.getColor() == DyeColor.RED), "The precolored CEM wool atlas matches the sheep dye color");
                }
            }
        }
        sample.addProperty("inventory_screen_real_gpu_draw_completed", true);
        sample.addProperty("same_renderer_and_model_identity", true);
        sample.addProperty("expected_body_color_argb", "0xFFFFFFFF");
        if (sick && !row.custom && row.entity.getType() == EntityType.CHICKEN) {
            var uploaded = Minecraft.getInstance().getTextureManager().getTexture(primary.texture);
            check(uploaded instanceof DynamicTexture, "Native sick chicken uses an actual uploaded repaired atlas");
            NativeImage image = ((DynamicTexture) uploaded).getPixels();
            check(image != null, "Repaired chicken pixels remain available for UV inspection");
            for (int y = 0; y < 3; y++) for (int x = 7; x < 11; x++)
                check(image.getPixelRGBA(x, y) >>> 24 == 255, "Chicken head bottom UV has no missing pixels");
            sample.addProperty("chicken_head_bottom_uv_opaque", true);
        }
    }

    private static void verifyRecoveryDelay(Row row) throws Exception {
        Livestock032View view = (Livestock032View) row.entity;
        view.mite$setLivestockNeeds(new LivestockRules032.Needs(1F, 1F, 1F));
        view.mite$setRecoveryTicks(20);
        check(LivestockHealth033.sickBodyTexture(row.entity) != null, "Recovery delay remains visually unwell");
        ResourceLocation delayed = texture(row.entity);
        if (row.custom) check(delayed.equals(row.normal), "Recovery delay retains the custom pack atlas");
        Capture capture = capture(row.entity);
        row.fact.add("recovery_delay_observed_buffers", capture.json());
        Colors primary = primary(capture);
        check(primary.texture.toString().equals(row.fact.get("sick_rendered_body").getAsString()),
                "Recovery delay reuses the actual gray sickness atlas");
        check(primary.colors.stream().allMatch(color -> color == -1), "Recovery delay retains neutral body vertex colors");
        JsonObject grayPixels = verifyGray(primary.texture, graySource(row),
                !row.custom && row.entity.getType() == EntityType.CHICKEN);
        check(grayPixels.get("pixel_hash").getAsString().equals(row.fact.get("sick_gray_pixel_hash").getAsString()),
                "Gray sickness pixels stay unchanged throughout the recovery delay");
        row.fact.add("recovery_delay_gray_pixels", grayPixels);
        view.mite$setRecoveryTicks(0);
        check(texture(row.entity).equals(row.normal), "Recovery delay expiry restores the normal atlas immediately");
        Capture recovered = capture(row.entity);
        row.fact.add("recovery_expiry_observed_buffers", recovered.json());
        Colors normal = primary(recovered);
        check(normal.colors.stream().allMatch(color -> color == -1), "Recovery delay expiry retains actual white body vertices");
        check(normal.texture.toString().equals(row.fact.get("normal_rendered_body").getAsString()),
                "Recovery delay expiry restores the same original ETF-selected atlas");
        row.fact.addProperty("recovery_delay_rendered_and_expiry_restored", true);
    }

    private static Colors primary(Capture capture) {
        // The texture adapter may request the normal consumer before rebinding the body to its generated atlas.
        return capture.buffers.stream().filter(buffer -> buffer.vertices > 0).findFirst().orElseThrow(
                () -> new IllegalStateException("Real renderer emitted no body vertices"));
    }

    private static ResourceLocation graySource(Row row) {
        ResourceLocation source = row.custom ? ResourceLocation.parse(row.fact.get("normal_rendered_body").getAsString())
                : LivestockHealth033.sickBodyTexture(row.entity);
        check(source != null, "The gray body retains an identifiable source atlas");
        return source;
    }

    /** Inspect pixels actually uploaded and selected by the transformed renderer, including ETF variants. */
    private static JsonObject verifyGray(ResourceLocation grayId, ResourceLocation sourceId, boolean repairedChicken) throws Exception {
        JsonObject fact = new JsonObject();
        fact.addProperty("generated_atlas", grayId.toString());
        fact.addProperty("source_atlas", sourceId.toString());
        check(!grayId.equals(sourceId), "Sick body binds a generated atlas rather than the colored source");
        var uploaded = Minecraft.getInstance().getTextureManager().getTexture(grayId);
        check(uploaded instanceof DynamicTexture, "Gray sickness atlas is a real uploaded DynamicTexture: " + grayId);
        NativeImage gray = ((DynamicTexture) uploaded).getPixels();
        check(gray != null, "Generated gray atlas retains inspectable pixels");
        int visible = 0, minimum = 255, maximum = 0, repaired = 0;
        long hash = 1469598103934665603L;
        try (NativeImage source = imageCopy(sourceId)) {
            check(gray.getWidth() == source.getWidth() && gray.getHeight() == source.getHeight(),
                    "Generated gray atlas preserves the actual source UV dimensions");
            for (int y = 0; y < gray.getHeight(); y++) for (int x = 0; x < gray.getWidth(); x++) {
                int original = source.getPixelRGBA(x, y);
                if (repairedChicken && x >= 7 && x < 11 && y < 3 && original >>> 24 == 0) {
                    original = source.getPixelRGBA(x - 4, y);
                    repaired++;
                }
                int pixel = gray.getPixelRGBA(x, y);
                hash = (hash ^ Integer.toUnsignedLong(pixel)) * 1099511628211L;
                check(pixel >>> 24 == original >>> 24, "Gray atlas preserves source alpha at " + x + "," + y);
                if (pixel >>> 24 == 0) continue;
                int red = pixel & 255, green = pixel >>> 8 & 255, blue = pixel >>> 16 & 255;
                check(red == green && green == blue, "Every visible sick body texel is neutral gray-white at " + x + "," + y);
                int sourceRed = original & 255, sourceGreen = original >>> 8 & 255, sourceBlue = original >>> 16 & 255;
                int luminance = (77 * sourceRed + 150 * sourceGreen + 29 * sourceBlue + 128) >> 8;
                check(red == 88 + (152 * luminance + 127) / 255,
                        "Actual gray atlas preserves source markings and the specified pale luminance at " + x + "," + y);
                visible++;
                minimum = Math.min(minimum, red);
                maximum = Math.max(maximum, red);
            }
            fact.addProperty("width", source.getWidth());
            fact.addProperty("height", source.getHeight());
        }
        check(visible > 0 && minimum < maximum, "Gray conversion keeps visible texture detail instead of a flat fill");
        if (repairedChicken) check(repaired == 12, "Native chicken independently verifies the twelve repaired head underside texels");
        fact.addProperty("all_visible_rgb_channels_equal", true);
        fact.addProperty("source_alpha_and_uv_preserved", true);
        fact.addProperty("specified_luminance_verified", true);
        fact.addProperty("native_chicken_head_repaired_texels", repaired);
        fact.addProperty("visible_texels", visible);
        fact.addProperty("gray_min", minimum);
        fact.addProperty("gray_max", maximum);
        fact.addProperty("pixel_hash", Long.toUnsignedString(hash));
        return fact;
    }

    private static NativeImage imageCopy(ResourceLocation id) throws Exception {
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isPresent()) try (var input = resource.get().open()) { return NativeImage.read(input); }
        var texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        check(texture instanceof DynamicTexture, "The actual source atlas is readable: " + id);
        NativeImage original = ((DynamicTexture) texture).getPixels();
        check(original != null, "Dynamic source atlas retains its pixels");
        NativeImage copy = new NativeImage(original.getWidth(), original.getHeight(), false);
        for (int y = 0; y < copy.getHeight(); y++) for (int x = 0; x < copy.getWidth(); x++)
            copy.setPixelRGBA(x, y, original.getPixelRGBA(x, y));
        return copy;
    }

    /** Uses the normal dispatcher too, so ETF's per-entity random texture state is active during observation. */
    private static Capture capture(Mob entity) {
        Capture capture = new Capture();
        Minecraft.getInstance().getEntityRenderDispatcher().render(entity, 0D, 0D, 0D,
                0F, 1F, new PoseStack(), capture, 15728880);
        return capture;
    }

    private static final class Capture implements MultiBufferSource {
        private final List<Colors> buffers = new ArrayList<>();
        @Override public VertexConsumer getBuffer(RenderType type) {
            Colors colors = ModList.get().isLoaded("entity_texture_features") ? Loaded.record(this, type)
                    : new Colors(type.toString(), textureIdentifier(type));
            buffers.add(colors);
            return colors;
        }
        JsonArray json() {
            JsonArray result = new JsonArray();
            for (Colors buffer : buffers) {
                JsonObject fact = new JsonObject();
                fact.addProperty("render_type", buffer.type);
                if (buffer.texture != null) fact.addProperty("effective_texture", buffer.texture.toString());
                fact.addProperty("vertices", buffer.vertices);
                JsonArray colors = new JsonArray();
                buffer.colors.forEach(color -> colors.add(hex(color)));
                fact.add("colors_argb", colors);
                result.add(fact);
            }
            return result;
        }
    }

    private static class Colors implements VertexConsumer {
        private final String type;
        private final ResourceLocation texture;
        private final Set<Integer> colors = new LinkedHashSet<>();
        private int vertices;
        Colors(String type, ResourceLocation texture) { this.type = type; this.texture = texture; }
        @Override public VertexConsumer addVertex(float x, float y, float z) { vertices++; return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            colors.add(alpha << 24 | red << 16 | green << 8 | blue); return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    /** EMF needs ETF metadata to keep textureOverride child draws on the supplied recorder. */
    private static final class Loaded {
        static Colors record(Capture capture, RenderType requested) {
            VertexConsumer actual = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(requested);
            var etf = actual instanceof traben.entity_texture_features.utils.ETFVertexConsumer metadata ? metadata : null;
            RenderType effective = etf == null || etf.etf$getRenderLayer() == null ? requested : etf.etf$getRenderLayer();
            var atlas = etf == null ? null : etf.etf$getETFTexture();
            ResourceLocation texture = atlas == null ? textureIdentifier(effective) : atlas.thisIdentifier;
            return new EtfColors(effective, texture, atlas,
                    new traben.entity_texture_features.utils.URenderTypeToVertexConsumer(capture));
        }

        private static final class EtfColors extends Colors implements traben.entity_texture_features.utils.ETFVertexConsumer {
            private traben.entity_texture_features.features.texture_handlers.ETFTexture atlas;
            private traben.entity_texture_features.utils.URenderTypeToVertexConsumer provider;
            private RenderType layer;
            EtfColors(RenderType layer, ResourceLocation texture,
                    traben.entity_texture_features.features.texture_handlers.ETFTexture atlas,
                    traben.entity_texture_features.utils.URenderTypeToVertexConsumer provider) {
                super(layer.toString(), texture);
                this.layer = layer; this.atlas = atlas; this.provider = provider;
            }
            @Override public traben.entity_texture_features.features.texture_handlers.ETFTexture etf$getETFTexture() { return atlas; }
            @Override public traben.entity_texture_features.utils.URenderTypeToVertexConsumer etf$getProvider() { return provider; }
            @Override public RenderType etf$getRenderLayer() { return layer; }
            @Override public void etf$initETFVertexConsumer(traben.entity_texture_features.utils.URenderTypeToVertexConsumer provider,
                    RenderType layer) { this.provider = provider; this.layer = layer; }
        }
    }

    private static ResourceLocation textureIdentifier(RenderType type) {
        var match = Pattern.compile("texture\\[Optional\\[([^\\]]+)\\]").matcher(type.toString());
        return match.find() ? ResourceLocation.parse(match.group(1)) : null;
    }

    private static JsonObject woolAtlasFact(ResourceLocation texture) throws Exception {
        JsonObject fact = textureFact(texture);
        int visible = 0, red = 0;
        long hash = 1469598103934665603L;
        try (var input = Minecraft.getInstance().getResourceManager().open(texture); NativeImage image = NativeImage.read(input)) {
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixelRGBA(x, y);
                hash = (hash ^ Integer.toUnsignedLong(pixel)) * 1099511628211L;
                if (pixel >>> 24 == 0) continue;
                visible++;
                int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
                if (r > g * 1.3 && r > b * 1.3) red++;
            }
        }
        check(visible > 0, "The actual wool atlas has visible pixels");
        fact.addProperty("visible_pixels", visible);
        fact.addProperty("red_dominant_pixels", red);
        fact.addProperty("pixel_hash", Long.toUnsignedString(hash));
        return fact;
    }

    private static JsonObject textureFact(ResourceLocation id) throws Exception {
        JsonObject fact = new JsonObject();
        fact.addProperty("location", id.toString());
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isPresent()) {
            fact.addProperty("source_pack", resource.get().sourcePackId());
            try (var stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                fact.addProperty("width", image.getWidth());
                fact.addProperty("height", image.getHeight());
            }
        } else {
            var uploaded = Minecraft.getInstance().getTextureManager().getTexture(id);
            check(uploaded instanceof DynamicTexture, "Body texture has source bytes or an uploaded dynamic atlas: " + id);
            NativeImage image = ((DynamicTexture) uploaded).getPixels();
            check(image != null, "Dynamic body atlas retains inspectable pixels");
            fact.addProperty("source_pack", "uploaded_dynamic_texture");
            fact.addProperty("width", image.getWidth());
            fact.addProperty("height", image.getHeight());
        }
        return fact;
    }

    private static boolean customized(EntityModel<?> model) throws Exception {
        if (!ModList.get().isLoaded("entity_model_features")) return false;
        Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
        return Boolean.TRUE.equals(api.getMethod("isModelCustomizedByEMF", EntityModel.class).invoke(null, model));
    }

    @SuppressWarnings({"rawtypes", "unchecked"}) private static LivingEntityRenderer renderer(LivingEntity entity) {
        return (LivingEntityRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    }

    @SuppressWarnings("unchecked") private static ResourceLocation texture(LivingEntity entity) {
        return renderer(entity).getTextureLocation(entity);
    }

    private static String hex(int color) { return String.format("0x%08X", color); }

    private static Path output() {
        return Path.of(System.getProperty("mite.sickLivestockAuditOutput", "build/verification/sick-livestock-client"))
                .toAbsolutePath().normalize();
    }

    private static void finish(Minecraft mc, Throwable failure) {
        if (done) return;
        done = true;
        if (!REPORT.has("failures")) REPORT.add("failures", new JsonArray());
        if (failure != null) {
            REPORT.addProperty("status", "failed");
            REPORT.getAsJsonArray("failures").add(failure.toString());
            Mite.LOGGER.error("Sick livestock client audit failed", failure);
        }
        REPORT.addProperty("safe_client_shutdown_requested", true);
        try {
            Files.createDirectories(output());
            Files.writeString(output().resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n",
                    StandardCharsets.UTF_8);
        } catch (Exception secondary) { Mite.LOGGER.error("Cannot save sick livestock client audit", secondary); }
        mc.stop();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Row(Mob entity, String label, ResourceLocation normal, boolean custom,
                       boolean saddled, boolean sheared, Object normalRenderer,
                       EntityModel<?> normalModel, JsonObject fact) {}
}
