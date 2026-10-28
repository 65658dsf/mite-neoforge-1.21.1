package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.EnderDragonModelCompatibility;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
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
import org.joml.Quaternionf;

/** Real dragon renderer, optional EDP stages and CEM resources before/after reload. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class EnderDragonCompatibilityClientAudit {
    private static final JsonObject REPORT = new JsonObject();
    private static boolean baked, started, done, edp, emf;
    private static int waiting, reloads;
    private static AuditScreen screen;
    private static CompletableFuture<Void> reload;
    private static EntityModel<?> previousModel;

    private EnderDragonCompatibilityClientAudit() {}

    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted ignored) {
            if (Boolean.getBoolean("mite.enderDragonCompatibilityAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.enderDragonCompatibilityAudit") || !baked || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                check(directory.toString().replace('\\', '/').contains("/build/verification/"),
                        "Dragon audit requires an isolated game directory under build/verification");
                started = true;
                edp = ModList.get().isLoaded("enderdragonplus");
                emf = ModList.get().isLoaded("entity_model_features");
                REPORT.addProperty("author", "NingMeng123");
                REPORT.addProperty("enderdragonplus_loaded", edp);
                REPORT.addProperty("entity_model_features_loaded", emf);
                REPORT.addProperty("entity_texture_features_loaded", ModList.get().isLoaded("entity_texture_features"));
                REPORT.addProperty("acceleratedrendering_loaded", ModList.get().isLoaded("acceleratedrendering"));
                REPORT.addProperty("game_directory", directory.toString());
                REPORT.addProperty("existing_worlds_opened", false);
                REPORT.addProperty("visual_verdict", "requires_manual_screenshot_review");
                REPORT.add("failures", new JsonArray());
                REPORT.add("samples", new JsonArray());
                REPORT.add("screenshots", new JsonArray());
                mc.options.pauseOnLostFocus = false;
                String world = "ender-dragon-compatibility-audit-" + System.currentTimeMillis();
                REPORT.addProperty("disposable_world", world);
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261028L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++waiting < 3600, "Timed out loading, rendering or reloading the isolated dragon audit");
            if (reload != null) {
                if (!reload.isDone()) return;
                reload.join();
                reload = null;
                reloads++;
            }
            if (screen == null && mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                screen = new AuditScreen(mc);
                mc.setScreen(screen);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post ignored) {
        Minecraft mc = Minecraft.getInstance();
        if (done || screen == null || mc.screen != screen || screen.frames < 4) return;
        try {
            Path screenshot = artifact(reloads == 0 ? "before-reload.png" : "after-reload.png");
            Files.createDirectories(screenshot.getParent());
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(image.getWidth() > 100 && image.getHeight() > 100, "The actual GPU framebuffer was captured");
                image.writeToFile(screenshot);
            }
            REPORT.getAsJsonArray("screenshots").add(screenshot.toString());
            screen.closeStages();
            if (reloads == 0) {
                previousModel = screen.model;
                screen = null;
                reload = mc.reloadResourcePacks();
            } else {
                REPORT.addProperty("resource_reload_count", reloads);
                REPORT.addProperty("client_only_entities_added_to_world", false);
                REPORT.addProperty("status", "passed_real_dragon_rendering_and_resource_reload");
                finish(mc, null);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static final class AuditScreen extends Screen {
        private final List<EnderDragon> dragons = new ArrayList<>();
        private final List<JsonObject> samples = new ArrayList<>();
        private final EntityModel<?> model;
        private ModelPart[] segmentedParts;
        private boolean customModel;
        private int frames;

        AuditScreen(Minecraft mc) throws Exception {
            super(Component.literal("Ender Dragon Plus resource-pack compatibility"));
            for (int stage = 1; stage <= 3; stage++) {
                EnderDragon dragon = EntityType.ENDER_DRAGON.create(mc.level);
                check(dragon != null, "The vanilla dragon constructor succeeds");
                dragon.setPos(mc.player.position());
                dragon.setId(-17000 - stage - 10 * reloads);
                dragon.tickCount = 80;
                dragon.flapTime = dragon.oFlapTime = .25F;
                if (edp) setStage(dragon, stage);
                dragons.add(dragon);
                JsonObject sample = new JsonObject();
                sample.addProperty("stage", stage);
                sample.addProperty("after_resource_reload", reloads != 0);
                sample.addProperty("enderdragonplus_glow_requested", edp);
                REPORT.getAsJsonArray("samples").add(sample);
                samples.add(sample);
            }
            EnderDragonRenderer renderer = (EnderDragonRenderer)mc.getEntityRenderDispatcher().getRenderer(dragons.getFirst());
            Field modelField = EnderDragonRenderer.class.getDeclaredField("model");
            modelField.setAccessible(true);
            model = (EntityModel<?>)modelField.get(renderer);
            if (previousModel != null) check(previousModel != model, "Resource reload rebuilds the actual dragon renderer model");
            JsonObject resources = new JsonObject();
            resources.addProperty("after_resource_reload", reloads != 0);
            ResourceLocation texture = renderer.getTextureLocation(dragons.getFirst());
            var bodyResource = mc.getResourceManager().getResource(texture).orElseThrow();
            resources.addProperty("body_texture", texture.toString());
            resources.addProperty("body_texture_pack", bodyResource.sourcePackId());
            try (var input = bodyResource.open(); NativeImage body = NativeImage.read(input)) {
                resources.addProperty("body_width", body.getWidth());
                resources.addProperty("body_height", body.getHeight());
                var jem = mc.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("optifine/cem/dragon.jem"));
                if (emf && jem.isPresent()) {
                    resources.addProperty("dragon_jem_pack", jem.get().sourcePackId());
                    Set<String> segmentedIds = new LinkedHashSet<>();
                    try (var reader = jem.get().openAsReader()) {
                        var definition = JsonParser.parseReader(reader).getAsJsonObject();
                        var size = definition.getAsJsonArray("textureSize");
                        check(size.get(0).getAsInt() == body.getWidth() && size.get(1).getAsInt() == body.getHeight(),
                                "The actual CEM model and body texture use the same atlas dimensions");
                        for (var element : definition.getAsJsonArray("models")) {
                            var part = element.getAsJsonObject();
                            for (String key : List.of("id", "part")) {
                                if (part.has(key) && part.get(key).getAsString().matches("(?:neck[1-5]|tail(?:[1-9]|1[0-2]))"))
                                    segmentedIds.add(part.get(key).getAsString());
                            }
                        }
                    }
                    Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
                    check(Boolean.TRUE.equals(api.getMethod("isModelCustomizedByEMF", EntityModel.class).invoke(null, model)),
                            "The selected custom dragon model is active in the actual renderer");
                    Object root = Class.forName("traben.entity_model_features.models.IEMFModel")
                            .getMethod("emf$getEMFRootModel").invoke(model);
                    check(root != null, "The customized dragon retains its actual EMF root");
                    check(root.getClass().getField("containsCustomModel").getBoolean(root), "The EMF root contains custom geometry");
                    customModel = true;
                    resources.addProperty("emf_custom_model_active", true);
                    resources.addProperty("model_part_count", ((ModelPart)root).getAllParts().count());
                    if (!segmentedIds.isEmpty()) {
                        check(segmentedIds.size() == 17, "The selected segmented dragon pack defines all five neck and twelve tail segments");
                        Set<String> actualIds = modelPartNames((ModelPart)root);
                        check(actualIds.containsAll(segmentedIds),
                                "The actual rendered model retains all seventeen neck/tail segment IDs: " + actualIds);
                        resources.addProperty("actual_neck_tail_segment_count", segmentedIds.size());
                        resources.add("actual_model_part_names", new GsonBuilder().create().toJsonTree(actualIds));
                        segmentedParts = verifySegmentSelection(model, resources);
                    }
                } else resources.addProperty("emf_custom_model_active", false);
            }
            if (!REPORT.has("resources")) REPORT.add("resources", new JsonArray());
            REPORT.getAsJsonArray("resources").add(resources);
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try {
                graphics.fill(0, 0, width, height, 0xFF182330);
                graphics.drawCenteredString(font, "Dragon stages 1 / 2 / 3 - " + (reloads == 0 ? "initial resources" : "after resource reload"),
                        width / 2, 7, 0xFFFFFFFF);
                int cell = (width - 16) / 3;
                int row = (height - 45) / 4;
                for (int stage = 0; stage < dragons.size(); stage++) {
                    EnderDragon dragon = dragons.get(stage);
                    Set<String> buffers = new LinkedHashSet<>();
                    JsonArray tailPoses = new JsonArray();
                    for (int view = 0; view < 4; view++) {
                        int x = 8 + stage * cell;
                        int y = 28 + view * row;
                        graphics.fill(x, y, x + cell - 4, y + row - 4, 0xFF334759);
                        String label = List.of("front", "back", "side", segmentedParts == null ? "spine only" : "tail only").get(view);
                        graphics.drawString(font, "Stage " + (stage + 1) + " / " + label, x + 5, y + 5, 0xFFFFFFFF, false);
                        graphics.enableScissor(x, y + 17, x + cell - 4, y + row - 4);
                        float scale = view == 3 ? cell * .05F : Math.min(cell * .026F, row * .075F);
                        float yaw = view == 0 ? .7F : view == 1 ? 3.8F : (float)Math.PI / 2F;
                        if (view == 3) drawTailOnly(graphics, dragon, x + cell * .5F, y + row * .57F, scale, yaw, buffers);
                        else draw(graphics, dragon, x + cell * .5F, y + row * .57F, scale, yaw, buffers);
                        graphics.disableScissor();
                        if (frames == 0 && segmentedParts != null) {
                            ModelPart tail = segmentedParts[5];
                            JsonObject pose = new JsonObject();
                            pose.addProperty("view", label);
                            pose.addProperty("after_actual_dispatcher_render", true);
                            pose.addProperty("tail1_y_rotation_degrees", Math.toDegrees(tail.yRot));
                            pose.add("tail1_position", new GsonBuilder().create().toJsonTree(List.of(tail.x, tail.y, tail.z)));
                            pose.add("tail1_rotation_radians", new GsonBuilder().create().toJsonTree(List.of(tail.xRot, tail.yRot, tail.zRot)));
                            pose.addProperty("tail1_visible_after_render", tail.visible);
                            tailPoses.add(pose);
                        }
                    }
                    if (frames == 0) {
                        JsonObject sample = samples.get(stage);
                        sample.addProperty("actual_renderer_completed", true);
                        sample.addProperty("views_rendered", 4);
                        if (segmentedParts != null) sample.add("actual_tail1_poses", tailPoses);
                        sample.add("requested_render_buffers", new GsonBuilder().create().toJsonTree(buffers));
                        check(buffers.stream().anyMatch(value -> value.contains("dragon")), "Actual dragon render buffers were requested");
                        if (edp && customModel) {
                            check(buffers.stream().noneMatch(value -> value.contains("enderdragonplus:textures/entity/dragon_glow_")),
                                    "Vanilla-UV EDP glow atlases are filtered before reaching the custom model's actual buffers");
                            sample.addProperty("incompatible_edp_glow_buffers_filtered", true);
                        } else if (edp) {
                            for (String layer : List.of("cracks", "veins")) check(buffers.stream().anyMatch(value -> value.contains(
                                            "enderdragonplus:textures/entity/dragon_glow_" + layer + ".png")),
                                    "The vanilla dragon retains its original EDP glow layer: " + layer);
                            sample.addProperty("vanilla_edp_glow_layers_preserved", true);
                        }
                    }
                }
                graphics.flush();
                frames++;
            } catch (Throwable failure) { finish(Minecraft.getInstance(), failure); }
        }

        private void closeStages() throws Exception {
            if (edp) for (EnderDragon dragon : dragons) Class.forName("com.enderdragonplus.client.DragonStageClient")
                    .getMethod("remove", int.class).invoke(null, dragon.getId());
        }

        private void drawTailOnly(GuiGraphics graphics, EnderDragon dragon, float x, float y, float scale,
                float yaw, Set<String> buffers) throws Exception {
            Map<ModelPart, Boolean> visibility = new IdentityHashMap<>();
            for (Field field : EnderDragonRenderer.DragonModel.class.getDeclaredFields()) {
                if (field.getType() != ModelPart.class || field.getName().equals("neck")) continue;
                field.setAccessible(true);
                ModelPart part = (ModelPart)field.get(model);
                visibility.put(part, part.visible);
            }
            if (segmentedParts != null) for (int i = 0; i < segmentedParts.length; i++) {
                if (i == 5) continue;
                ModelPart part = segmentedParts[i];
                visibility.put(part, part.visible);
            }
            try {
                visibility.keySet().forEach(part -> part.visible = false);
                draw(graphics, dragon, x, y, scale, yaw, buffers);
            } finally {
                visibility.forEach((part, original) -> part.visible = original);
            }
        }

        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public boolean isPauseScreen() { return true; }
    }

    private static void draw(GuiGraphics graphics, EnderDragon dragon, float x, float y, float scale, float yaw, Set<String> buffers) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 80F);
        graphics.pose().scale(scale, scale, -scale);
        graphics.pose().mulPose(new Quaternionf().rotationZ((float)Math.PI).rotateX(.4F).rotateY(yaw));
        Lighting.setupForEntityInInventory();
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        MultiBufferSource recording = type -> { buffers.add(type.toString()); return graphics.bufferSource().getBuffer(type); };
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(dragon, 0, 0, 0, 0F, 1F, graphics.pose(), recording, 15728880));
            graphics.flush();
        } finally {
            dispatcher.setRenderShadow(true);
            graphics.pose().popPose();
            Lighting.setupFor3DItems();
        }
    }

    /** Exercise real stage/glow rendering without adding synthetic entities to the client world. */
    private static void setStage(EnderDragon dragon, int stage) throws Exception {
        Class<?> stages = Class.forName("com.enderdragonplus.client.DragonStageClient");
        stages.getMethod("set", int.class, int.class).invoke(null, dragon.getId(), stage);
        Field auraMap = stages.getDeclaredField("AURAS");
        auraMap.setAccessible(true);
        Object map = auraMap.get(null);
        Object aura = map.getClass().getMethod("get", int.class).invoke(map, dragon.getId());
        for (String name : List.of("strength", "rage")) {
            Field value = aura.getClass().getDeclaredField(name);
            value.setAccessible(true);
            value.setFloat(aura, name.equals("strength") ? 1F : .8F);
        }
        check(((Number)stages.getMethod("glow", int.class).invoke(null, dragon.getId())).floatValue() > .5F,
                "The real EnderDragonPlus glow path is active for the sample");
    }

    private static Set<String> modelPartNames(ModelPart root) throws Exception {
        Field children = ModelPart.class.getDeclaredField("children");
        children.setAccessible(true);
        Set<String> names = new LinkedHashSet<>();
        collectPartNames(root, children, names, Collections.newSetFromMap(new IdentityHashMap<>()));
        return names;
    }

    private static ModelPart[] verifySegmentSelection(EntityModel<?> model, JsonObject resources) throws Exception {
        Field neckField = EnderDragonRenderer.DragonModel.class.getDeclaredField("neck");
        neckField.setAccessible(true);
        ModelPart spine = (ModelPart)neckField.get(model);
        ModelPart[] segments = new ModelPart[17];
        for (ModelPart child : (ModelPart[])spine.getClass().getMethod("getAllEMFCustomChildren").invoke(spine)) {
            if (!child.hasChild("EMF_mite_dragon_segment")) continue;
            ModelPart marker = child.getChild("EMF_mite_dragon_segment");
            for (int i = 0; i < segments.length; i++) {
                if (!marker.hasChild("EMF_mite_dragon_segment_index_" + i)) continue;
                check(segments[i] == null, "Every neck/tail render index selects a unique baked segment: " + i);
                segments[i] = child;
            }
        }
        List<Boolean> visibility = new ArrayList<>();
        for (int i = 0; i < segments.length; i++) {
            check(segments[i] != null, "The baked spine contains the actual segment marker for index " + i);
            check(segments[i].visible, "The pack initially enables neck/tail segment " + i);
            visibility.add(segments[i].visible);
        }
        check(segments[0].getAllParts().anyMatch(part -> !part.isEmpty()), "The selected neck1 segment contains actual renderable geometry");
        check(segments[5].getAllParts().anyMatch(part -> !part.isEmpty()), "The selected tail1 segment contains actual renderable geometry");
        for (int index = 0; index < segments.length; index++) {
            int selected = index;
            EnderDragonModelCompatibility.renderSegment(spine, selected, () -> {
                int visible = 0;
                for (int other = 0; other < segments.length; other++) {
                    check(segments[other].visible == (other == selected),
                            "Actual spine draw " + selected + " exposes only its corresponding neck/tail geometry; checked " + other);
                    if (segments[other].visible) visible++;
                }
                check(visible == 1, "Each repeated spine draw exposes exactly one real CEM segment");
            });
            checkVisibilityRestored(segments, visibility);
        }
        RuntimeException sentinel = new RuntimeException("Expected dragon segment audit callback failure");
        try {
            EnderDragonModelCompatibility.renderSegment(spine, 5, () -> { throw sentinel; });
            throw new IllegalStateException("The segment render callback exception must propagate");
        } catch (RuntimeException failure) {
            if (failure != sentinel) throw failure;
        }
        checkVisibilityRestored(segments, visibility);
        resources.addProperty("actual_segment_draw_scopes_verified", segments.length);
        resources.addProperty("neck1_and_tail1_have_geometry", true);
        resources.addProperty("segment_visibility_restored_after_failure", true);
        return segments;
    }

    private static void checkVisibilityRestored(ModelPart[] segments, List<Boolean> visibility) {
        for (int i = 0; i < segments.length; i++) check(segments[i].visible == visibility.get(i),
                "The actual baked segment visibility is restored after the draw scope: " + i);
    }

    @SuppressWarnings("unchecked")
    private static void collectPartNames(ModelPart part, Field children, Set<String> names, Set<ModelPart> visited) throws Exception {
        if (!visited.add(part)) return;
        for (var entry : ((Map<String, ModelPart>)children.get(part)).entrySet()) {
            names.add(entry.getKey().replaceFirst("^EMF_", ""));
            collectPartNames(entry.getValue(), children, names, visited);
        }
    }

    private static Path output() {
        return Path.of(System.getProperty("mite.enderDragonCompatibilityAuditOutput", "build/verification/ender-dragon-client-audit.json"))
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
            Mite.LOGGER.error("Ender dragon compatibility client audit failed", failure);
        }
        REPORT.addProperty("safe_client_shutdown_requested", true);
        try {
            Path path = output();
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n", StandardCharsets.UTF_8);
        } catch (Exception writeFailure) { Mite.LOGGER.error("Cannot save ender dragon compatibility client audit", writeFailure); }
        mc.stop();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
