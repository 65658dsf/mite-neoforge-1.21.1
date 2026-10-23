package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.gametest.BetterCombatMobSettings;
import io.github.ningmeng123.mite.client.BetterCombatMobAnimations;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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

/** Real FA/EMF/Better Combat rendering in a disposable world; optional classes stay isolated. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class FreshAnimationsCombatClientAudit {
    private static final String EMF_API = "traben.entity_model_features.EMFAnimationApi";
    private static final String BC_ANIMATABLE = "net.bettercombat.client.animation.PlayerAttackAnimatable";
    private static final int PAGE_ROWS = 7;
    private static final Set<String> COMBAT_BONES = Set.of("root", "head", "hat", "body", "right_arm", "left_arm",
            "right_leg", "left_leg", "jacket", "right_sleeve", "left_sleeve", "right_pants", "left_pants", "arms");
    private static boolean baked, started, done;
    private static int waiting;
    private static JsonObject report;
    private static AuditScreen screen;
    private static Trace trace;
    private static Method activeMethod;
    private static final Map<Object, Oracle> PENDING = new IdentityHashMap<>();

    private FreshAnimationsCombatClientAudit() {}

    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event) {
            if (Boolean.getBoolean("mite.freshAnimationsCombatAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void afterTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mite.freshAnimationsCombatAudit") || !baked || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                started = true;
                report = new JsonObject();
                report.addProperty("author", "NingMeng123");
                report.add("failures", new JsonArray()); report.add("entities", new JsonArray());
                report.add("screenshots", new JsonArray());
                for (String mod : List.of("entity_model_features", "entity_texture_features", "bettercombat", "playeranimator")) {
                    boolean loaded = ModList.get().isLoaded(mod);
                    report.addProperty(mod + "_loaded", loaded);
                    check(loaded, "Required audit mod is loaded: " + mod);
                }
                requireResource(mc, "minecraft:optifine/cem/zombie.jem", "FreshAnimations");
                requireResource(mc, "minecraft:emf/cem/player.jem", "FA+Player");
                activeMethod = Class.forName("io.github.ningmeng123.mite.client.BetterCombatFreshAnimations")
                        .getMethod("active", LivingEntity.class);
                installObserver();
                String world = "freshanimations-combat-audit-" + System.currentTimeMillis();
                report.addProperty("disposable_world", world);
                report.addProperty("scope", "actual FA animation before/after hooks, final model and armor poses, player BC layer, shared-model isolation, animal control, framebuffer pages");
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(123456789L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++waiting < 3600, "Timed out loading/rendering the disposable FA combat audit world");
            if (screen == null && mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                screen = new AuditScreen(mc); mc.setScreen(screen);
            }
        } catch (Throwable failure) { fail(mc, failure); }
    }

    private static void requireResource(Minecraft mc, String id, String expectedPack) {
        var resource = mc.getResourceManager().getResource(ResourceLocation.parse(id)).orElseThrow(
                () -> new IllegalStateException("Audit requires an enabled resource: " + id));
        String source = resource.sourcePackId();
        report.addProperty(id + "_source", source);
        check(source.toLowerCase().contains(expectedPack.toLowerCase()), "Expected resource pack supplies " + id + ": " + source);
    }

    /** The observer registers after the production guard and only reads the optional EMF API. */
    private static void installObserver() throws Exception {
        Loaded.install();
        report.addProperty("registered_real_emf_animation_observer", true);
    }

    /** Loaded only after the explicitly enabled audit confirms its optional dependencies. */
    private static final class Loaded {
        static void install() throws Exception {
            check(traben.entity_model_features.EMFAnimationApi.registerAnimationHook(new Observer()),
                    "The real EMF animation observer registered successfully");
        }

        private static final class Observer extends traben.entity_model_features.EMFAnimationApi.EMFAnimationHook {
            @Override public boolean onAnimationStart(AnimationContext context, boolean cancelled) {
                beforeEmf(context); return true;
            }
            @Override public void onAnimationEnd(AnimationContext context, boolean cancelled) {
                afterEmf(context);
            }
        }
    }

    /** Observe the real optional EMF hook; failures are deferred until after rendering. */
    public static void beforeEmf(Object context) {
        if (trace == null) return;
        try {
            Object state = invoke(context, "activeState");
            Object entity = invoke(state, "emfEntity");
            if (entity != trace.entity) return;
            Object root = invoke(context, "animatingModelRoot");
            Object mainRoot = Class.forName("traben.entity_model_features.models.IEMFModel")
                    .getMethod("emf$getEMFRootModel").invoke(model(trace.entity));
            boolean main = root == mainRoot || poseDriver(trace.entity);
            trace.rootNames.add("main=" + main + ", root=" + root.getClass().getField("modelName").get(root));
            var canonical = canonical(root);
            Map<ModelPart, PartState> all = new IdentityHashMap<>();
            ((ModelPart)root).getAllParts().forEach(part -> all.put(part, new PartState(part)));
            PENDING.put(root, new Oracle(trace, active(trace.entity) && !canonical.isEmpty(), main, canonical, all,
                    main ? pose(model(trace.entity), false) : Map.of()));
        } catch (Throwable failure) { trace.errors.add("before EMF: " + failure); }
    }

    public static void afterEmf(Object context) {
        if (trace == null) return;
        try {
            Object root = invoke(context, "animatingModelRoot");
            Oracle before = PENDING.remove(root);
            if (before == null || before.owner != trace) return;
            if (invoke(context, "error") != null) trace.errors.add("EMF reported an animation error: " + invoke(context, "error"));
            trace.emfCalls++;
            if (before.main) trace.mainEmfCalls++;
            boolean changed = before.all.entrySet().stream().anyMatch(entry -> !entry.getValue().matches(entry.getKey()));
            if (changed) trace.changedEmfCalls++;
            if (before.active) {
                trace.guardedCalls++;
                if (!sameStates(before.canonical, canonical(root))) trace.errors.add("EMF overwrote an active combat canonical part");
                if (before.main && !poseDriver(trace.entity)) trace.expectedFinalCore = before.modelPose;
            }
        } catch (Throwable failure) { trace.errors.add("after EMF: " + failure); }
    }

    @SubscribeEvent public static void afterLivingRender(RenderLivingEvent.Post<?, ?> event) {
        if (trace == null || event.getEntity() != trace.entity) return;
        try {
            EntityModel<?> model = event.getRenderer().getModel();
            trace.finalPose = pose(model, false);
            if (active(trace.entity) && !trace.expectedFinalCore.isEmpty()
                    && !samePose(trace.expectedFinalCore, trace.finalPose)) trace.errors.add("Final rendered pose differs from the pre-EMF combat oracle");
            if (!active(trace.entity) || !(model instanceof HumanoidModel<?> parent)) return;
            if (parent instanceof PlayerModel<?> player) {
                for (var pair : List.of(new ModelPart[]{player.jacket, player.body},
                        new ModelPart[]{player.rightSleeve, player.rightArm}, new ModelPart[]{player.leftSleeve, player.leftArm},
                        new ModelPart[]{player.rightPants, player.rightLeg}, new ModelPart[]{player.leftPants, player.leftLeg})) {
                    if (!new PartState(pair[0]).sameTransform(new PartState(pair[1]))) trace.errors.add("Clothing does not follow the combat limb");
                    trace.clothingPairs++;
                }
            }
            Field layers = LivingEntityRenderer.class.getDeclaredField("layers"); layers.setAccessible(true);
            for (Object layer : (List<?>)layers.get(event.getRenderer())) if (layer instanceof HumanoidArmorLayer<?, ?, ?>) {
                for (String name : List.of("innerModel", "outerModel")) {
                    Field field = HumanoidArmorLayer.class.getDeclaredField(name); field.setAccessible(true);
                    if (!samePose(pose((EntityModel<?>)field.get(layer), false), trace.finalPose))
                        trace.errors.add("Actual armor model differs from combat limbs: " + name);
                    trace.armorModels++;
                }
            }
        } catch (Throwable failure) { trace.errors.add("post render: " + failure); }
    }

    @SubscribeEvent public static void afterFrame(RenderFrameEvent.Post event) {
        if (done || screen == null || screen.framesOnPage < 2 || Minecraft.getInstance().screen != screen) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            Path screenshot = output().resolveSibling("freshanimations-combat-audit-page-" + (screen.page + 1) + ".png");
            Files.createDirectories(screenshot.getParent());
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(screenshot); }
            report.getAsJsonArray("screenshots").add(screenshot.toString());
            screen.page++; screen.framesOnPage = 0;
            if (screen.page * PAGE_ROWS >= screen.rows.size()) {
                report.addProperty("status", "passed_real_fa_emf_combat_rendering");
                report.addProperty("client_only_entities_added_to_world", false);
                finish(mc);
            }
        } catch (Throwable failure) { fail(mc, failure); }
    }

    private static final class AuditScreen extends Screen {
        final List<Row> rows = new ArrayList<>();
        int page, framesOnPage;

        AuditScreen(Minecraft mc) {
            super(Component.literal("Fresh Animations / Better Combat compatibility audit"));
            add(mc.player, new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")),
                    "minecraft:local_player", true);
            add(new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")),
                    new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "NingMeng123")), "minecraft:remote_player", true);
            JsonArray lifecycle = new JsonArray();
            for (Row row : rows) lifecycle.add(checkPlayerLifecycle(row));
            report.add("player_attack_lifecycle", lifecycle);
            for (var type : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITHER_SKELETON,
                    EntityType.PIGLIN, EntityType.VINDICATOR)) {
                add(type.create(mc.level), type.create(mc.level), EntityType.getKey(type).toString(), true);
            }
            for (MobKind kind : List.of(MobKind.LONGDEAD, MobKind.LONGDEAD_GUARDIAN,
                    MobKind.BONE_LORD, MobKind.ANCIENT_BONE_LORD, MobKind.JELLY, MobKind.BLOB,
                    MobKind.OOZE, MobKind.PUDDING)) {
                var type = MiteMobs.type(kind);
                add(type.create(mc.level), type.create(mc.level), EntityType.getKey(type).toString(), true);
            }
            int modern = 0;
            for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
                if (!entry.getKey().location().getNamespace().equals("modern_ite")) continue;
                var entity = entry.getValue().create(mc.level);
                if (!(entity instanceof Mob mob) || !humanoid(model(mob))) continue;
                add(mob, (LivingEntity)entry.getValue().create(mc.level), entry.getKey().location().toString(),
                        entry.getKey().location().getPath().equals("skeleton_zombie"));
                modern++;
            }
            report.addProperty("modern_ite_loaded", ModList.get().isLoaded("modern_ite"));
            report.addProperty("modern_ite_humanoid_entities", modern);
            if (ModList.get().isLoaded("modern_ite")) check(modern > 0, "Registered extreme-edition humanoid renderers are included");
            add(EntityType.COW.create(mc.level), EntityType.COW.create(mc.level), "minecraft:cow", true);
            report.addProperty("entity_rows", rows.size());
        }

        private void add(LivingEntity entity, LivingEntity control, String key, boolean requireFa) {
            check(entity != null && control != null, "Audit entity constructors succeed: " + key);
            equip(entity); equip(control);
            JsonObject fact = new JsonObject(); fact.addProperty("entity", key); fact.add("samples", new JsonArray());
            report.getAsJsonArray("entities").add(fact);
            rows.add(new Row(entity, control, key, requireFa, fact));
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try (var compatibility = new BetterCombatMobSettings(true)) {
                graphics.fill(0, 0, width, height, 0xFF111923);
                graphics.drawCenteredString(font, "Fresh Animations + Better Combat: idle / walk / windup / impact / recovery", width / 2, 6, 0xFFE8EEF8);
                int label = Math.min(138, Math.max(85, width / 4));
                int cell = Math.max(1, (width - label - 8) / 5), top = 25;
                int end = Math.min(rows.size(), (page + 1) * PAGE_ROWS);
                int rowHeight = Math.max(20, (height - top - 8) / Math.max(1, end - page * PAGE_ROWS));
                for (int index = page * PAGE_ROWS; index < end; index++) {
                    Row row = rows.get(index); int y = top + (index % PAGE_ROWS) * rowHeight;
                    graphics.drawString(font, row.key.substring(row.key.indexOf(':') + 1), 4, y + rowHeight / 2, 0xFFD8E4F2, false);
                    int[] ages = {1, 1, 3, 8, 14};
                    for (int column = 0; column < 5; column++) {
                        int x = label + column * cell;
                        graphics.fill(x, y, x + cell - 2, y + rowHeight - 2, 0xFF203044);
                        sample(graphics, row, ages[column], column > 1, x + cell / 2F, y + rowHeight - 4,
                                Math.min(34F, rowHeight * .38F), List.of("idle", "walk", "windup", "impact", "recovery").get(column));
                    }
                }
                graphics.flush(); framesOnPage++;
            } catch (Throwable failure) { fail(Minecraft.getInstance(), failure); }
            finally { trace = null; PENDING.clear(); }
        }

        private void sample(GuiGraphics graphics, Row row, int age, boolean attack, float x, float y, float scale, String phase) throws ReflectiveOperationException {
            stop(row.entity); stop(row.control);
            row.entity.walkAnimation.update(0F, 1F);
            row.entity.tickCount = row.control.tickCount = 40;
            Trace controlBefore = renderEntity(graphics, row.control, -500, -500, scale);
            Trace idle = renderEntity(graphics, row.entity, -500, -500, scale);
            boolean animal = !humanoid(model(row.entity));
            boolean slime = row.entity instanceof net.minecraft.world.entity.monster.Slime;
            if (row.requireFa) {
                check(idle.mainEmfCalls > 0, "Actual FA model animates " + row.key + "; emfCalls=" + idle.emfCalls
                        + "; mainEmfCalls=" + idle.mainEmfCalls + "; roots=" + idle.rootNames);
                if (phase.equals("idle") && !slime) check(idle.changedEmfCalls > 0, "FA idle changes actual model parts: " + row.key);
            }
            if (attack && !animal) begin(row.entity, age);
            if (phase.equals("walk")) {
                for (int index = 0; index < 6; index++) row.entity.walkAnimation.update(.8F, 1F);
                row.entity.tickCount = 60;
            }
            check(active(row.entity) == (attack && !animal), "Combat guard follows actual attack state: " + row.key + " / " + phase);
            Trace visible = renderEntity(graphics, row.entity, x, y, scale);
            if (phase.equals("walk") && !slime) check(!samePose(idle.finalPose, visible.finalPose),
                    "Actual renderer has a distinct walking pose: " + row.key);
            if (attack && !animal) {
                if (row.requireFa) check(visible.guardedCalls > 0, "FA attack passes through the real compatibility guard: " + row.key);
                check(!samePose(idle.finalPose, visible.finalPose), "Real weapon keyframes change the rendered pose: " + row.key + " / " + phase);
            }
            if (animal) {
                check(visible.guardedCalls == 0, "Animal FA animation is never guarded");
                if (!phase.equals("walk")) check(samePose(idle.finalPose, visible.finalPose), "Unrelated attack sample leaves animal pose unchanged");
            }
            stop(row.entity);
            check(!active(row.entity), "Guard releases immediately after ending the attack: " + row.key);
            Trace controlAfter = renderEntity(graphics, row.control, -500, -500, scale);
            check(samePose(controlBefore.finalPose, controlAfter.finalPose), "Shared renderer does not leak a combat pose: " + row.key);
            Trace resumed = renderEntity(graphics, row.entity, -500, -500, scale);
            if (row.requireFa) check(resumed.mainEmfCalls > 0 && resumed.guardedCalls == 0, "FA animation resumes after combat: " + row.key);
            if (framesOnPage == 0) {
                JsonObject sample = new JsonObject(); sample.addProperty("phase", phase);
                sample.addProperty("actual_emf_calls", visible.emfCalls); sample.addProperty("guarded_emf_calls", visible.guardedCalls);
                sample.addProperty("idle_emf_changed_calls", idle.changedEmfCalls);
                sample.addProperty("actual_armor_models", visible.armorModels); sample.addProperty("clothing_pairs", visible.clothingPairs);
                sample.addProperty("shared_model_isolated", true); sample.addProperty("fa_resumed", true);
                sample.add("idle_pose", jsonPose(idle.finalPose)); sample.add("rendered_pose", jsonPose(visible.finalPose));
                row.fact.getAsJsonArray("samples").add(sample);
            }
        }

        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public boolean isPauseScreen() { return true; }
    }

    private static Trace renderEntity(GuiGraphics graphics, LivingEntity entity, float x, float y, float scale) {
        Trace current = new Trace(entity); trace = current;
        try {
            InventoryScreen.renderEntityInInventory(graphics, x, y, scale, new Vector3f(),
                    new Quaternionf().rotationZ((float)Math.PI).rotateY(.35F), null, entity);
        } finally { trace = null; PENDING.clear(); }
        check(current.finalPose != null, "Real LivingEntityRenderer Post event was observed: " + entity.getType());
        check(current.errors.isEmpty(), "Rendering invariant failed for " + entity.getType() + ": " + current.errors);
        return current;
    }

    private static void begin(LivingEntity entity, int age) throws ReflectiveOperationException {
        if (entity instanceof Mob mob) {
            var profile = BetterCombatBridge.profile(mob, 0);
            check(profile != null, "Actual Better Combat weapon attributes resolve: " + entity.getType());
            mob.tickCount = 0;
            BetterCombatMobAnimations.start(mob, profile.animation(), 20, .4F, false);
            mob.tickCount = age;
        } else {
            Class<?> hand = Class.forName("net.bettercombat.logic.AnimatedHand");
            Class.forName(BC_ANIMATABLE).getMethod("playAttackAnimation", String.class, hand, float.class, float.class)
                    .invoke(entity, "bettercombat:one_handed_slash_horizontal_right", hand.getField("MAIN_HAND").get(null), 20F, .4F);
            tickPlayer(entity, age);
        }
    }

    private static void stop(LivingEntity entity) throws ReflectiveOperationException {
        if (entity instanceof Mob mob) BetterCombatMobAnimations.stop(mob);
        else {
            Class.forName(BC_ANIMATABLE).getMethod("stopAttackAnimation", float.class).invoke(entity, 0F);
            tickPlayer(entity, 2);
        }
    }

    private static void tickPlayer(LivingEntity entity, int count) throws ReflectiveOperationException {
        Object stack = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess")
                .getMethod("getPlayerAnimLayer", AbstractClientPlayer.class).invoke(null, entity);
        Method tick = stack.getClass().getMethod("tick");
        for (int index = 0; index < count; index++) tick.invoke(stack);
    }

    private static JsonObject checkPlayerLifecycle(Row row) {
        try {
            stop(row.entity); check(!active(row.entity), "Player begins outside combat: " + row.key);
            begin(row.entity, 3); check(active(row.entity), "Player real BC layer is active: " + row.key);
            Class.forName(BC_ANIMATABLE).getMethod("stopAttackAnimation", float.class).invoke(row.entity, 6F);
            check(active(row.entity), "Cancellation fade retains the combat guard: " + row.key);
            tickPlayer(row.entity, 2);
            check(active(row.entity), "Combat guard remains during unfinished cancellation fade: " + row.key);
            tickPlayer(row.entity, 10);
            check(!active(row.entity), "Combat guard releases after cancellation fade: " + row.key);
            begin(row.entity, 3); check(active(row.entity), "Second real player attack starts: " + row.key);
            tickPlayer(row.entity, 200);
            check(!active(row.entity), "Natural animation stop releases the guard: " + row.key);
            stop(row.entity);
            JsonObject result = new JsonObject(); result.addProperty("player", row.key);
            result.addProperty("cancellation_fade_guarded", true); result.addProperty("fade_end_releases_guard", true);
            result.addProperty("natural_animation_end_releases_guard", true); return result;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static boolean active(LivingEntity entity) {
        try { return Boolean.TRUE.equals(activeMethod.invoke(null, entity)); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static void equip(LivingEntity entity) {
        // EMF's distance LOD uses world positions even when InventoryScreen renders a detached entity.
        // Keep fixtures beside the actual camera, including flat worlds whose ground is below y=0.
        if (entity != Minecraft.getInstance().player) entity.setPos(Minecraft.getInstance().player.position());
        if (humanoid(model(entity))) {
            entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            entity.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            entity.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            entity.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        }
        if (entity instanceof Mob mob) { mob.setNoAi(true); mob.setAggressive(true); }
        entity.tickCount = 40; entity.setYRot(0); entity.yRotO = 0; entity.setXRot(0); entity.xRotO = 0;
        entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = 0;
    }

    private static EntityModel<?> model(LivingEntity entity) {
        return ((LivingEntityRenderer<?, ?>)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity)).getModel();
    }

    private static boolean humanoid(EntityModel<?> model) { return model instanceof HumanoidModel<?> || model instanceof IllagerModel<?>; }

    private static boolean poseDriver(LivingEntity entity) {
        return model(entity).getClass().getSimpleName().equals("SkeletonZombieModel");
    }

    private static Map<ModelPart, PartState> canonical(Object root) throws ReflectiveOperationException {
        Map<ModelPart, PartState> result = new IdentityHashMap<>();
        Map<?, ?> parts = (Map<?, ?>)invoke(root, "getAllVanillaPartsByNameEMF");
        if (!parts.containsKey("body") || !parts.containsKey("right_arm") || !parts.containsKey("left_arm")) return result;
        for (var entry : parts.entrySet()) {
            if (!COMBAT_BONES.contains(entry.getKey())) continue;
            ModelPart part = (ModelPart)entry.getValue(); result.put(part, new PartState(part));
        }
        return result;
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        Method callable = target.getClass().getMethod(method); callable.setAccessible(true); return callable.invoke(target);
    }

    private static boolean sameStates(Map<ModelPart, PartState> first, Map<ModelPart, PartState> second) {
        if (first.size() != second.size()) return false;
        for (var entry : first.entrySet()) if (!entry.getValue().sameTransform(second.get(entry.getKey()))) return false;
        return true;
    }

    private static Map<String, List<Float>> pose(EntityModel<?> model, boolean clothing) {
        Map<String, ModelPart> parts = new LinkedHashMap<>();
        if (model instanceof HumanoidModel<?> humanoid) {
            parts.put("head", humanoid.head); parts.put("body", humanoid.body);
            parts.put("right_arm", humanoid.rightArm); parts.put("left_arm", humanoid.leftArm);
            parts.put("right_leg", humanoid.rightLeg); parts.put("left_leg", humanoid.leftLeg);
        } else if (model instanceof IllagerModel<?> illager) {
            for (String name : List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")) parts.put(name, illager.root().getChild(name));
        } else {
            // Animal models can have private named parts; record every native model part field.
            for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) for (Field field : type.getDeclaredFields())
                if (ModelPart.class.isAssignableFrom(field.getType())) try {
                    field.setAccessible(true); ModelPart part = (ModelPart)field.get(model);
                    if (part != null) parts.put(type.getSimpleName() + "." + field.getName(), part);
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        Map<String, List<Float>> result = new LinkedHashMap<>();
        parts.forEach((name, part) -> result.put(name, new PartState(part).values));
        return result;
    }

    private static boolean samePose(Map<String, List<Float>> first, Map<String, List<Float>> second) {
        if (!first.keySet().equals(second.keySet())) return false;
        for (String name : first.keySet()) if (!near(first.get(name), second.get(name))) return false;
        return true;
    }

    private static boolean near(List<Float> first, List<Float> second) {
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) if (Math.abs(first.get(index) - second.get(index)) > .0001F) return false;
        return true;
    }

    private static JsonObject jsonPose(Map<String, List<Float>> pose) {
        JsonObject object = new JsonObject();
        pose.forEach((name, values) -> { JsonArray vector = new JsonArray(); values.forEach(vector::add); object.add(name, vector); });
        return object;
    }

    private static void fail(Minecraft mc, Throwable failure) {
        if (report == null) { report = new JsonObject(); report.add("failures", new JsonArray()); }
        report.getAsJsonArray("failures").add(failure.toString()); report.addProperty("status", "failed");
        Mite.LOGGER.error("Fresh Animations / Better Combat audit failed", failure); finish(mc);
    }

    private static void finish(Minecraft mc) {
        done = true; trace = null; PENDING.clear(); report.addProperty("safe_client_shutdown_requested", true);
        try {
            Path path = output(); Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        } catch (Exception failure) { Mite.LOGGER.error("Cannot write Fresh Animations combat audit", failure); }
        mc.stop();
    }

    private static Path output() {
        return Path.of(System.getProperty("mite.freshAnimationsCombatAuditOutput", "build/verification/freshanimations-combat-client-audit.json"))
                .toAbsolutePath().normalize();
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private record PartState(List<Float> values) {
        PartState(ModelPart part) { this(List.of(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale)); }
        boolean matches(ModelPart part) { return sameTransform(new PartState(part)); }
        boolean sameTransform(PartState other) { return other != null && near(values, other.values); }
    }

    private record Row(LivingEntity entity, LivingEntity control, String key, boolean requireFa, JsonObject fact) {}
    private record Oracle(Trace owner, boolean active, boolean main, Map<ModelPart, PartState> canonical,
                          Map<ModelPart, PartState> all, Map<String, List<Float>> modelPose) {}

    private static final class Trace {
        final LivingEntity entity;
        final List<String> errors = new ArrayList<>();
        final List<String> rootNames = new ArrayList<>();
        Map<String, List<Float>> expectedFinalCore = Map.of(), finalPose;
        int emfCalls, mainEmfCalls, changedEmfCalls, guardedCalls, armorModels, clothingPairs;
        Trace(LivingEntity entity) { this.entity = entity; }
    }
}
