package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.Livestock032View;
import io.github.ningmeng123.mite.entity.animal.LivestockHealth033;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
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

/** Real GPU atlas samples in a disposable world; screenshots require visual review. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class EntityAtlasClientAudit {
    private static final JsonObject REPORT = new JsonObject();
    private static boolean baked, started, done;
    private static int waiting, renderedEvents;
    private static AuditScreen screen;
    private static Row rendering;
    private static CompletableFuture<WorldScene> worldSceneFuture;
    private static WorldScene worldScene;
    private static final Set<UUID> WORLD_RENDERED = new java.util.HashSet<>();
    private static boolean worldSceneRequested, worldSceneReady;
    private static int worldWaitTicks, worldStableTicks, worldFrames, worldRenderEvents;

    private EntityAtlasClientAudit() {}

    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted ignored) {
            if (Boolean.getBoolean("mite.entityAtlasAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.entityAtlasAudit") || !baked || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                check(directory.toString().replace('\\', '/').contains("/build/verification/"),
                        "Entity atlas audit requires an isolated directory under build/verification");
                started = true;
                REPORT.addProperty("author", "NingMeng123");
                REPORT.addProperty("game_directory", directory.toString());
                REPORT.addProperty("existing_worlds_opened", false);
                REPORT.addProperty("client_only_entities_added_to_world", false);
                REPORT.addProperty("visual_verdict", "requires_manual_screenshot_review");
                REPORT.addProperty("entity_model_features_loaded", ModList.get().isLoaded("entity_model_features"));
                REPORT.addProperty("entity_texture_features_loaded", ModList.get().isLoaded("entity_texture_features"));
                REPORT.addProperty("acceleratedrendering_loaded", ModList.get().isLoaded("acceleratedrendering"));
                REPORT.add("failures", new JsonArray());
                REPORT.add("entities", new JsonArray());
                REPORT.add("screenshots", new JsonArray());
                JsonArray packs = new JsonArray();
                mc.getResourceManager().listPacks().forEach(pack -> packs.add(pack.packId()));
                REPORT.add("active_packs", packs);
                mc.options.pauseOnLostFocus = false;
                String world = "entity-atlas-audit-" + System.currentTimeMillis();
                REPORT.addProperty("disposable_world", world);
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261026L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++waiting < 3600, "Timed out loading or drawing the entity atlas audit");
            if (worldSceneRequested) {
                tickWorldScene(mc);
                return;
            }
            if (screen == null && mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                screen = new AuditScreen(mc);
                mc.setScreen(screen);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent public static void rendered(RenderLivingEvent.Post<?, ?> event) {
        Row row = rendering;
        if (row == null && worldScene != null && worldScene.uuids.contains(event.getEntity().getUUID())) {
            WORLD_RENDERED.add(event.getEntity().getUUID());
            worldRenderEvents++;
        }
        if (row == null || event.getEntity() != row.entity) return;
        renderedEvents++;
        row.draws++;
        if (row.fact.has("renderer_class")) return;
        try {
            LivingEntityRenderer renderer = renderer(row.entity);
            EntityModel<?> model = renderer.getModel();
            row.fact.addProperty("renderer_class", renderer.getClass().getName());
            row.fact.addProperty("model_class", model.getClass().getName());
            row.fact.addProperty("emf_customized", customized(model));
            ResourceLocation body = texture(row.entity);
            row.fact.add("body_texture", textureFact(body));
            JsonArray overrides = new JsonArray();
            Set<ModelPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (field.getType() != ModelPart.class) continue;
                    field.setAccessible(true);
                    ModelPart part = (ModelPart) field.get(model);
                    if (part == null) continue;
                    part.getAllParts().forEach(child -> {
                        if (!visited.add(child)) return;
                        try {
                            Object override = child.getClass().getField("textureOverride").get(child);
                            if (override instanceof ResourceLocation id) overrides.add(textureFact(id));
                        } catch (NoSuchFieldException ignored) {
                            // Ordinary vanilla model parts have no independent texture binding.
                        } catch (Exception failure) { throw new IllegalStateException(failure); }
                    });
                }
            }
            row.fact.add("part_texture_overrides", overrides);
        } catch (Throwable failure) { finish(Minecraft.getInstance(), failure); }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post ignored) {
        Minecraft mc = Minecraft.getInstance();
        if (done || screen == null) return;
        if (worldSceneRequested) {
            if (!worldSceneReady || mc.getOverlay() != null || mc.screen != null || ++worldFrames < 60) return;
            try {
                check(WORLD_RENDERED.containsAll(worldScene.uuids),
                        "All three server-spawned samples completed real world renderer draws");
                Path screenshot = output().resolve("world.png");
                try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(screenshot);
                }
                REPORT.getAsJsonArray("screenshots").add(screenshot.toString());
                REPORT.addProperty("actual_world_renderer_events", worldRenderEvents);
                REPORT.addProperty("distinct_world_entities_rendered", WORLD_RENDERED.size());
                REPORT.addProperty("world_scene_client_sync_confirmed", true);
                REPORT.addProperty("status", "captured_real_gui_and_world_entity_atlas_samples");
                finish(mc, null);
            } catch (Throwable failure) { finish(mc, failure); }
            return;
        }
        if (mc.screen != screen || screen.frames < 5) return;
        try {
            Files.createDirectories(output());
            Path screenshot = output().resolve("page-" + screen.page + ".png");
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                check(image.getWidth() > 100 && image.getHeight() > 100, "A real GPU framebuffer was captured");
                image.writeToFile(screenshot);
                REPORT.addProperty("framebuffer_width", image.getWidth());
                REPORT.addProperty("framebuffer_height", image.getHeight());
            }
            REPORT.getAsJsonArray("screenshots").add(screenshot.toString());
            if (++screen.page < 3) { screen.frames = 0; return; }
            for (Row row : screen.rows) {
                check(row.draws > 0, "LivingEntityRenderer completed a draw for " + row.label);
                row.fact.addProperty("actual_living_renderer_events", row.draws);
            }
            REPORT.addProperty("actual_living_renderer_events", renderedEvents);
            REPORT.addProperty("status", "captured_real_entity_atlas_samples");
            if (Boolean.getBoolean("mite.entityAtlasWorldScene")) {
                beginWorldScene(mc);
                return;
            }
            finish(mc, null);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void beginWorldScene(Minecraft mc) {
        check(mc.getSingleplayerServer() != null && mc.player != null, "A disposable server is available for the world scene");
        worldSceneRequested = true;
        worldSceneFuture = new CompletableFuture<>();
        REPORT.addProperty("world_scene_requested", true);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = true;
        UUID playerId = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                check(player != null, "The disposable server player is present");
                ServerLevel level = server.overworld();
                level.setDayTime(6000L);
                level.setWeatherParameters(6000, 0, false, false);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                for (int x = -12; x <= 12; x++) for (int z = -8; z <= 12; z++)
                    level.setBlock(new BlockPos(x, 70, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                var pillager = EntityType.PILLAGER.create(level);
                check(pillager != null, "Server pillager constructor succeeds");
                pillager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                prepareWorldMob(pillager, 4, -2.2, 0F);
                Sheep white = EntityType.SHEEP.create(level), red = EntityType.SHEEP.create(level);
                check(white != null && red != null, "Server sheep constructors succeed");
                white.setColor(DyeColor.WHITE); red.setColor(DyeColor.RED);
                for (Sheep sheep : List.of(white, red)) {
                    sheep.setSheared(false);
                    Livestock032.needs(sheep, new LivestockRules032.Needs(1, 1, 1));
                    Livestock032View view = (Livestock032View) sheep;
                    view.mite$setRecoveryTicks(0);
                }
                prepareWorldMob(white, 10, 0, 95F);
                prepareWorldMob(red, 11, 2.2, -85F);
                List<Mob> samples = List.of(pillager, white, red);
                for (Mob sample : samples) check(level.addFreshEntity(sample),
                        "Server fixture rejected: type=" + EntityType.getKey(sample.getType()) + " id=" + sample.getId()
                                + " uuid=" + sample.getUUID() + " spawn_type=" + sample.getSpawnType()
                                + " removed=" + sample.isRemoved() + " spawn_cancelled=" + sample.isSpawnCancelled());
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, 0, 71.5, 6.5, 180F, 12F);
                player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                worldSceneFuture.complete(new WorldScene(samples.stream().map(Mob::getId).toList(),
                        samples.stream().map(Mob::getUUID).toList()));
            } catch (Throwable failure) { worldSceneFuture.completeExceptionally(failure); }
        });
        // Unpause the integrated server so entity tracking, chunk loading and
        // ordinary network synchronization can complete before the screenshot.
        mc.setScreen(null);
    }

    private static void prepareWorldMob(Mob mob, int fixtureIndex, double x, float yaw) {
        mob.setUUID(UUID.nameUUIDFromBytes(("mite-entity-atlas-" + fixtureIndex).getBytes(StandardCharsets.UTF_8)));
        ((MobSpawnTypeAccessor) mob).mite$setSpawnType(MobSpawnType.COMMAND);
        // Match the extension's /summon metadata. A command reason alone is not
        // its explicit-spawn marker, so an unmarked fixture would enter ordinary
        // automatic-spawn admission and grace-period gates on this new world.
        mob.getPersistentData().putBoolean("modern_ite:explicit_command_spawn", true);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.setInvulnerable(true);
        mob.moveTo(x, 71, 0, yaw, 0F);
        mob.yBodyRot = mob.yBodyRotO = yaw;
        mob.yHeadRot = mob.yHeadRotO = yaw;
    }

    private static void tickWorldScene(Minecraft mc) {
        check(++worldWaitTicks < 1200, "Timed out synchronizing the disposable world scene");
        if (!worldSceneFuture.isDone() || mc.level == null || mc.player == null) return;
        if (worldScene == null) {
            worldScene = worldSceneFuture.join();
            REPORT.addProperty("real_server_entities_added", worldScene.ids.size());
            JsonArray fixtures = new JsonArray();
            for (int i = 0; i < worldScene.ids.size(); i++) {
                JsonObject fixture = new JsonObject();
                fixture.addProperty("entity_id", worldScene.ids.get(i));
                fixture.addProperty("uuid", worldScene.uuids.get(i).toString());
                fixture.addProperty("spawn_type", "COMMAND");
                fixture.addProperty("explicit_command_spawn", true);
                fixture.addProperty("role", List.of("pillager_helmet_uuid4", "healthy_white_sheep", "healthy_red_sheep").get(i));
                fixtures.add(fixture);
            }
            REPORT.add("server_world_scene_entities", fixtures);
        }
        boolean synchronizedScene = worldScene.ids.stream().allMatch(id -> mc.level.getEntity(id) != null)
                && mc.player.distanceToSqr(0, 71.5, 6.5) < 1D;
        if (synchronizedScene) worldStableTicks++;
        else worldStableTicks = 0;
        worldSceneReady = worldStableTicks >= 30;
    }

    private record WorldScene(List<Integer> ids, List<UUID> uuids) {}

    private static final class AuditScreen extends Screen {
        private final List<Row> rows = new ArrayList<>();
        private int frames, page;

        AuditScreen(Minecraft mc) {
            super(Component.literal("Pillager and sheep texture atlas audit"));
            for (int i = 0; i < 6; i++) {
                LivingEntity pillager = EntityType.PILLAGER.create(mc.level);
                check(pillager != null, "Pillager constructor succeeds");
                pillager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                add(mc, pillager, "pillager UUID " + i, i);
            }
            addSheep(mc, "healthy white", DyeColor.WHITE, false, false, 10);
            addSheep(mc, "healthy red", DyeColor.RED, false, false, 11);
            addSheep(mc, "healthy sheared", DyeColor.WHITE, true, false, 12);
            addSheep(mc, "sick white", DyeColor.WHITE, false, true, 13);
            addSheep(mc, "sick red", DyeColor.RED, false, true, 14);
            addSheep(mc, "sick sheared", DyeColor.WHITE, true, true, 15);
        }

        private void addSheep(Minecraft mc, String label, DyeColor color, boolean sheared, boolean sick, int index) {
            Sheep sheep = EntityType.SHEEP.create(mc.level);
            check(sheep != null, "Sheep constructor succeeds");
            sheep.setColor(color);
            sheep.setSheared(sheared);
            Livestock032View view = (Livestock032View) sheep;
            view.mite$setLivestockNeeds(new LivestockRules032.Needs(sick ? .1F : 1F, 1F, 1F));
            view.mite$setRecoveryTicks(0);
            check((LivestockHealth033.sickBodyTexture(sheep) != null) == sick, "Sheep health fixture matches " + label);
            Row row = add(mc, sheep, label, index);
            row.fact.addProperty("sick", sick);
            row.fact.addProperty("sheared", sheared);
            row.fact.addProperty("wool_color", color.getName());
        }

        private Row add(Minecraft mc, LivingEntity entity, String label, int index) {
            entity.setUUID(UUID.nameUUIDFromBytes(("mite-entity-atlas-" + index).getBytes(StandardCharsets.UTF_8)));
            entity.setId(40000 + index);
            entity.setPos(mc.player.position());
            entity.setYRot(0); entity.yRotO = 0; entity.yBodyRot = entity.yBodyRotO = 0;
            entity.yHeadRot = entity.yHeadRotO = 0;
            entity.setOnGround(true);
            entity.tickCount = 60;
            JsonObject fact = new JsonObject();
            fact.addProperty("label", label);
            fact.addProperty("entity", EntityType.getKey(entity.getType()).toString());
            fact.addProperty("uuid", entity.getUUID().toString());
            REPORT.getAsJsonArray("entities").add(fact);
            Row row = new Row(entity, label, fact);
            rows.add(row);
            return row;
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try {
                graphics.fill(0, 0, width, height, 0xFF17202B);
                String title = page == 0 ? "Pillagers: fixed UUIDs / independent helmet textures"
                        : page == 1 ? "Sheep: healthy and sick / front" : "Sheep: healthy and sick / rear";
                graphics.drawCenteredString(font, title, width / 2, 8, 0xFFF0F3FA);
                int cellWidth = (width - 16) / 3, cellHeight = (height - 34) / 2;
                int start = page == 0 ? 0 : 6;
                for (int i = 0; i < 6; i++) {
                    Row row = rows.get(start + i);
                    int x = 8 + i % 3 * cellWidth, y = 27 + i / 3 * cellHeight;
                    graphics.fill(x, y, x + cellWidth - 4, y + cellHeight - 4, 0xFF2D3948);
                    graphics.drawCenteredString(font, row.label, x + cellWidth / 2, y + 5, 0xFFE5E9F4);
                    float scale = Math.min(cellWidth * .42F, cellHeight * (page == 0 ? .40F : .55F));
                    rendering = row;
                    try {
                        InventoryScreen.renderEntityInInventory(graphics, x + cellWidth / 2F,
                                y + cellHeight - 16F, scale, new Vector3f(),
                                new Quaternionf().rotationZ((float) Math.PI).rotateX(.15F)
                                        .rotateY((page == 2 ? 0F : (float) Math.PI) + .55F), null, row.entity);
                    } finally { rendering = null; }
                }
                graphics.flush();
                frames++;
            } catch (Throwable failure) { finish(Minecraft.getInstance(), failure); }
        }

        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public boolean isPauseScreen() { return true; }
    }

    private static JsonObject textureFact(ResourceLocation id) {
        JsonObject fact = new JsonObject();
        fact.addProperty("location", id.toString());
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        fact.addProperty("resource_exists", resource.isPresent());
        if (resource.isPresent()) {
            fact.addProperty("source_pack", resource.get().sourcePackId());
            try (var stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                fact.addProperty("width", image.getWidth());
                fact.addProperty("height", image.getHeight());
            } catch (Exception failure) { fact.addProperty("read_error", failure.toString()); }
        }
        return fact;
    }

    private static boolean customized(EntityModel<?> model) throws Exception {
        if (!ModList.get().isLoaded("entity_model_features")) return false;
        Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
        return Boolean.TRUE.equals(api.getMethod("isModelCustomizedByEMF", EntityModel.class).invoke(null, model));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LivingEntityRenderer renderer(LivingEntity entity) {
        return (LivingEntityRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    }

    @SuppressWarnings("unchecked") private static ResourceLocation texture(LivingEntity entity) {
        return renderer(entity).getTextureLocation(entity);
    }

    private static Path output() {
        return Path.of(System.getProperty("mite.entityAtlasAuditOutput", "build/verification/entity-atlas"))
                .toAbsolutePath().normalize();
    }

    private static void finish(Minecraft mc, Throwable failure) {
        if (done) return;
        done = true;
        if (!REPORT.has("failures")) REPORT.add("failures", new JsonArray());
        if (failure != null) {
            REPORT.addProperty("status", "failed");
            REPORT.getAsJsonArray("failures").add(failure.toString());
            Mite.LOGGER.error("Entity atlas client audit failed", failure);
        }
        REPORT.addProperty("safe_client_shutdown_requested", true);
        try {
            Files.createDirectories(output());
            Files.writeString(output().resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n",
                    StandardCharsets.UTF_8);
        } catch (Exception writeFailure) { Mite.LOGGER.error("Cannot save entity atlas audit", writeFailure); }
        mc.stop();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Row {
        private final LivingEntity entity;
        private final String label;
        private final JsonObject fact;
        private int draws;
        private Row(LivingEntity entity, String label, JsonObject fact) {
            this.entity = entity;
            this.label = label;
            this.fact = fact;
        }
    }
}
