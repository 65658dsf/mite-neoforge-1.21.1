package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.BetterCombatMobAnimations;
import io.github.ningmeng123.mite.client.mob.LegacyStalkerModel;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.gametest.BetterCombatMobSettings;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
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

/** Real rendering verifies visible source cubes against active FA geometry, without invoking its driver. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class ZombieVariantClientAudit {
    private static final List<MobKind> KINDS = List.of(MobKind.CLAY_GOLEM, MobKind.INVISIBLE_STALKER,
            MobKind.SHADOW, MobKind.WIGHT, MobKind.EARTH_ELEMENTAL);
    private static final JsonObject REPORT = new JsonObject();
    private static boolean baked, started, done, emf, combat;
    private static int waiting;
    private static AuditScreen screen;
    private static Sample current;

    private ZombieVariantClientAudit() {}

    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void baked(ModelEvent.BakingCompleted ignored) {
            if (Boolean.getBoolean("mite.zombieVariantAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.zombieVariantAudit") || !baked || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                check(directory.toString().replace('\\', '/').contains("/build/verification/"), "An isolated verification directory is required");
                started = true;
                emf = ModList.get().isLoaded("entity_model_features");
                combat = ModList.get().isLoaded("bettercombat") && ModList.get().isLoaded("playeranimator");
                REPORT.addProperty("author", "NingMeng123");
                REPORT.addProperty("entity_model_features_loaded", emf);
                REPORT.addProperty("better_combat_loaded", combat);
                REPORT.addProperty("game_directory", directory.toString());
                REPORT.addProperty("existing_worlds_opened", false);
                REPORT.addProperty("visual_verdict", "requires_manual_screenshot_review");
                REPORT.add("failures", new JsonArray()); REPORT.add("entities", new JsonArray());
                if (emf) {
                    var id = ResourceLocation.withDefaultNamespace("optifine/cem/zombie.jem");
                    var resource = mc.getResourceManager().getResource(id).orElseThrow(() -> new IllegalStateException("Enabled FA zombie.jem is required"));
                    REPORT.addProperty("zombie_jem_source", resource.sourcePackId());
                }
                mc.options.pauseOnLostFocus = false;
                String world = "zombie-variants-audit-" + System.currentTimeMillis();
                REPORT.addProperty("disposable_world", world);
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261026L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++waiting < 3600, "Timed out loading or rendering zombie variants");
            if (screen == null && mc.player != null && mc.level != null && mc.getSingleplayerServer() != null) {
                screen = new AuditScreen(mc); mc.setScreen(screen);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent public static void rendered(RenderLivingEvent.Post<?, ?> event) {
        if (current == null || current.entity != event.getEntity()) return;
        try {
            check(event.getRenderer().getModel() instanceof HumanoidModel<?>, "The real renderer uses a humanoid model");
            HumanoidModel<?> model = (HumanoidModel<?>)event.getRenderer().getModel();
            current.pose = pose(model);
            if (model instanceof LegacyStalkerModel<?> legacy) {
                current.faApplied = legacy.freshAnimationsApplied();
                current.headMapped = legacy.freshHeadMapped();
                if (emf && !BetterCombatMobAnimations.active(current.entity)) {
                    check(current.faApplied && current.headMapped, "The real renderer sampled FA and mapped its actual head: " + current.entity.getType());
                    current.mapped = compareMappedGeometry(legacy, current);
                } else if (!emf) check(!current.faApplied, "No-EMF rendering uses the source animation fallback");
            }
        } catch (Throwable failure) { current.failure = failure; }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post ignored) {
        Minecraft mc = Minecraft.getInstance();
        if (done || screen == null || mc.screen != screen || screen.frames < 3) return;
        try {
            Path path = output().resolveSibling(emf ? "zombie-variants-fa-render.png" : "zombie-variants-fallback-render.png");
            Files.createDirectories(path.getParent());
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
            REPORT.addProperty("screenshot", path.toString());
            REPORT.addProperty("status", emf ? "passed_real_fa_zombie_variant_mapping" : "passed_source_zombie_variant_fallback");
            finish(mc, null);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static final class AuditScreen extends Screen {
        final List<Row> rows = new ArrayList<>();
        int frames;

        AuditScreen(Minecraft mc) throws Exception {
            super(Component.literal("Zombie variants: source skin and Fresh Animations"));
            add(mc, EntityType.ZOMBIE, null);
            HumanoidModel<Mob> reference = new HumanoidModel<>(LayerDefinition.create(
                    HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 32).bakeRoot());
            for (MobKind kind : KINDS) {
                Row row = add(mc, MiteMobs.type(kind), kind);
                var renderer = renderer(row.entity);
                check(renderer.getModel() instanceof LegacyStalkerModel<?>, "Variant retains its source geometry model: " + kind);
                HumanoidModel<?> model = (HumanoidModel<?>)renderer.getModel();
                for (String name : parts(model).keySet()) {
                    check(geometry(parts(model).get(name)).equals(geometry(parts(reference).get(name))),
                            "Source cube bounds and 64x32 UVs are unchanged: " + kind + "/" + name);
                }
                ResourceLocation texture = texture(row.entity);
                try (var stream = mc.getResourceManager().open(texture); NativeImage image = NativeImage.read(stream)) {
                    check(image.getWidth() == 64 && image.getHeight() == 32, "Original species atlas remains 64x32: " + kind);
                    row.fact.addProperty("skin_width", image.getWidth()); row.fact.addProperty("skin_height", image.getHeight());
                }
                float expectedOpacity = kind == MobKind.INVISIBLE_STALKER ? .05F : 1F;
                check(Math.abs(((Number)field(model, "opacity")).floatValue() - expectedOpacity) < .0001F, "Source model opacity is preserved: " + kind);
                check(Math.abs(((Number)field(renderer, "opacity")).floatValue() - expectedOpacity) < .0001F, "Source renderer opacity is preserved: " + kind);
                check(Math.abs(((Number)field(renderer, "shadowStrength")).floatValue() - expectedOpacity) < .0001F, "Source shadow opacity is preserved: " + kind);
                row.fact.addProperty("skin_texture", texture.toString()); row.fact.addProperty("opacity", expectedOpacity);
                row.fact.addProperty("four_pixel_limbs_and_original_uvs", true);
            }
        }

        private Row add(Minecraft mc, EntityType<?> type, MobKind kind) {
            Mob entity = (Mob)type.create(mc.level);
            check(entity != null, "Variant entity constructor succeeds");
            entity.setPos(mc.player.position()); entity.setOnGround(true);
            entity.setYRot(0); entity.yRotO = 0; entity.yBodyRot = entity.yBodyRotO = 0;
            entity.yHeadRot = entity.yHeadRotO = 0;
            if (combat) entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            JsonObject fact = new JsonObject(); fact.addProperty("entity", EntityType.getKey(type).toString());
            fact.add("samples", new JsonArray()); REPORT.getAsJsonArray("entities").add(fact);
            Row row = new Row(entity, kind, fact); rows.add(row); return row;
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try (var settings = new BetterCombatMobSettings(true)) {
                graphics.fill(0, 0, width, height, 0xFF18222E);
                graphics.drawCenteredString(font, combat ? "Zombie variants: idle / walking / attack / recovered" : "Zombie variants: idle / walking", width / 2, 6, 0xFFF0F4FC);
                int columns = combat ? 4 : 2, label = Math.min(125, Math.max(80, width / 5));
                int cell = Math.max(1, (width - label - 8) / columns), rowHeight = Math.max(20, (height - 30) / rows.size());
                for (int index = 0; index < rows.size(); index++) {
                    Row row = rows.get(index); int y = 25 + index * rowHeight;
                    String name = row.fact.get("entity").getAsString();
                    graphics.drawString(font, name.substring(name.indexOf(':') + 1), 4, y + rowHeight / 2, 0xFFE0E7F2, false);
                    Map<String, List<Float>> idle = null, attack = null;
                    for (int column = 0; column < columns; column++) {
                        BetterCombatMobAnimations.stop(row.entity);
                        row.entity.walkAnimation.update(0F, 1F); row.entity.tickCount = column == 1 ? 60 : 40;
                        if (column == 1) for (int step = 0; step < 6; step++) row.entity.walkAnimation.update(.8F, 1F);
                        if (column == 2) {
                            var profile = BetterCombatBridge.profile(row.entity, 0);
                            check(profile != null, "Real Better Combat weapon profile resolves");
                            row.entity.tickCount = 0;
                            BetterCombatMobAnimations.start(row.entity, profile.animation(), 20, .4F, false);
                            row.entity.tickCount = 3;
                            check(BetterCombatMobAnimations.active(row.entity), "The actual weapon animation is active");
                        }
                        int x = label + column * cell;
                        graphics.fill(x, y, x + cell - 3, y + rowHeight - 3, 0xFF2D3B4D);
                        String phase = List.of("idle", "walking", "attack", "recovered").get(column);
                        Sample sample = draw(graphics, row.entity, phase, x + cell / 2F, y + rowHeight - 4F, Math.min(cell * .26F, rowHeight * .43F));
                        if (column == 0) idle = sample.pose;
                        if (column == 1) check(!same(idle, sample.pose), "Visible walking bones differ from idle: " + name);
                        if (column == 2) { attack = sample.pose; check(!same(idle, attack), "Actual weapon keyframes override FA idle bones: " + name); }
                        if (column == 3) check(!BetterCombatMobAnimations.active(row.entity) && !same(attack, sample.pose), "Attack end releases visible source bones: " + name);
                        if (row.kind == MobKind.INVISIBLE_STALKER) check(sample.alphas.contains(13), "Actual translucent source vertices retain 5% alpha");
                        if (row.kind == MobKind.SHADOW) check(sample.alphas.contains(255), "Actual shadow vertices retain their source opacity");
                        if (row.kind == MobKind.EARTH_ELEMENTAL || row.kind == MobKind.CLAY_GOLEM)
                            check(sample.glowVertices > 0, "The actual renderer draws its source elemental glow layer");
                        if (frames == 0) {
                            JsonObject fact = new JsonObject(); fact.addProperty("phase", List.of("idle", "walking", "attack", "recovered").get(column));
                            fact.addProperty("real_renderer_observed", true); fact.addProperty("fresh_animations_applied", sample.faApplied);
                            fact.addProperty("actual_head_geometry_mapped", sample.headMapped); fact.addProperty("matching_world_space_bones", sample.mapped);
                            fact.addProperty("glow_vertices", sample.glowVertices);
                            fact.add("vertex_alphas", new GsonBuilder().create().toJsonTree(sample.alphas));
                            fact.add("render_types", new GsonBuilder().create().toJsonTree(sample.renderTypes));
                            fact.add("visible_pose", new GsonBuilder().create().toJsonTree(sample.pose));
                            row.fact.getAsJsonArray("samples").add(fact);
                        }
                    }
                    BetterCombatMobAnimations.stop(row.entity);
                }
                graphics.flush(); frames++;
            } catch (Throwable failure) { finish(Minecraft.getInstance(), failure); }
            finally { current = null; }
        }

        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public boolean isPauseScreen() { return true; }
    }

    private static Sample draw(GuiGraphics graphics, Mob entity, String phase, float x, float y, float scale) {
        Sample sample = new Sample(entity, phase); current = sample;
        REPORT.addProperty("active_sample", EntityType.getKey(entity.getType()) + "/" + phase);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 50); graphics.pose().scale(scale, scale, -scale);
            graphics.pose().mulPose(new Quaternionf().rotationZ((float)Math.PI).rotateY(.35F));
            Lighting.setupForEntityInInventory(); dispatcher.setRenderShadow(false);
            MultiBufferSource tracked = type -> {
                String name = type.toString(); sample.renderTypes.add(name);
                return new TrackedVertices(graphics.bufferSource().getBuffer(type), sample, name.contains("_glow"));
            };
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0, 1, graphics.pose(), tracked, 15728880));
            graphics.flush();
        } finally {
            current = null; dispatcher.setRenderShadow(true); graphics.pose().popPose(); Lighting.setupFor3DItems();
        }
        if (sample.failure != null) throw new IllegalStateException("Rendered mapping oracle failed for "
                + EntityType.getKey(entity.getType()) + "/" + phase + ": " + sample.failure, sample.failure);
        check(sample.pose != null, "The actual LivingEntityRenderer Post event was observed");
        return sample;
    }

    /** Compare all eight world-space cube corners; skip inactive or hidden FA geometry. */
    private static int compareMappedGeometry(LegacyStalkerModel<?> model, Sample sample) throws Exception {
        ModelPart driver = (ModelPart)field(model, "animationRoot");
        Map<String, ModelPart> visible = parts(model);
        Map<String, List<Vector3f>> expected = new LinkedHashMap<>();
        Map<String, String> paths = new LinkedHashMap<>();
        collect(driver, "", new PoseStack(), visible, expected, paths);
        JsonObject diagnostic = new JsonObject();
        diagnostic.addProperty("entity", EntityType.getKey(sample.entity.getType()).toString());
        diagnostic.addProperty("phase", sample.phase);
        diagnostic.add("visible_part_transforms", new GsonBuilder().create().toJsonTree(pose(model)));
        diagnostic.add("active_driver_geometry_paths", new GsonBuilder().create().toJsonTree(paths));
        diagnostic.add("bones", new JsonArray());
        JsonArray candidates = new JsonArray();
        describeDriver(driver, "", true, candidates);
        diagnostic.add("all_driver_candidates", candidates);
        REPORT.add("last_mapping_check", diagnostic);
        int matched = 0;
        for (String name : List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")) {
            List<Vector3f> target = expected.get(name);
            check(target != null, "Active FA geometry exists for visible source bone " + name);
            if (name.equals("head")) check(paths.get(name).contains("head2"), "The oracle uses FA's real headwear/head2 geometry");
            PoseStack pose = new PoseStack(); visible.get(name).translateAndRotate(pose);
            List<Vector3f> actual = corners(cubes(visible.get(name)).getFirst(), pose);
            float worstDistance = 0F;
            for (Vector3f vertex : actual) {
                float nearest = Float.POSITIVE_INFINITY;
                for (Vector3f point : target) nearest = Math.min(nearest, point.distanceSquared(vertex));
                worstDistance = Math.max(worstDistance, nearest);
            }
            JsonObject bone = new JsonObject(); bone.addProperty("bone", name); bone.addProperty("driver_path", paths.get(name));
            bone.add("visible_corners", vectors(actual)); bone.add("driver_corners", vectors(target));
            bone.addProperty("largest_nearest_corner_distance", Math.sqrt(worstDistance));
            bone.addProperty("matches", worstDistance < .000001F);
            diagnostic.getAsJsonArray("bones").add(bone);
            check(worstDistance < .000001F, "Visible geometry mismatch for " + EntityType.getKey(sample.entity.getType())
                    + "/" + sample.phase + "/" + name + "; driver=" + paths.get(name) + "; nearest corner error="
                    + Math.sqrt(worstDistance) + "; visible transform=" + pose(model).get(name)
                    + "; visible corners=" + vectors(actual) + "; driver corners=" + vectors(target));
            matched++;
        }
        return matched;
    }

    private static JsonArray vectors(List<Vector3f> vectors) {
        JsonArray result = new JsonArray();
        for (Vector3f vector : vectors) {
            JsonArray point = new JsonArray(); point.add(vector.x); point.add(vector.y); point.add(vector.z); result.add(point);
        }
        return result;
    }

    private static void describeDriver(ModelPart part, String path, boolean ancestorVisible, JsonArray output) throws Exception {
        boolean active = ancestorVisible && part.visible;
        if (!bone(path).isEmpty()) {
            JsonObject item = new JsonObject(); item.addProperty("path", path);
            item.addProperty("visible", part.visible); item.addProperty("ancestors_visible", ancestorVisible);
            item.addProperty("skip_draw", part.skipDraw); item.addProperty("cube_count", cubes(part).size());
            item.add("transform", new GsonBuilder().create().toJsonTree(List.of(part.x, part.y, part.z,
                    part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale)));
            JsonArray bounds = new JsonArray();
            for (ModelPart.Cube cube : cubes(part)) bounds.add(new GsonBuilder().create().toJsonTree(
                    List.of(cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ)));
            item.add("cube_bounds", bounds); output.add(item);
        }
        for (var entry : children(part).entrySet()) describeDriver(entry.getValue(), path + "/" + entry.getKey(), active, output);
    }

    private static void collect(ModelPart part, String path, PoseStack pose, Map<String, ModelPart> visible,
            Map<String, List<Vector3f>> expected, Map<String, String> paths) throws Exception {
        if (!part.visible) return;
        pose.pushPose(); part.translateAndRotate(pose);
        try {
            String bone = bone(path);
            if (!part.skipDraw && visible.containsKey(bone)) for (ModelPart.Cube cube : cubes(part)) {
                ModelPart.Cube source = cubes(visible.get(bone)).getFirst();
                if (Math.abs((cube.maxX - cube.minX) - (source.maxX - source.minX)) > .001F
                        || Math.abs((cube.maxY - cube.minY) - (source.maxY - source.minY)) > .001F
                        || Math.abs((cube.maxZ - cube.minZ) - (source.maxZ - source.minZ)) > .001F) continue;
                if (!expected.containsKey(bone) || path.contains("head2")) {
                    expected.put(bone, corners(cube, pose)); paths.put(bone, path);
                }
            }
            for (var entry : children(part).entrySet()) collect(entry.getValue(), path + "/" + entry.getKey(), pose, visible, expected, paths);
        } finally { pose.popPose(); }
    }

    private static String bone(String path) {
        String[] names = path.split("/");
        for (int i = names.length - 1; i >= 0; i--) {
            String name = names[i].replaceFirst("^EMF_", "");
            if (name.equals("head2")) return "head";
            if (List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg").contains(name)) return name;
        }
        return "";
    }

    private static List<Vector3f> corners(ModelPart.Cube cube, PoseStack pose) {
        List<Vector3f> result = new ArrayList<>();
        for (float x : new float[]{cube.minX, cube.maxX}) for (float y : new float[]{cube.minY, cube.maxY})
            for (float z : new float[]{cube.minZ, cube.maxZ}) result.add(pose.last().pose().transformPosition(new Vector3f(x / 16, y / 16, z / 16)));
        return result;
    }

    private static Map<String, ModelPart> parts(HumanoidModel<?> model) {
        Map<String, ModelPart> parts = new LinkedHashMap<>();
        parts.put("head", model.head); parts.put("hat", model.hat); parts.put("body", model.body);
        parts.put("right_arm", model.rightArm); parts.put("left_arm", model.leftArm);
        parts.put("right_leg", model.rightLeg); parts.put("left_leg", model.leftLeg); return parts;
    }

    private static Map<String, List<Float>> pose(HumanoidModel<?> model) {
        Map<String, List<Float>> result = new LinkedHashMap<>();
        parts(model).forEach((name, part) -> result.put(name, List.of(part.x, part.y, part.z, part.xRot,
                part.yRot, part.zRot, part.xScale, part.yScale, part.zScale))); return result;
    }

    private static List<Float> geometry(ModelPart part) throws Exception {
        List<Float> result = new ArrayList<>();
        for (ModelPart.Cube cube : cubes(part)) {
            result.addAll(List.of(cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ));
            for (Object polygon : (Object[])field(cube, "polygons")) for (Object vertex : (Object[])field(polygon, "vertices")) {
                result.add(((Number)field(vertex, "u")).floatValue()); result.add(((Number)field(vertex, "v")).floatValue());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked") private static List<ModelPart.Cube> cubes(ModelPart part) throws Exception {
        return (List<ModelPart.Cube>)field(part, "cubes");
    }
    @SuppressWarnings("unchecked") private static Map<String, ModelPart> children(ModelPart part) throws Exception {
        return (Map<String, ModelPart>)field(part, "children");
    }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    @SuppressWarnings("rawtypes") private static LivingEntityRenderer renderer(Mob entity) {
        return (LivingEntityRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    }
    @SuppressWarnings("unchecked") private static ResourceLocation texture(Mob entity) { return renderer(entity).getTextureLocation(entity); }
    private static boolean same(Map<String, List<Float>> first, Map<String, List<Float>> second) {
        if (first == null || second == null || !first.keySet().equals(second.keySet())) return false;
        for (String key : first.keySet()) for (int index = 0; index < first.get(key).size(); index++)
            if (Math.abs(first.get(key).get(index) - second.get(key).get(index)) > .0001F) return false;
        return true;
    }

    private static Path output() {
        return Path.of(System.getProperty("mite.zombieVariantAuditOutput", "build/verification/zombie-variant-client-audit.json")).toAbsolutePath().normalize();
    }
    private static void finish(Minecraft mc, Throwable failure) {
        if (done) return;
        done = true;
        if (!REPORT.has("failures")) REPORT.add("failures", new JsonArray());
        if (failure != null) {
            REPORT.addProperty("status", "failed"); REPORT.getAsJsonArray("failures").add(failure.toString());
            JsonArray causes = new JsonArray();
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 20; depth++, cause = cause.getCause()) {
                JsonObject detail = new JsonObject(); detail.addProperty("type", cause.getClass().getName());
                detail.addProperty("message", cause.getMessage()); causes.add(detail);
            }
            REPORT.add("failure_causes", causes);
            java.io.StringWriter stack = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(stack));
            REPORT.addProperty("failure_stack_trace", stack.toString());
            Mite.LOGGER.error("Zombie variant render audit failed", failure);
        }
        REPORT.addProperty("safe_client_shutdown_requested", true);
        try {
            Path path = output(); Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n", StandardCharsets.UTF_8);
        } catch (Exception writing) { Mite.LOGGER.error("Cannot write zombie variant audit", writing); }
        mc.stop();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private record Row(Mob entity, MobKind kind, JsonObject fact) {}
    private static final class Sample {
        final Mob entity;
        final String phase;
        final Set<String> renderTypes = new LinkedHashSet<>();
        final Set<Integer> alphas = new LinkedHashSet<>();
        Map<String, List<Float>> pose;
        Throwable failure;
        boolean faApplied, headMapped;
        int mapped, glowVertices;
        Sample(Mob entity, String phase) { this.entity = entity; this.phase = phase; }
    }
    private record TrackedVertices(VertexConsumer delegate, Sample sample, boolean glow) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { if (glow) sample.glowVertices++; delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { sample.alphas.add(a); delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }
}
