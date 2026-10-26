package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.phantom.PhantomBladeProjectileRenderer;
import io.github.ningmeng123.mite.phantom.PhantomBladeProjectile;
import io.github.ningmeng123.mite.phantom.PhantomBladeSystem;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

/** Opt-in screenshots from a disposable, genuinely synchronized world; pixels require human review. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class PhantomBladeVisualSmoke {
    private enum Phase { TITLE, LOADING, SETUP, PARKED, TARGET, FLIGHT, RETURNING, DOCKED, DONE }
    private static final Vec3 HOME = new Vec3(.5D, 128D, .5D);
    private static final float YAW = -25F, PITCH = 16F;
    private static final JsonObject REPORT = new JsonObject();
    private static Phase phase = Phase.TITLE;
    private static CompletableFuture<Integer> pending;
    private static UUID ownerUuid;
    private static int targetId = -1, bladeId = -1, restingFrames, worldFrames, clientTicks;
    private static boolean isolated;

    private PhantomBladeVisualSmoke() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.phantomBladeVisualSmoke") || phase == Phase.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (phase == Phase.TITLE) {
                if (mc.getOverlay() != null) return;
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                isolated = directory.toString().replace('\\', '/').contains("/build/verification/");
                check(isolated, "Visual smoke requires its own game directory under build/verification; no existing game is opened");
                REPORT.addProperty("author", "NingMeng123");
                REPORT.addProperty("game_directory", directory.toString());
                REPORT.addProperty("existing_worlds_opened", false);
                REPORT.addProperty("visual_verdict", "not_automatically_assessed");
                REPORT.addProperty("manual_pixel_review_required", true);
                String world = "phantom-single-blade-"+System.currentTimeMillis();
                REPORT.addProperty("disposable_world", world);
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.options.fov().set(60);
                phase = Phase.LOADING;
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.HARD, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261024L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++clientTicks < 3600, "Timed out loading or rendering the isolated visual scene");
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
            mc.options.hideGui = true;
            mc.getToasts().clear();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.setYRot(YAW); mc.player.setXRot(PITCH);
            mc.player.yRotO = YAW; mc.player.xRotO = PITCH;
            mc.player.yHeadRot = YAW; mc.player.yHeadRotO = YAW;
            if (phase == Phase.LOADING) {
                if (mc.screen != null) return;
                ownerUuid = mc.player.getUUID();
                var server = mc.getSingleplayerServer();
                pending = CompletableFuture.supplyAsync(() -> {
                    ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
                    check(owner != null, "The integrated server owns a real online player");
                    var level = owner.serverLevel();
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                    level.setDayTime(6000L); level.setWeatherParameters(0, 0, false, false);
                    for (BlockPos pos : BlockPos.betweenClosed(-12, 127, -12, 12, 127, 12))
                        level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    owner.setGameMode(GameType.CREATIVE); owner.setNoGravity(true); owner.setInvulnerable(true);
                    owner.teleportTo(level, HOME.x, HOME.y, HOME.z, YAW, PITCH);
                    owner.getInventory().clearContent();
                    owner.getInventory().items.set(9, new ItemStack(MitePhantomBlades.get("copper")));
                    owner.getInventory().setChanged(); owner.containerMenu.broadcastChanges();
                    PhantomBladeSystem.refresh(owner);
                    check(PhantomBladeSystem.swarm(owner) != null, "The real carried copper core creates its normal synchronized formation");
                    return owner.getId();
                }, server);
                phase = Phase.SETUP;
            } else if (phase == Phase.SETUP && pending.isDone()) {
                int id = pending.join();
                if (mc.player.getId() != id || mc.player.position().distanceToSqr(HOME) > .1D || mc.screen != null) return;
                REPORT.addProperty("integrated_server_online_owner_id", id);
                REPORT.addProperty("world_created", true);
                REPORT.addProperty("camera", "third_person_back");
                REPORT.addProperty("camera_yaw", YAW); REPORT.addProperty("camera_pitch", PITCH);
                phase = Phase.PARKED;
            } else if (phase == Phase.TARGET && pending.isDone()) {
                targetId = pending.join();
                if (mc.level.getEntity(targetId) != null) phase = Phase.FLIGHT;
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent public static void worldRendered(RenderLevelStageEvent event) {
        if (!Boolean.getBoolean("mite.phantomBladeVisualSmoke") || phase == Phase.DONE
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        ++worldFrames;
        if (phase != Phase.PARKED) return;
        for (Entity entity : mc.level.entitiesForRendering()) if (entity instanceof PhantomBladeProjectile blade
                && blade.ownerId() == mc.player.getId() && blade.isDocked()) {
            Vec3 camera = event.getCamera().getPosition();
            if (mc.getEntityRenderDispatcher().shouldRender(blade, event.getFrustum(), camera.x, camera.y, camera.z)) ++restingFrames;
        }
    }

    @SubscribeEvent public static void frameRendered(RenderFrameEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.phantomBladeVisualSmoke") || phase == Phase.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || mc.getOverlay() != null) return;
        try {
            if (phase == Phase.PARKED && restingFrames >= 40) {
                PhantomBladeProjectile resting = null; int blades = 0;
                for (Entity entity : mc.level.entitiesForRendering()) if (entity instanceof PhantomBladeProjectile candidate
                        && candidate.ownerId() == mc.player.getId()) { resting = candidate; ++blades; }
                if (resting == null || !resting.isDocked()) return;
                check(blades == 1, "The normal carried core renders precisely one persistent sword");
                bladeId = resting.getId();
                JsonObject parked = state(resting);
                parked.addProperty("swords", blades); parked.addProperty("docked", resting.isDocked());
                parked.addProperty("frustum_eligible_world_frames", restingFrames);
                capture(mc, "resting.png", parked); REPORT.add("resting", parked);
                var server = mc.getSingleplayerServer();
                pending = CompletableFuture.supplyAsync(() -> {
                    var owner = server.getPlayerList().getPlayer(ownerUuid);
                    check(owner != null, "The actual owner remains online before target insertion");
                    var level = owner.serverLevel();
                    var zombie = EntityType.ZOMBIE.create(level);
                    check(zombie != null, "A native zombie target can be created");
                    zombie.setNoAi(true); zombie.setNoGravity(true); zombie.setBaby(false);
                    zombie.getPersistentData().putBoolean("modern_ite:explicit_command_spawn", true);
                    zombie.moveTo(HOME.add(6D, 0D, 4D));
                    check(level.addFreshEntity(zombie) && zombie.isAddedToLevel(), "The scene target really enters the integrated server level");
                    for (EquipmentSlot slot : EquipmentSlot.values()) zombie.setItemSlot(slot, ItemStack.EMPTY);
                    zombie.getAttribute(Attributes.ARMOR).setBaseValue(0D);
                    zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
                    zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1D);
                    zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0D);
                    zombie.setHealth(zombie.getMaxHealth());
                    for (BlockPos pos : BlockPos.betweenClosed(5, 132, 3, 7, 132, 5))
                        level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    return zombie.getId();
                }, server);
                phase = Phase.TARGET;
            } else if (phase == Phase.FLIGHT) {
                for (Entity entity : mc.level.entitiesForRendering()) if (entity instanceof PhantomBladeProjectile blade
                        && blade.ownerId() == mc.player.getId() && !blade.isDocked() && !blade.isReturning()) {
                    check(blade.getId() == bladeId, "The parked entity itself flies out instead of spawning a replacement sword");
                    var renderer = mc.getEntityRenderDispatcher().getRenderer(blade);
                    if (!(renderer instanceof PhantomBladeProjectileRenderer)) continue;
                    Field field = PhantomBladeProjectileRenderer.class.getDeclaredField("trails"); field.setAccessible(true);
                    var trail = ((Map<?, ?>)field.get(renderer)).get(blade);
                    if (!(trail instanceof PhantomBladeProjectileRenderer.Trail history) || history.points().size() < 3) continue;
                    // The production renderer alone populates this cache: three positions prove real render calls over moving ticks.
                    JsonObject flight = state(blade);
                    flight.addProperty("actual_renderer_class", renderer.getClass().getName());
                    flight.addProperty("actual_renderer_trail_samples", history.points().size());
                    flight.addProperty("material", blade.material()); flight.addProperty("blade_index", blade.bladeIndex());
                    flight.addProperty("owner_id", blade.ownerId()); flight.addProperty("target_id", blade.targetId());
                    flight.add("velocity", vector(blade.getDeltaMovement()));
                    Entity target = mc.level.getEntity(targetId);
                    check(target != null, "The screenshot's actual target has synchronized to the client");
                    flight.add("target_position", vector(target.getBoundingBox().getCenter()));
                    if (target instanceof LivingEntity living) {
                        flight.addProperty("target_health", living.getHealth());
                        flight.addProperty("target_armor", living.getArmorValue());
                    }
                    JsonArray points = new JsonArray();
                    history.points().forEach(point -> points.add(vector(point.position()))); flight.add("rendered_trajectory", points);
                    capture(mc, "flight.png", flight); REPORT.add("flight", flight);
                    phase = Phase.RETURNING; return;
                }
            } else if (phase == Phase.RETURNING) {
                Entity entity = mc.level.getEntity(bladeId);
                check(entity instanceof PhantomBladeProjectile, "The same sword remains synchronized after impact");
                var blade = (PhantomBladeProjectile)entity;
                if (!blade.isReturning()) return;
                JsonObject returning = state(blade); returning.addProperty("returning", blade.isReturning());
                returning.add("velocity", vector(blade.getDeltaMovement()));
                capture(mc, "return.png", returning); REPORT.add("returning", returning);
                phase = Phase.DOCKED;
            } else if (phase == Phase.DOCKED) {
                Entity entity = mc.level.getEntity(bladeId);
                check(entity instanceof PhantomBladeProjectile, "The same sword survives its entire return flight");
                var blade = (PhantomBladeProjectile)entity;
                if (!blade.isDocked()) return;
                JsonObject cooldown = state(blade); cooldown.addProperty("docked", blade.isDocked());
                cooldown.addProperty("same_entity_through_full_cycle", true);
                capture(mc, "cooldown.png", cooldown); REPORT.add("cooldown", cooldown);
                finish(mc, null); return;
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static JsonObject state(Entity entity) {
        JsonObject state = new JsonObject(); state.addProperty("entity_id", entity.getId());
        state.addProperty("entity_tick", entity.tickCount); state.addProperty("removed", entity.isRemoved());
        state.addProperty("added_to_level", entity.isAddedToLevel());
        state.addProperty("dimension", entity.level().dimension().location().toString());
        state.add("position", vector(entity.position())); state.addProperty("world_frames", worldFrames); return state;
    }
    private static JsonArray vector(Vec3 vector) { JsonArray json = new JsonArray(); json.add(vector.x); json.add(vector.y); json.add(vector.z); return json; }
    private static void capture(Minecraft mc, String name, JsonObject fact) throws Exception {
        Path directory = output(); Files.createDirectories(directory);
        try (NativeImage pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels.writeToFile(directory.resolve(name));
            fact.addProperty("width", pixels.getWidth()); fact.addProperty("height", pixels.getHeight());
        }
        fact.addProperty("screenshot", directory.resolve(name).toString());
        REPORT.addProperty("opengl_version", GL11.glGetString(GL11.GL_VERSION));
        REPORT.addProperty("opengl_renderer", GL11.glGetString(GL11.GL_RENDERER));
        REPORT.addProperty("native_framebuffer_captured", true);
    }
    private static Path output() {
        return Path.of(System.getProperty("mite.phantomBladeVisualSmokeOutput", "build/verification/phantom-single-blade-visual"))
                .toAbsolutePath().normalize();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void finish(Minecraft mc, Throwable failure) {
        phase = Phase.DONE;
        REPORT.addProperty("status", failure == null ? "captured_real_world_frames_pending_visual_review" : "failed");
        if (failure != null) { REPORT.addProperty("failure", failure.toString()); Mite.LOGGER.error("Phantom blade visual smoke failed", failure); }
        REPORT.addProperty("safe_client_shutdown_requested", isolated);
        try {
            Files.createDirectories(output());
            Files.writeString(output().resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)+"\n", StandardCharsets.UTF_8);
        } catch (Exception writeFailure) { Mite.LOGGER.error("Cannot write phantom blade visual smoke report", writeFailure); }
        if (isolated) mc.stop();
    }
}
