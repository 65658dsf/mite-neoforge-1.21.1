package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.gametest.BetterCombatMobSettings;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.client.BetterCombatMobAnimations;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
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

/** Opt-in real resource, model, armor and framebuffer audit in a newly created disposable world. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class BetterCombatMobClientAudit {
    private static boolean baked;
    private static boolean started;
    private static boolean done;
    private static int waiting;
    private static AuditScreen screen;
    private static JsonObject report;
    private static Mob capturing;
    private static Map<String, List<Float>> captured;
    private static int alignedArmorModels;

    private BetterCombatMobClientAudit() {}

    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event) {
            if (Boolean.getBoolean("mite.betterCombatMobAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void afterTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mite.betterCombatMobAudit") || !baked || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
                    onboarding.onClose();
                    return;
                }
                if (!(mc.screen instanceof TitleScreen)) return;
                started = true;
                report = new JsonObject();
                report.addProperty("author", "NingMeng123");
                report.add("failures", new JsonArray());
                report.add("entities", new JsonArray());
                report.addProperty("bettercombat_loaded", BetterCombatBridge.enabled());
                report.addProperty("playeranimator_loaded", ModList.get().isLoaded("playeranimator"));
                if (!BetterCombatBridge.enabled() || !ModList.get().isLoaded("playeranimator")) {
                    report.addProperty("status", "passed_optional_mods_absent");
                    finish(mc);
                    return;
                }
                String world = "bettercombat-mob-audit-" + System.currentTimeMillis();
                report.addProperty("disposable_world", world);
                report.addProperty("scope", "installed keyframe registry, native model and held-item rendering, actual armor layers, shared model restoration, framebuffer screenshot");
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(123456789L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++waiting < 2400, "Timed out loading the disposable audit world");
            if (screen == null && mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                screen = new AuditScreen(mc);
                mc.setScreen(screen);
            }
        } catch (Throwable failure) {
            fail(mc, failure);
        }
    }

    @SubscribeEvent public static void afterLivingRender(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity() != capturing) return;
        captured = pose(event.getRenderer().getModel());
        alignedArmorModels = 0;
        if (!(event.getRenderer().getModel() instanceof HumanoidModel<?> parent)) return;
        try {
            Field layers = LivingEntityRenderer.class.getDeclaredField("layers");
            layers.setAccessible(true);
            for (Object layer : (List<?>)layers.get(event.getRenderer())) if (layer instanceof HumanoidArmorLayer<?, ?, ?>) {
                for (String name : List.of("innerModel", "outerModel")) {
                    Field field = HumanoidArmorLayer.class.getDeclaredField(name);
                    field.setAccessible(true);
                    HumanoidModel<?> armor = (HumanoidModel<?>)field.get(layer);
                    check(pose(armor).equals(pose(parent)), "The actually rendered armor model follows the animated mob: " + name);
                    alignedArmorModels++;
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @SubscribeEvent public static void afterFrame(RenderFrameEvent.Post event) {
        if (done || screen == null || screen.renderedFrames < 2 || Minecraft.getInstance().screen != screen) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            Path screenshot = Path.of(System.getProperty("mite.betterCombatMobAuditScreenshotOutput",
                    output().resolveSibling("bettercombat-mob-client-audit.png").toString())).toAbsolutePath().normalize();
            Files.createDirectories(screenshot.getParent());
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(screenshot);
                report.addProperty("screenshot_width", image.getWidth());
                report.addProperty("screenshot_height", image.getHeight());
            }
            report.addProperty("screenshot", screenshot.toString());
            report.addProperty("native_rendered_frames", screen.renderedFrames);
            report.addProperty("client_only_entities_added_to_world", false);
            report.addProperty("status", "passed_real_keyframe_models_armor_and_framebuffer");
            finish(mc);
        } catch (Throwable failure) {
            fail(mc, failure);
        }
    }

    private static final class AuditScreen extends Screen {
        private final List<Row> rows = new ArrayList<>();
        private int renderedFrames;

        private AuditScreen(Minecraft mc) {
            super(Component.literal("Better Combat armed monster animation audit"));
            for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.SKELETON,
                    EntityType.PIGLIN, EntityType.VINDICATOR, MiteMobs.type(MobKind.LONGDEAD))) {
                Mob mob = type.create(mc.level), control = type.create(mc.level);
                check(mob != null && control != null, "Native entity constructors succeed: " + type);
                equip(mob); equip(control);
                var profile = BetterCombatBridge.profile(mob, 0);
                check(profile != null, "The equipped item resolves to real Better Combat weapon attributes: " + type);
                var id = ResourceLocation.parse(profile.animation());
                check(registeredKeyframes(id),
                        "The real resource reload registered the selected animation: " + id);
                JsonObject fact = new JsonObject();
                fact.addProperty("entity", EntityType.getKey(type).toString());
                fact.addProperty("animation", profile.animation());
                fact.addProperty("duration_ticks", profile.cooldownTicks());
                fact.addProperty("effective_upswing", profile.upswing());
                fact.add("samples", new JsonArray());
                report.getAsJsonArray("entities").add(fact);
                rows.add(new Row(mob, control, profile, fact));
            }
            verifyCompatibilityToggle(rows.getFirst());
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try (var compatibility = new BetterCombatMobSettings(true)) {
                graphics.fill(0, 0, width, height, 0xFF121924);
                graphics.drawCenteredString(font, "Better Combat: native / windup / impact / recovery", width / 2, 8, 0xFFE8EEF8);
                int top = 32, label = 86, cellWidth = Math.max(70, (width - label - 10) / 4);
                // Respect the logical GUI viewport: an auto-scaled 854 x 480 window is only 427 x 240 here.
                int rowHeight = Math.max(1, Math.min(100, (height - top - 5) / rows.size()));
                float scale = Math.min(38F, rowHeight * .39F);
                for (int index = 0; index < rows.size(); index++) {
                    Row row = rows.get(index);
                    int y = top + index * rowHeight;
                    graphics.drawString(font, EntityType.getKey(row.mob().getType()).getPath(), 6, y + rowHeight / 2, 0xFFD8E4F2, false);
                    int duration = row.profile().cooldownTicks();
                    int impact = row.profile().windupTicks();
                    int[] ages = {impact, Math.max(1, impact / 2), impact, Math.max(impact + 1, duration - 1)};
                    for (int column = 0; column < 4; column++) {
                        int x = label + column * cellWidth;
                        graphics.fill(x, y, x + cellWidth - 2, y + rowHeight - 2, index % 2 == 0 ? 0xFF202D3E : 0xFF1A2534);
                        sample(graphics, row, ages[column], column > 0, x + cellWidth / 2F, y + rowHeight - 4, scale,
                                column == 0 ? "native" : column == 1 ? "windup" : column == 2 ? "impact" : "recovery");
                    }
                }
                graphics.flush();
                renderedFrames++;
            } catch (Throwable failure) {
                fail(Minecraft.getInstance(), failure);
            } finally {
                capturing = null;
            }
        }

        private void sample(GuiGraphics graphics, Row row, int age, boolean animated, float x, float y,
                float scale, String phase) {
            Mob mob = row.mob(), control = row.control();
            BetterCombatMobAnimations.stop(control);
            control.tickCount = age - 1;
            // Each entity owns its native pose; identity can affect native idle animation and look interpolation.
            Map<String, List<Float>> controlNativePose = render(graphics, control, -500F, -500F, scale);
            BetterCombatMobAnimations.stop(mob);
            mob.tickCount = age - 1;
            Map<String, List<Float>> nativePose = render(graphics, mob, -500F, -500F, scale);
            if (animated) {
                mob.tickCount = 0;
                BetterCombatMobAnimations.start(mob, row.profile().animation(), row.profile().cooldownTicks(),
                        row.profile().windupTicks() / (float)row.profile().cooldownTicks(), false);
                mob.tickCount = age - 1;
            }
            Map<String, List<Float>> visible = render(graphics, mob, x, y, scale);
            int armorModels = alignedArmorModels;
            EntityModel<?> model = ((LivingEntityRenderer<?, ?>)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob)).getModel();
            check(pose(model).equals(nativePose), "Animated model pivots, rotations and scale restore after native rendering: " + phase);
            BetterCombatMobAnimations.stop(mob);
            control.tickCount = age - 1;
            Map<String, List<Float>> consecutive = render(graphics, control, -500F, -500F, scale);
            check(consecutive.equals(controlNativePose), "A consecutive different unanimated entity retains its own native pose: "
                    + phase + "; before=" + controlNativePose + "; after=" + consecutive);
            if (animated && (phase.equals("windup") || phase.equals("impact")))
                check(!visible.equals(nativePose), "The actual renderer applies weapon keyframes during " + phase);
            if (renderedFrames == 0) {
                JsonObject fact = new JsonObject();
                fact.addProperty("phase", phase); fact.addProperty("age_ticks", age);
                fact.addProperty("animation_changes_model", !visible.equals(nativePose));
                fact.addProperty("restored_native_pose", true);
                fact.addProperty("consecutive_entity_has_no_leak", true);
                fact.addProperty("aligned_actual_armor_models", armorModels);
                fact.add("native_pose", jsonPose(nativePose));
                fact.add("rendered_pose", jsonPose(visible));
                fact.add("control_native_pose_before", jsonPose(controlNativePose));
                fact.add("control_native_pose_after", jsonPose(consecutive));
                row.fact().getAsJsonArray("samples").add(fact);
            }
        }

        private Map<String, List<Float>> render(GuiGraphics graphics, Mob mob, float x, float y, float scale) {
            capturing = mob; captured = null;
            InventoryScreen.renderEntityInInventory(graphics, x, y, scale, new Vector3f(),
                    new Quaternionf().rotationZ((float)Math.PI).rotateY(.35F), null, mob);
            capturing = null;
            check(captured != null, "The real native LivingEntityRenderer fires its post-render event");
            return captured;
        }

        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public boolean isPauseScreen() { return true; }
    }

    private static void equip(Mob mob) {
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        mob.setNoAi(true); mob.setAggressive(true);
        mob.setYRot(0F); mob.yRotO = 0F; mob.setXRot(0F); mob.xRotO = 0F;
        mob.yBodyRot = 0F; mob.yBodyRotO = 0F; mob.yHeadRot = 0F; mob.yHeadRotO = 0F;
    }

    private static void verifyCompatibilityToggle(Row row) {
        Mob mob = row.mob();
        var profile = row.profile();
        check(!MiteConfig.BETTER_COMBAT_MOBS.getDefault(), "Monster animation compatibility defaults to disabled");
        try (var disabled = new BetterCombatMobSettings(false)) {
            BetterCombatMobAnimations.start(mob, profile.animation(), profile.cooldownTicks(), (float)profile.upswing(), false);
            check(!BetterCombatMobAnimations.active(mob), "Disabled compatibility refuses an incoming monster animation");
            try (var enabled = new BetterCombatMobSettings(true)) {
                BetterCombatMobAnimations.start(mob, profile.animation(), profile.cooldownTicks(), (float)profile.upswing(), false);
                check(BetterCombatMobAnimations.active(mob), "Explicitly enabled compatibility starts the real monster animation");
                try (var offDuringWindup = new BetterCombatMobSettings(false)) {
                    check(!BetterCombatMobAnimations.active(mob), "Disabling during windup immediately clears the real animation");
                }
                check(!BetterCombatMobAnimations.active(mob), "Reenabling does not revive an interrupted monster animation");
            }
            report.addProperty("default_disabled_animation_toggle_verified", true);
        } finally { BetterCombatMobAnimations.stop(mob); }
    }

    private static boolean registeredKeyframes(ResourceLocation id) {
        try {
            Object animation = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry")
                    .getMethod("getAnimation", ResourceLocation.class).invoke(null, id);
            return animation != null && animation.getClass().getName().equals("dev.kosmx.playerAnim.core.data.KeyframeAnimation");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static Map<String, List<Float>> pose(EntityModel<?> model) {
        Map<String, ModelPart> parts = new LinkedHashMap<>();
        if (model instanceof HumanoidModel<?> humanoid) {
            parts.put("head", humanoid.head); parts.put("torso", humanoid.body);
            parts.put("right_arm", humanoid.rightArm); parts.put("left_arm", humanoid.leftArm);
            parts.put("right_leg", humanoid.rightLeg); parts.put("left_leg", humanoid.leftLeg);
        } else if (model instanceof IllagerModel<?> illager) {
            for (String name : List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg"))
                parts.put(name, illager.root().getChild(name));
        }
        Map<String, List<Float>> result = new LinkedHashMap<>();
        parts.forEach((name, part) -> result.put(name, List.of(part.x, part.y, part.z, part.xRot, part.yRot,
                part.zRot, part.xScale, part.yScale, part.zScale)));
        return result;
    }

    private static JsonObject jsonPose(Map<String, List<Float>> pose) {
        JsonObject result = new JsonObject();
        pose.forEach((name, values) -> {
            JsonArray vector = new JsonArray(); values.forEach(vector::add); result.add(name, vector);
        });
        return result;
    }

    private static void fail(Minecraft mc, Throwable failure) {
        if (report == null) { report = new JsonObject(); report.add("failures", new JsonArray()); }
        report.getAsJsonArray("failures").add(failure.toString());
        report.addProperty("status", "failed");
        Mite.LOGGER.error("Better Combat mob client audit failed", failure);
        finish(mc);
    }

    private static void finish(Minecraft mc) {
        done = true;
        report.addProperty("safe_client_shutdown_requested", true);
        try {
            Path output = output(); Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        } catch (Exception failure) { Mite.LOGGER.error("Cannot write the Better Combat mob client audit", failure); }
        mc.stop();
    }

    private static Path output() {
        return Path.of(System.getProperty("mite.betterCombatMobAuditOutput", "build/verification/bettercombat-mob-client-audit.json"))
                .toAbsolutePath().normalize();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Row(Mob mob, Mob control, BetterCombatBridge.Profile profile, JsonObject fact) {}
}
