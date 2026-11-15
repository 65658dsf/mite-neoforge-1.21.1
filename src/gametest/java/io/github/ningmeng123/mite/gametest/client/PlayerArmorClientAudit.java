package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** Disposable-world GPU comparison of player and armor resource packs. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class PlayerArmorClientAudit {
    private static final JsonObject REPORT = new JsonObject();
    private static final String[] VIEW_NAMES = {"front", "rear", "left 55 deg", "right 55 deg"};
    private static final float[] VIEW_ANGLES = {(float) Math.PI, 0F,
            (float) Math.PI + .96F, (float) Math.PI - .96F};
    private static final List<PackGroup> GROUPS = new ArrayList<>();
    private static List<String> initialPacks = List.of();
    private static boolean started;
    private static boolean done;
    private static boolean restoring;
    private static int readyTicks;
    private static int groupIndex = -1;
    private static long startedAt;
    private static CompletableFuture<Void> reload;
    private static AuditScreen screen;
    private static Sample rendering;
    private static PlayerSkin auditSkin;
    private static PlayerInfo overriddenPlayerInfo;
    private static Object initialSkinLookup;
    private static boolean worldMode;
    private static boolean worldRequested;
    private static boolean worldReady;
    private static int worldPhase;
    private static int worldFrames;
    private static int worldStableTicks;
    private static int worldRenderEvents;
    private static Vec3 worldPosition;
    private static CompletableFuture<Void> worldSetup;
    private static JsonObject worldFact;
    private static CameraType initialCamera;
    private static boolean initialHideGui;

    private PlayerArmorClientAudit() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.playerArmorAudit") || done) return;
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE) GLFW.glfwHideWindow(window);
        mc.options.pauseOnLostFocus = false;
        try {
            if (started) check(System.nanoTime() - startedAt < 600_000_000_000L,
                    "Timed out loading or rendering the player armor audit");
            if (mc.getOverlay() != null) return;
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
                    onboarding.onClose();
                    return;
                }
                if (!(mc.screen instanceof TitleScreen)) return;
                begin(mc);
                return;
            }
            if (worldRequested) {
                tickWorld(mc);
                return;
            }
            if (reload != null) {
                if (!reload.isDone()) return;
                reload.join();
                reload = null;
                if (restoring) {
                    REPORT.addProperty("initial_pack_selection_restored", true);
                    REPORT.addProperty("status", "captured_real_player_armor_four_pack_combinations");
                    finish(mc, null);
                    return;
                }
                screen.beginGroup(mc, GROUPS.get(groupIndex));
                mc.setScreen(screen);
            }
            if (screen != null) return;
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null
                    || mc.getConnection() == null || mc.screen != null) return;
            if (++readyTicks < 200) return;
            screen = new AuditScreen(mc);
            mc.setScreen(screen);
            if (worldMode) {
                groupIndex = 0;
                screen.beginGroup(mc, GROUPS.getFirst());
            } else nextGroup(mc);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void begin(Minecraft mc) throws Exception {
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.toString().replace('\\', '/').contains("/build/verification/"),
                "Player armor audit requires an isolated directory under build/verification");
        started = true;
        startedAt = System.nanoTime();
        REPORT.addProperty("author", "NingMeng123");
        REPORT.addProperty("game_directory", directory.toString());
        REPORT.addProperty("existing_worlds_opened", false);
        REPORT.addProperty("client_only_entities_added_to_world", false);
        REPORT.addProperty("window_hidden", true);
        worldMode = Boolean.getBoolean("mite.playerArmorAuditWorld");
        initialCamera = mc.options.getCameraType();
        initialHideGui = mc.options.hideGui;
        REPORT.addProperty("world_mode", worldMode);
        REPORT.addProperty("full_suit", Boolean.getBoolean("mite.playerArmorAuditFullSuit"));
        REPORT.addProperty("visual_verdict", "requires_manual_screenshot_review");
        REPORT.addProperty("entity_model_features_loaded", ModList.get().isLoaded("entity_model_features"));
        REPORT.addProperty("entity_texture_features_loaded", ModList.get().isLoaded("entity_texture_features"));
        REPORT.addProperty("acceleratedrendering_loaded", ModList.get().isLoaded("acceleratedrendering"));
        REPORT.add("groups", new JsonArray());
        REPORT.add("failures", new JsonArray());
        REPORT.add("screenshots", new JsonArray());
        REPORT.add("world_scenes", new JsonArray());
        initialPacks = List.copyOf(mc.getResourcePackRepository().getSelectedIds());
        REPORT.add("initial_selected_packs", strings(initialPacks));
        List<String> selected = worldMode ? initialPacks : selectedPacks();
        for (String id : selected) check(mc.getResourcePackRepository().isAvailable(id), "Resource pack is available: " + id);
        if (worldMode) GROUPS.add(new PackGroup("00-initial-packs", selected));
        else {
            check(selected.stream().anyMatch(PlayerArmorClientAudit::isFaPlayer), "FA+Player pack is selected for comparison");
            check(selected.stream().anyMatch(PlayerArmorClientAudit::isTrabenArmor), "Traben armor pack is selected for comparison");
            GROUPS.add(new PackGroup("00-baseline", selected.stream().filter(id -> !isFaPlayer(id) && !isTrabenArmor(id)).toList()));
            GROUPS.add(new PackGroup("01-fa-player", selected.stream().filter(id -> !isTrabenArmor(id)).toList()));
            GROUPS.add(new PackGroup("02-traben-armor", selected.stream().filter(id -> !isFaPlayer(id)).toList()));
            GROUPS.add(new PackGroup("03-both", selected));
        }
        Files.createDirectories(output());
        String world = "player-armor-audit-" + System.currentTimeMillis();
        REPORT.addProperty("disposable_world", world);
        mc.createWorldOpenFlows().createFreshLevel(world,
                new LevelSettings(world, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261115L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(), mc.screen);
    }

    private static List<String> selectedPacks() {
        String configured = System.getProperty("mite.playerArmorAuditPacks", "").trim();
        if (configured.isEmpty()) return initialPacks;
        return java.util.Arrays.stream(configured.split("\\|"))
                .map(String::trim).filter(id -> !id.isEmpty()).distinct().toList();
    }

    private static boolean isFaPlayer(String id) { return id.toLowerCase(Locale.ROOT).contains("fa+player"); }
    private static boolean isTrabenArmor(String id) { return id.toLowerCase(Locale.ROOT).contains("traben"); }

    private static void nextGroup(Minecraft mc) {
        screen.group = null;
        screen.frames = 0;
        if (++groupIndex == GROUPS.size()) {
            restoring = true;
            mc.getResourcePackRepository().setSelected(initialPacks);
        } else mc.getResourcePackRepository().setSelected(GROUPS.get(groupIndex).packs);
        reload = mc.reloadResourcePacks();
    }

    private static void loadAuditSkin(Minecraft mc) throws Exception {
        String configured = System.getProperty("mite.playerArmorAuditSkin", "").trim();
        if (configured.isEmpty()) return;
        Path path = Path.of(configured);
        check(path.isAbsolute(), "Player armor audit skin requires an absolute PNG path");
        path = path.toAbsolutePath().normalize();
        check(Files.isRegularFile(path), "Player armor audit skin is available: " + path);
        NativeImage image;
        try (var stream = Files.newInputStream(path)) { image = NativeImage.read(stream); }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width != 64 || height != 64) {
            image.close();
            throw new IllegalStateException("Player armor audit skin requires a 64x64 modern wide-player PNG");
        }
        ResourceLocation texture = mc.getTextureManager().register("mite-player-armor-audit-skin", new DynamicTexture(image));
        auditSkin = new PlayerSkin(texture, null, null, null, PlayerSkin.Model.WIDE, true);
        REPORT.addProperty("custom_skin_path", path.toString());
        REPORT.addProperty("custom_skin_width", width);
        REPORT.addProperty("custom_skin_height", height);
        REPORT.addProperty("custom_skin_texture", texture.toString());
        overriddenPlayerInfo = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        check(overriddenPlayerInfo != null, "The actual local player's PlayerInfo is present");
        Field lookup = PlayerInfo.class.getDeclaredField("skinLookup");
        lookup.setAccessible(true);
        initialSkinLookup = lookup.get(overriddenPlayerInfo);
        lookup.set(overriddenPlayerInfo, (Supplier<PlayerSkin>) () -> auditSkin);
        check(mc.player.getSkin().texture().equals(texture), "The actual local player uses the requested audit skin");
        REPORT.addProperty("custom_skin_applied_to_actual_local_player", true);
    }

    @SubscribeEvent public static void rendered(RenderLivingEvent.Post<?, ?> event) {
        if (worldRequested && worldReady && !done && event.getEntity() == Minecraft.getInstance().player) {
            worldRenderEvents++;
            if (!worldFact.has("armor_layers")) {
                try { worldFact.add("armor_layers", armorLayers(event.getRenderer())); }
                catch (ReflectiveOperationException failure) { finish(Minecraft.getInstance(), failure); }
            }
        }
        if (rendering == null || event.getEntity() != rendering.player || done) return;
        rendering.draws++;
        if (rendering.fact.has("renderer_class")) return;
        try {
            var renderer = event.getRenderer();
            var model = renderer.getModel();
            rendering.fact.addProperty("renderer_class", renderer.getClass().getName());
            rendering.fact.addProperty("model_class", model.getClass().getName());
            if (ModList.get().isLoaded("entity_model_features")) {
                Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
                Object customized = api.getMethod("isModelCustomizedByEMF", net.minecraft.client.model.EntityModel.class)
                        .invoke(null, model);
                rendering.fact.addProperty("emf_customized", Boolean.TRUE.equals(customized));
            }
            rendering.fact.add("armor_layers", armorLayers(renderer));
        } catch (Throwable failure) { finish(Minecraft.getInstance(), failure); }
    }

    private static JsonArray armorLayers(LivingEntityRenderer<?, ?> renderer) throws ReflectiveOperationException {
        Field layersField = LivingEntityRenderer.class.getDeclaredField("layers");
        layersField.setAccessible(true);
        JsonArray layers = new JsonArray();
        for (Object layer : (List<?>) layersField.get(renderer)) {
            if (!(layer instanceof HumanoidArmorLayer<?, ?, ?>)) continue;
            JsonObject armor = new JsonObject();
            armor.addProperty("layer_class", layer.getClass().getName());
            for (String name : List.of("innerModel", "outerModel")) {
                Field modelField = HumanoidArmorLayer.class.getDeclaredField(name);
                modelField.setAccessible(true);
                armor.add(name, armorModel((HumanoidModel<?>) modelField.get(layer)));
            }
            layers.add(armor);
        }
        return layers;
    }

    private static JsonObject armorModel(HumanoidModel<?> model) throws ReflectiveOperationException {
        JsonObject fact = new JsonObject();
        fact.addProperty("model_class", model.getClass().getName());
        fact.add("head", part(model.head));
        fact.add("body", part(model.body));
        fact.add("right_arm", part(model.rightArm));
        fact.add("left_arm", part(model.leftArm));
        fact.add("right_leg", part(model.rightLeg));
        fact.add("left_leg", part(model.leftLeg));
        if (!ModList.get().isLoaded("entity_model_features")) return fact;
        Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
        fact.addProperty("emf_customized", Boolean.TRUE.equals(api.getMethod("isModelCustomizedByEMF", EntityModel.class)
                .invoke(null, model)));
        Class<?> emfModel = Class.forName("traben.entity_model_features.models.IEMFModel");
        Object root = emfModel.getMethod("emf$getEMFRootModel").invoke(model);
        if (root == null) return fact;
        fact.addProperty("root_class", root.getClass().getName());
        fact.addProperty("model_name", String.valueOf(root.getClass().getField("modelName").get(root)));
        fact.addProperty("contains_custom_model", root.getClass().getField("containsCustomModel").getBoolean(root));
        fact.addProperty("contains_custom_animations", root.getClass().getField("containsCustomAnims").getBoolean(root));
        Object directory = root.getClass().getField("directoryContext").get(root);
        if (directory != null) fact.addProperty("selected_jem", String.valueOf(directory.getClass()
                .getMethod("getFinalFileLocation").invoke(directory)));
        JsonArray customIds = new JsonArray();
        JsonObject scaleParts = new JsonObject();
        JsonObject customTransforms = new JsonObject();
        Class<?> customPart = Class.forName("traben.entity_model_features.models.parts.EMFModelPartCustom");
        for (ModelPart child : ((ModelPart) root).getAllParts().toList()) {
            if (!customPart.isInstance(child)) continue;
            String id = String.valueOf(customPart.getField("id").get(child));
            customIds.add(id);
            String unprefixedId = id.replaceFirst("^EMF_", "");
            if (unprefixedId.contains("_scale")) scaleParts.add(id, part(child));
            if (unprefixedId.contains("_scale") || List.of("head", "headwear", "body", "left_arm", "right_arm", "left_leg", "right_leg")
                    .contains(unprefixedId)) customTransforms.add(id, part(child));
        }
        fact.add("custom_part_ids", customIds);
        fact.add("armor_scale_parts", scaleParts);
        fact.add("armor_custom_part_transforms", customTransforms);
        return fact;
    }

    private static JsonObject part(ModelPart part) {
        JsonObject fact = new JsonObject();
        fact.addProperty("class", part.getClass().getName());
        fact.addProperty("visible", part.visible);
        fact.addProperty("skip_draw", part.skipDraw);
        fact.addProperty("x", part.x);
        fact.addProperty("y", part.y);
        fact.addProperty("z", part.z);
        fact.addProperty("x_rotation", part.xRot);
        fact.addProperty("y_rotation", part.yRot);
        fact.addProperty("z_rotation", part.zRot);
        fact.addProperty("x_scale", part.xScale);
        fact.addProperty("y_scale", part.yScale);
        fact.addProperty("z_scale", part.zScale);
        try {
            Object texture = part.getClass().getField("textureOverride").get(part);
            fact.addProperty("texture_override", texture == null ? null : texture.toString());
        } catch (NoSuchFieldException ignored) {
            // Vanilla model parts inherit the armor render layer's texture.
        } catch (IllegalAccessException failure) { fact.addProperty("texture_override_read_error", failure.toString()); }
        return fact;
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.playerArmorAudit") || done) return;
        if (worldRequested) {
            frameWorld(Minecraft.getInstance());
            return;
        }
        if (reload != null || restoring || screen == null
                || screen.group == null || screen.frames < 24) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen != screen) return;
        try {
            for (Sample sample : screen.samples) {
                check(sample.draws >= 4, "Player renderer completed actual GPU draws for " + sample.modelName);
                sample.fact.addProperty("actual_living_renderer_events", sample.draws);
            }
            Path screenshot = output().resolve(screen.group.name + ".png");
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(image.getWidth() > 100 && image.getHeight() > 100, "Captured the actual GPU framebuffer");
                image.writeToFile(screenshot);
                screen.groupFact.addProperty("framebuffer_width", image.getWidth());
                screen.groupFact.addProperty("framebuffer_height", image.getHeight());
            }
            screen.groupFact.addProperty("screenshot", screenshot.toString());
            screen.groupFact.addProperty("rendered_frames", screen.frames);
            REPORT.getAsJsonArray("screenshots").add(screenshot.toString());
            saveReport();
            if (worldMode) {
                beginWorld(mc);
                return;
            }
            nextGroup(mc);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void beginWorld(Minecraft mc) {
        worldRequested = true;
        worldPosition = mc.player.position();
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        mc.options.hideGui = true;
        beginWorldPhase(mc, 0);
        mc.setScreen(null);
    }

    private static void beginWorldPhase(Minecraft mc, int phase) {
        worldPhase = phase;
        worldFrames = 0;
        worldStableTicks = 0;
        worldRenderEvents = 0;
        worldReady = false;
        worldFact = new JsonObject();
        worldFact.addProperty("name", phase == 0 ? "front" : "angled");
        worldFact.addProperty("camera", "THIRD_PERSON_FRONT");
        worldFact.addProperty("player_yaw_degrees", phase == 0 ? 0F : 25F);
        worldFact.addProperty("player_body_yaw_degrees", 0F);
        worldFact.addProperty("player_pitch_degrees", 10F);
        worldFact.addProperty("actual_local_player", true);
        REPORT.getAsJsonArray("world_scenes").add(worldFact);
        worldSetup = new CompletableFuture<>();
        UUID uuid = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(uuid);
                check(player != null, "The disposable world's real server player is available");
                var level = server.overworld();
                level.setDayTime(6000L);
                level.setWeatherParameters(6000, 0, false, false);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                player.setGameMode(GameType.CREATIVE);
                equipArmor(player);
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOWL));
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.setNoGravity(true);
                player.setDeltaMovement(Vec3.ZERO);
                player.teleportTo(level, worldPosition.x, worldPosition.y, worldPosition.z, phase == 0 ? 0F : 25F, 10F);
                player.inventoryMenu.broadcastChanges();
                worldSetup.complete(null);
            } catch (Throwable failure) { worldSetup.completeExceptionally(failure); }
        });
    }

    private static void tickWorld(Minecraft mc) {
        if (!worldSetup.isDone() || mc.level == null || mc.player == null || mc.screen != null) return;
        worldSetup.join();
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        mc.options.hideGui = true;
        mc.player.setYRot(worldPhase == 0 ? 0F : 25F);
        mc.player.yRotO = mc.player.getYRot();
        mc.player.yBodyRot = mc.player.yBodyRotO = 0F;
        mc.player.yHeadRot = mc.player.yHeadRotO = mc.player.getYRot();
        mc.player.setXRot(10F);
        mc.player.xRotO = 10F;
        mc.player.setDeltaMovement(Vec3.ZERO);
        boolean synchronizedPlayer = mc.player.position().distanceToSqr(worldPosition) < 1D
                && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(MiteArmorContent.get("mite:mithril_chestplate"))
                && mc.player.getMainHandItem().is(Items.BOWL);
        worldStableTicks = synchronizedPlayer ? worldStableTicks + 1 : 0;
        worldReady = worldStableTicks >= 30;
    }

    private static void frameWorld(Minecraft mc) {
        if (!worldReady || mc.getOverlay() != null || mc.screen != null || ++worldFrames < 80) return;
        try {
            check(worldRenderEvents > 0, "The actual local player completed real world renderer draws");
            Path screenshot = output().resolve(worldPhase == 0 ? "world-front.png" : "world-angled.png");
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(screenshot); }
            worldFact.addProperty("screenshot", screenshot.toString());
            worldFact.addProperty("actual_living_renderer_events", worldRenderEvents);
            worldFact.addProperty("stable_world_frames", worldFrames);
            worldFact.addProperty("skin_texture", mc.player.getSkin().texture().toString());
            worldFact.add("armor_equipment", equipment(mc.player));
            REPORT.getAsJsonArray("screenshots").add(screenshot.toString());
            saveReport();
            if (worldPhase == 0) beginWorldPhase(mc, 1);
            else {
                worldRequested = false;
                mc.options.setCameraType(initialCamera);
                mc.options.hideGui = initialHideGui;
                mc.getResourcePackRepository().setSelected(initialPacks);
                REPORT.addProperty("camera_and_hud_restored", true);
                REPORT.addProperty("initial_pack_selection_restored", true);
                REPORT.addProperty("status", "captured_real_gui_and_third_person_world_player_armor");
                finish(mc, null);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static final class AuditScreen extends Screen {
        private final List<Sample> samples = new ArrayList<>();
        private PackGroup group;
        private JsonObject groupFact;
        private int frames;

        private AuditScreen(Minecraft mc) throws Exception {
            super(Component.literal("Player armor pack comparison"));
            loadAuditSkin(mc);
            samples.add(sample(mc, "wide", "steve", PlayerSkin.Model.WIDE, 0));
            samples.add(sample(mc, "slim", "alex", PlayerSkin.Model.SLIM, 1));
            prepare(mc, mc.player);
            mc.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOWL));
            mc.player.setXRot(10F);
            mc.player.xRotO = 10F;
            samples.add(new Sample(mc.player, "local / bowl"));
        }

        private Sample sample(Minecraft mc, String modelName, String skinName, PlayerSkin.Model model, int index) {
            ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/entity/player/" + modelName + "/" + skinName + ".png");
            check(mc.getResourceManager().getResource(texture).isPresent(), "Built-in player skin is available: " + texture);
            PlayerSkin skin = new PlayerSkin(texture, null, null, null, model, true);
            if (model == PlayerSkin.Model.WIDE && auditSkin != null) skin = auditSkin;
            UUID uuid = UUID.nameUUIDFromBytes(("mite-player-armor-audit-" + modelName).getBytes(StandardCharsets.UTF_8));
            FixturePlayer player = new FixturePlayer(mc.level, new GameProfile(uuid, "NingMeng123"), skin);
            player.setId(-46000 - index);
            prepare(mc, player);
            return new Sample(player, modelName);
        }

        private void prepare(Minecraft mc, AbstractClientPlayer player) {
            player.setPos(mc.player.position());
            player.setOnGround(true);
            player.setNoGravity(true);
            player.setPose(Pose.STANDING);
            player.setMainArm(HumanoidArm.RIGHT);
            player.setYRot(0F);
            player.yRotO = 0F;
            player.yBodyRot = player.yBodyRotO = 0F;
            player.yHeadRot = player.yHeadRotO = 0F;
            player.setXRot(0F);
            player.xRotO = 0F;
            player.tickCount = 60;
            equipArmor(player);
        }

        private void beginGroup(Minecraft mc, PackGroup next) {
            group = next;
            frames = 0;
            groupFact = new JsonObject();
            groupFact.addProperty("name", group.name);
            groupFact.add("requested_packs", strings(group.packs));
            groupFact.add("selected_packs", strings(mc.getResourcePackRepository().getSelectedIds()));
            JsonArray active = new JsonArray();
            mc.getResourceManager().listPacks().forEach(pack -> active.add(pack.packId()));
            groupFact.add("active_resource_packs", active);
            JsonArray resources = new JsonArray();
            for (String path : List.of("emf/cem/player.jem", "emf/cem/player_slim.jem",
                    "optifine/cem/inner_armor.jem", "optifine/cem/outer_armor.jem")) {
                ResourceLocation id = ResourceLocation.withDefaultNamespace(path);
                JsonObject resource = new JsonObject();
                resource.addProperty("location", id.toString());
                var selected = mc.getResourceManager().getResource(id);
                resource.addProperty("present", selected.isPresent());
                selected.ifPresent(value -> resource.addProperty("source_pack", value.sourcePackId()));
                resources.add(resource);
            }
            groupFact.add("model_resources", resources);
            JsonArray facts = new JsonArray();
            for (Sample sample : samples) {
                prepare(mc, sample.player);
                if (sample.player == mc.player) {
                    sample.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOWL));
                    sample.player.setXRot(10F);
                    sample.player.xRotO = 10F;
                }
                sample.draws = 0;
                sample.fact = new JsonObject();
                sample.fact.addProperty("label", sample.modelName);
                sample.fact.addProperty("skin_model", sample.player.getSkin().model().name().toLowerCase(Locale.ROOT));
                sample.fact.addProperty("player_class", sample.player.getClass().getName());
                sample.fact.addProperty("is_actual_local_player", sample.player == mc.player);
                sample.fact.addProperty("skin_texture", sample.player.getSkin().texture().toString());
                sample.fact.addProperty("uuid", sample.player.getUUID().toString());
                sample.fact.addProperty("chest_item", BuiltInRegistries.ITEM.getKey(sample.player.getItemBySlot(EquipmentSlot.CHEST).getItem()).toString());
                sample.fact.add("armor_equipment", equipment(sample.player));
                sample.fact.addProperty("main_hand", BuiltInRegistries.ITEM.getKey(sample.player.getMainHandItem().getItem()).toString());
                sample.fact.addProperty("main_arm", "right");
                sample.fact.addProperty("head_pitch_degrees", sample.player.getXRot());
                sample.fact.addProperty("pose", "standing_idle");
                facts.add(sample.fact);
            }
            groupFact.add("samples", facts);
            REPORT.getAsJsonArray("groups").add(groupFact);
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try {
                graphics.fill(0, 0, width, height, 0xFF202630);
                graphics.drawString(font, group == null ? "Reloading player armor resources" : group.name,
                        8, 7, 0xFFE8EDF5, false);
                if (group == null || reload != null) return;
                int cellWidth = (width - 16) / 4;
                int cellHeight = (height - 28) / samples.size();
                for (int row = 0; row < samples.size(); row++) {
                    Sample sample = samples.get(row);
                    for (int view = 0; view < VIEW_NAMES.length; view++) {
                        int x = 8 + view * cellWidth;
                        int y = 23 + row * cellHeight;
                        graphics.fill(x, y, x + cellWidth - 4, y + cellHeight - 4, 0xFF414A58);
                        graphics.drawString(font, sample.modelName + " / " + VIEW_NAMES[view], x + 5, y + 5, 0xFFF2F4F7, false);
                        boolean fullSuit = Boolean.getBoolean("mite.playerArmorAuditFullSuit");
                        float scale = fullSuit ? Math.min(cellWidth * .70F, cellHeight * .48F)
                                : Math.min(cellWidth * .80F, cellHeight * .65F);
                        float feetY = fullSuit ? y + cellHeight - 8F : y + cellHeight + scale * .38F;
                        graphics.enableScissor(x + 2, y + 17, x + cellWidth - 6, y + cellHeight - 6);
                        rendering = sample;
                        try {
                            InventoryScreen.renderEntityInInventory(graphics, x + (cellWidth - 4) / 2F,
                                    feetY, scale, new Vector3f(),
                                    new Quaternionf().rotationZ((float) Math.PI).rotateY(VIEW_ANGLES[view]), null, sample.player);
                        } finally {
                            rendering = null;
                            graphics.disableScissor();
                        }
                    }
                }
                graphics.flush();
                frames++;
            } catch (Throwable failure) { finish(Minecraft.getInstance(), failure); }
        }

        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public boolean isPauseScreen() { return true; }
    }

    private static final class FixturePlayer extends RemotePlayer {
        private final PlayerSkin skin;

        private FixturePlayer(ClientLevel level, GameProfile profile, PlayerSkin skin) {
            super(level, profile);
            this.skin = skin;
            getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 127);
        }

        @Override public PlayerSkin getSkin() { return skin; }
    }

    private static void equipArmor(LivingEntity entity) {
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) entity.setItemSlot(slot, ItemStack.EMPTY);
        entity.setItemSlot(EquipmentSlot.CHEST, new ItemStack(MiteArmorContent.get("mite:mithril_chestplate")));
        if (Boolean.getBoolean("mite.playerArmorAuditFullSuit")) {
            entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MiteArmorContent.get("mite:mithril_helmet")));
            entity.setItemSlot(EquipmentSlot.LEGS, new ItemStack(MiteArmorContent.get("mite:mithril_leggings")));
            entity.setItemSlot(EquipmentSlot.FEET, new ItemStack(MiteArmorContent.get("mite:mithril_boots")));
        }
    }

    private static JsonObject equipment(LivingEntity entity) {
        JsonObject fact = new JsonObject();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
            fact.addProperty(slot.getName(), BuiltInRegistries.ITEM.getKey(entity.getItemBySlot(slot).getItem()).toString());
        return fact;
    }

    private static final class Sample {
        private final AbstractClientPlayer player;
        private final String modelName;
        private JsonObject fact;
        private int draws;

        private Sample(AbstractClientPlayer player, String modelName) {
            this.player = player;
            this.modelName = modelName;
        }
    }

    private record PackGroup(String name, List<String> packs) {}

    private static JsonArray strings(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static Path output() {
        return Path.of(System.getProperty("mite.playerArmorAuditOutput", "build/verification/player-armor"))
                .toAbsolutePath().normalize();
    }

    private static void saveReport() throws Exception {
        Files.createDirectories(output());
        Files.writeString(output().resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n",
                StandardCharsets.UTF_8);
    }

    private static void finish(Minecraft mc, Throwable failure) {
        if (done) return;
        done = true;
        if (!REPORT.has("failures")) REPORT.add("failures", new JsonArray());
        if (failure != null) {
            REPORT.addProperty("status", "failed");
            REPORT.getAsJsonArray("failures").add(failure.toString());
            Mite.LOGGER.error("Player armor client audit failed", failure);
        }
        try {
            if (initialCamera != null) {
                mc.options.setCameraType(initialCamera);
                mc.options.hideGui = initialHideGui;
            }
            if (overriddenPlayerInfo != null && initialSkinLookup != null) {
                Field lookup = PlayerInfo.class.getDeclaredField("skinLookup");
                lookup.setAccessible(true);
                lookup.set(overriddenPlayerInfo, initialSkinLookup);
                REPORT.addProperty("original_local_skin_restored", true);
            }
            if (auditSkin != null) mc.getTextureManager().release(auditSkin.texture());
        } catch (ReflectiveOperationException restoreFailure) {
            REPORT.getAsJsonArray("failures").add("Restoring local player skin: " + restoreFailure);
            Mite.LOGGER.error("Cannot restore player armor audit skin", restoreFailure);
        }
        REPORT.addProperty("safe_client_shutdown_requested", true);
        try { saveReport(); }
        catch (Exception writeFailure) { Mite.LOGGER.error("Cannot save player armor client audit", writeFailure); }
        mc.stop();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
