package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.mob.LegacyArachnidModel;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
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

/** Opt-in real GPU spider rendering in a disposable client; optional EMF is read reflectively. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class SpiderResourcePackClientAudit {
    private static final List<MobKind> KINDS = List.of(MobKind.WOOD_SPIDER, MobKind.BLACK_WIDOW_SPIDER,
            MobKind.DEMON_SPIDER, MobKind.PHASE_SPIDER);
    private static final JsonObject REPORT = new JsonObject();
    private static boolean baked, started, done, emf;
    private static int waiting;
    private static AuditScreen screen;
    private static LivingEntity rendering;
    private static List<Float> renderedPose;
    private static int renderedEvents, sampleEmfCalls;
    private static String sampleEmfError;

    private SpiderResourcePackClientAudit() {}

    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event) {
            if (Boolean.getBoolean("mite.spiderResourcePackAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.spiderResourcePackAudit") || !baked || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                check(directory.toString().replace('\\', '/').contains("/build/verification/"),
                        "Spider audit requires an isolated client directory under build/verification");
                started = true;
                emf = ModList.get().isLoaded("entity_model_features");
                REPORT.addProperty("author", "NingMeng123");
                REPORT.addProperty("entity_model_features_loaded", emf);
                REPORT.addProperty("entity_texture_features_loaded", ModList.get().isLoaded("entity_texture_features"));
                REPORT.addProperty("game_directory", directory.toString());
                REPORT.addProperty("existing_worlds_opened", false);
                REPORT.addProperty("visual_verdict", "requires_manual_screenshot_review");
                REPORT.add("failures", new JsonArray());
                REPORT.add("entities", new JsonArray());
                REPORT.add("screenshots", new JsonArray());
                if (emf) for (String model : List.of("spider", "cave_spider")) {
                    var id = ResourceLocation.withDefaultNamespace("optifine/cem/" + model + ".jem");
                    var resource = mc.getResourceManager().getResource(id).orElseThrow(
                            () -> new IllegalStateException("Enabled FA+Spiders resource required: " + id));
                    REPORT.addProperty(model + "_jem_pack", resource.sourcePackId());
                }
                if (emf) Loaded.install();
                mc.options.pauseOnLostFocus = false;
                String world = "spider-resource-pack-audit-" + System.currentTimeMillis();
                REPORT.addProperty("disposable_world", world);
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261026L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++waiting < 3600, "Timed out loading or rendering the isolated spider audit world");
            if (screen == null && mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                screen = new AuditScreen(mc);
                mc.setScreen(screen);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent public static void rendered(RenderLivingEvent.Post<?, ?> event) {
        if (rendering == null || event.getEntity() != rendering) return;
        renderedPose = pose(event.getRenderer().getModel());
        renderedEvents++;
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post ignored) {
        Minecraft mc = Minecraft.getInstance();
        if (done || screen == null || mc.screen != screen || screen.frames < 3) return;
        try {
            Path screenshot = artifact(emf ? "resource-pack-render.png" : "legacy-fallback-render.png");
            Files.createDirectories(screenshot.getParent());
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(image.getWidth() > 100 && image.getHeight() > 100, "The real GPU framebuffer was captured");
                image.writeToFile(screenshot);
                REPORT.addProperty("framebuffer_width", image.getWidth());
                REPORT.addProperty("framebuffer_height", image.getHeight());
            }
            REPORT.getAsJsonArray("screenshots").add(screenshot.toString());
            REPORT.addProperty("actual_living_renderer_events", renderedEvents);
            REPORT.addProperty("client_only_entities_added_to_world", false);
            REPORT.addProperty("status", emf ? "passed_real_spider_resource_pack_rendering" : "passed_legacy_spider_fallback_rendering");
            finish(mc, null);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static final class AuditScreen extends Screen {
        private final List<Row> rows = new ArrayList<>();
        private int frames;

        AuditScreen(Minecraft mc) throws Exception {
            super(Component.literal("Spider model resource-pack compatibility audit"));
            add(mc, EntityType.SPIDER, null);
            add(mc, EntityType.CAVE_SPIDER, null);
            Set<ModelPart> roots = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<Long> colors = new java.util.HashSet<>();
            for (MobKind kind : KINDS) {
                Row row = add(mc, MiteMobs.type(kind), kind);
                EntityModel<?> model = renderer(row.entity).getModel();
                check(model instanceof LegacyArachnidModel, "Custom spider retains the compatible SpiderModel implementation: " + kind);
                ModelPart root = root(model);
                check(roots.add(root), "Every species owns a separate model root: " + kind);
                check(customized(model) == emf, "Custom model activation follows EMF and its enabled spider pack: " + kind);
                var body = texture(row.entity);
                row.fact.addProperty("body_texture", body.toString());
                row.fact.addProperty("independent_model_root", true);
                row.fact.addProperty("emf_customized", customized(model));
                if (emf) {
                    check(root.getClass().getName().equals("traben.entity_model_features.models.parts.EMFModelPartRoot"),
                            "Standard spider layer produced a real EMF model root: " + kind);
                    check(root.getClass().getField("containsCustomModel").getBoolean(root), "The root contains actual custom geometry");
                    var expected = ResourceLocation.fromNamespaceAndPath("mite", "dynamic/spider/" + kind.path());
                    check(body.equals(expected), "The species body uses its dynamic FA texture: " + kind);
                    var glow = ResourceLocation.fromNamespaceAndPath("mite", "dynamic/spider/" + kind.path() + "_eyes");
                    long hash = dynamic(mc, body, row.fact, "body", false);
                    check(colors.add(hash), "Species have distinct dynamically adapted body colors: " + kind);
                    dynamic(mc, glow, row.fact, "eyes", true);
                } else {
                    String name = kind == MobKind.BLACK_WIDOW_SPIDER ? "black_widow" : kind.path();
                    check(body.equals(ResourceLocation.fromNamespaceAndPath("mite", "textures/entity/spider/" + name + ".png")),
                            "No-EMF fallback keeps the original species texture: " + kind);
                    try (var stream = mc.getResourceManager().open(body); NativeImage image = NativeImage.read(stream)) {
                        check(image.getWidth() == 64 && image.getHeight() == 32, "The legacy atlas remains 64x32: " + kind);
                        row.fact.addProperty("body_width", image.getWidth());
                        row.fact.addProperty("body_height", image.getHeight());
                    }
                    check(root.getAllParts().count() == 12, "Fallback keeps the original eleven spider parts and root");
                }
            }
            REPORT.addProperty("independent_custom_roots", roots.size());
            REPORT.addProperty("distinct_dynamic_body_atlases", colors.size());
        }

        private Row add(Minecraft mc, EntityType<?> type, MobKind kind) throws Exception {
            LivingEntity entity = (LivingEntity) type.create(mc.level);
            check(entity != null, "Spider entity constructor succeeds: " + type);
            entity.setPos(mc.player.position());
            entity.setYRot(0); entity.yRotO = 0; entity.yBodyRot = entity.yBodyRotO = 0;
            entity.yHeadRot = entity.yHeadRotO = 0;
            JsonObject fact = new JsonObject();
            fact.addProperty("entity", EntityType.getKey(type).toString());
            fact.addProperty("emf_customized", customized(renderer(entity).getModel()));
            fact.add("samples", new JsonArray());
            REPORT.getAsJsonArray("entities").add(fact);
            Row row = new Row(entity, kind, fact);
            rows.add(row);
            return row;
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try {
                graphics.fill(0, 0, width, height, 0xFF17202B);
                graphics.drawCenteredString(font, "Spider compatibility: idle / walking / airborne", width / 2, 6, 0xFFF0F3FA);
                int label = Math.min(140, Math.max(95, width / 5));
                int cell = Math.max(1, (width - label - 8) / 3);
                int rowHeight = Math.max(20, (height - 30) / rows.size());
                for (int i = 0; i < rows.size(); i++) {
                    Row row = rows.get(i);
                    int y = 25 + i * rowHeight;
                    String name = row.fact.get("entity").getAsString();
                    graphics.drawString(font, name.substring(name.indexOf(':') + 1), 4, y + rowHeight / 2, 0xFFE5E9F4, false);
                    List<Float> idle = null;
                    for (int column = 0; column < 3; column++) {
                        int x = label + column * cell;
                        graphics.fill(x, y, x + cell - 3, y + rowHeight - 3, 0xFF2D3948);
                        row.entity.walkAnimation.update(0F, 1F);
                        row.entity.tickCount = column == 0 ? 40 : 60;
                        row.entity.setOnGround(column != 2);
                        row.entity.setDeltaMovement(column == 2 ? new Vec3(0, .2, 0) : Vec3.ZERO);
                        if (column == 1) for (int step = 0; step < 6; step++) row.entity.walkAnimation.update(.8F, 1F);
                        List<Float> visible = draw(graphics, row.entity, x + cell / 2F,
                                y + rowHeight - 4F, Math.min(cell * .28F, rowHeight * .55F));
                        if (column == 0) idle = visible;
                        if (column == 1) check(!samePose(idle, visible), "Real rendered walking bones differ from idle: " + name);
                        if (frames == 0) {
                            JsonObject sample = new JsonObject();
                            sample.addProperty("phase", List.of("idle", "walking", "airborne").get(column));
                            sample.addProperty("real_renderer_observed", true);
                            sample.addProperty("actual_emf_animation_calls", sampleEmfCalls);
                            sample.addProperty("walking_bones_differ_from_idle", column == 1);
                            sample.addProperty("on_ground", row.entity.onGround());
                            sample.add("rendered_pose", new GsonBuilder().create().toJsonTree(visible));
                            row.fact.getAsJsonArray("samples").add(sample);
                        }
                    }
                }
                graphics.flush();
                frames++;
            } catch (Throwable failure) { finish(Minecraft.getInstance(), failure); }
            finally { rendering = null; }
        }

        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public boolean isPauseScreen() { return true; }
    }

    private static long dynamic(Minecraft mc, ResourceLocation id, JsonObject fact, String role, boolean eyes) throws Exception {
        var texture = mc.getTextureManager().getTexture(id);
        check(texture instanceof DynamicTexture, "The adapted " + role + " is a real uploaded DynamicTexture: " + id);
        NativeImage image = ((DynamicTexture) texture).getPixels();
        check(image != null, "The dynamic atlas retains its pixels: " + id);
        try (var stream = mc.getResourceManager().open(ResourceLocation.withDefaultNamespace("textures/entity/spider/spider.png"));
                NativeImage template = NativeImage.read(stream)) {
            check(image.getWidth() == template.getWidth() && image.getHeight() == template.getHeight(),
                    "The adapted atlas matches the active resource pack dimensions: " + id);
        }
        int nontransparent = 0;
        long hash = 1469598103934665603L;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getPixelRGBA(x, y);
            hash = (hash ^ Integer.toUnsignedLong(pixel)) * 1099511628211L;
            if ((pixel >>> 24) != 0) nontransparent++;
        }
        check(nontransparent > 0, "The " + role + " atlas has visible pixels: " + id);
        if (eyes) check(nontransparent < image.getWidth() * image.getHeight() / 2, "Only the eyes glow; the body is not a fully emissive overlay");
        fact.addProperty(role + "_width", image.getWidth());
        fact.addProperty(role + "_height", image.getHeight());
        fact.addProperty(role + "_visible_pixels", nontransparent);
        fact.addProperty(role + "_pixel_hash", Long.toUnsignedString(hash));
        Path atlas = artifact(id.getPath().replace('/', '-') + ".png");
        Files.createDirectories(atlas.getParent()); image.writeToFile(atlas);
        fact.addProperty(role + "_atlas_preview", atlas.toString());
        return hash;
    }

    private static List<Float> draw(GuiGraphics graphics, LivingEntity entity, float x, float y, float scale) {
        rendering = entity; renderedPose = null; sampleEmfCalls = 0; sampleEmfError = null;
        try {
            InventoryScreen.renderEntityInInventory(graphics, x, y, scale, new Vector3f(),
                    new Quaternionf().rotationZ((float)Math.PI).rotateX(.3F).rotateY((float)Math.PI + .45F), null, entity);
        } finally { rendering = null; }
        check(renderedPose != null, "The actual LivingEntityRenderer completed the spider draw");
        check(sampleEmfError == null, "EMF completes the real spider animation without errors: " + sampleEmfError);
        if (emf) check(sampleEmfCalls > 0, "Actual EMF animation executes for the visible spider sample: " + entity.getType());
        return renderedPose;
    }

    /** This optional implementation is resolved only in the EMF-enabled audit run. */
    private static final class Loaded {
        static void install() throws Exception {
            check(traben.entity_model_features.EMFAnimationApi.registerAnimationHook(new Observer()),
                    "The real EMF animation observer registers successfully");
        }

        private static final class Observer extends traben.entity_model_features.EMFAnimationApi.EMFAnimationHook {
            @Override public void onAnimationEnd(AnimationContext context, boolean cancelled) {
                if (rendering == null || context.animatingModelRoot() != root(renderer(rendering).getModel())) return;
                sampleEmfCalls++;
                if (context.error() != null) sampleEmfError = context.error().toString();
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LivingEntityRenderer renderer(LivingEntity entity) {
        return (LivingEntityRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    }

    @SuppressWarnings("unchecked") private static ResourceLocation texture(LivingEntity entity) {
        return renderer(entity).getTextureLocation(entity);
    }

    private static ModelPart root(EntityModel<?> model) {
        return ((HierarchicalModel<?>)model).root();
    }

    private static boolean customized(EntityModel<?> model) throws Exception {
        if (!emf) return false;
        Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
        return Boolean.TRUE.equals(api.getMethod("isModelCustomizedByEMF", EntityModel.class).invoke(null, model));
    }

    private static List<Float> pose(EntityModel<?> model) {
        List<Float> result = new ArrayList<>();
        root(model).getAllParts().forEach(part -> result.addAll(List.of(part.x, part.y, part.z,
                part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale)));
        return result;
    }

    private static boolean samePose(List<Float> first, List<Float> second) {
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) if (Math.abs(first.get(i) - second.get(i)) > .0001F) return false;
        return true;
    }

    private static Path output() {
        return Path.of(System.getProperty("mite.spiderResourcePackAuditOutput", "build/verification/spider-resource-pack-client-audit.json"))
                .toAbsolutePath().normalize();
    }

    private static Path artifact(String name) {
        return output().resolveSibling(output().getFileName().toString().replaceFirst("\\.json$", "") + "-" + name);
    }

    private static void finish(Minecraft mc, Throwable failure) {
        if (done) return;
        done = true;
        if (!REPORT.has("failures")) REPORT.add("failures", new JsonArray());
        if (failure != null) {
            REPORT.addProperty("status", "failed");
            REPORT.getAsJsonArray("failures").add(failure.toString());
            Mite.LOGGER.error("Spider resource-pack audit failed", failure);
        }
        REPORT.addProperty("safe_client_shutdown_requested", true);
        try {
            Path path = output(); Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n", StandardCharsets.UTF_8);
        } catch (Exception writeFailure) { Mite.LOGGER.error("Cannot save spider resource-pack audit", writeFailure); }
        mc.stop();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Row(LivingEntity entity, MobKind kind, JsonObject fact) {}
}
