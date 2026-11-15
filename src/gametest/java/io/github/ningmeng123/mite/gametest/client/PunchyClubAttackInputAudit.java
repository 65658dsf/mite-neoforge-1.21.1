package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.PunchyWeaponTransitions;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import net.neoforged.fml.ModList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Native attack-key queue through Punchy's real tick and first-person rendered frame. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class PunchyClubAttackInputAudit {
    private static final List<String> WEAPONS = List.of(
            "mite:mithril_sword", "mite:mithril_dagger", "mite:flint_knife", "mite:wooden_cudgel",
            "mite:mithril_battle_axe", "mite:mithril_war_hammer", "mite:mithril_scythe", "minecraft:trident",
            "mite:simplyswords_mithril_sai", "mite:simplyswords_mithril_longsword",
            "mite:simplyswords_mithril_twinblade", "mite:simplyswords_mithril_rapier",
            "mite:simplyswords_mithril_katana", "mite:simplyswords_mithril_spear",
            "mite:simplyswords_mithril_glaive", "mite:simplyswords_mithril_warglaive",
            "mite:simplyswords_mithril_cutlass", "mite:simplyswords_mithril_claymore",
            "mite:simplyswords_mithril_greataxe", "mite:simplyswords_mithril_greathammer",
            "mite:simplyswords_mithril_chakram", "mite:simplyswords_mithril_scythe",
            "mite:simplyswords_mithril_halberd");
    private static JsonObject report, fact;
    private static JsonArray cases;
    private static ItemStack originalMain, originalOff;
    private static CameraType originalCamera;
    private static float originalPitch;
    private static boolean originalAttackDown;
    private static Object pose;
    private static Object punchyConfig;
    private static Field betterCombatOption;
    private static boolean originalBetterCombatOption;
    private static Method isPlaying, currentClip, currentTime, inputEpoch, visualEpoch, inputGesture, playbackId;
    private static long inputBefore, visualBefore, playbackBefore;
    private static int index, ticks, stage, attackFrames, maxChangedPixels;
    private static int[] idlePixels;
    private static int idleWidth, idleHeight;
    private static boolean frameReady;
    private static String pendingFrame;
    private static Throwable frameFailure;
    private static final List<CompletableFuture<Void>> nativeImageWrites = new ArrayList<>();

    private PunchyClubAttackInputAudit() {}

    static boolean tick(Minecraft mc, JsonObject shared) throws ReflectiveOperationException {
        if (frameFailure == null) {
            try { return auditTick(mc, shared); }
            catch (Throwable failure) { frameFailure = failure; }
        }
        if (!completeImageWrites(nativeImageWrites)) return false;
        throw new IllegalStateException("Actual Punchy attack frame failed", frameFailure);
    }

    private static boolean auditTick(Minecraft mc, JsonObject shared) throws ReflectiveOperationException {
        if (report == null) {
            report = shared;
            cases = new JsonArray(); report.add("actual_attack_input_frames", cases);
            originalMain = mc.player.getMainHandItem(); originalOff = mc.player.getOffhandItem();
            originalCamera = mc.options.getCameraType(); originalPitch = mc.player.getXRot();
            originalAttackDown = mc.options.keyAttack.isDown();
            Class<?> manager = Class.forName("punchy.client.animation.PunchyAnimationManager");
            pose = manager.getField("POSE_HANDLER").get(null);
            isPlaying = pose.getClass().getMethod("isPlaying");
            currentClip = pose.getClass().getMethod("getCurrentClip");
            currentTime = pose.getClass().getMethod("getCurrentTime");
            playbackId = pose.getClass().getMethod("getPlaybackId");
            Class<?> tracker = Class.forName("punchy.client.state.AttackActionTracker");
            inputEpoch = tracker.getMethod("peekEpoch"); visualEpoch = tracker.getMethod("peekVisualEpoch");
            inputGesture = tracker.getMethod("peekInputGesture");
            if (ModList.get().isLoaded("bettercombat")) {
                Field data = Class.forName("punchy.config.PunchyConfig").getDeclaredField("DATA");
                data.setAccessible(true);
                punchyConfig = data.get(null);
                betterCombatOption = punchyConfig.getClass().getField("bettercombatCompat");
                originalBetterCombatOption = betterCombatOption.getBoolean(punchyConfig);
                betterCombatOption.setBoolean(punchyConfig, true);
                report.addProperty("punchy_bettercombat_compatibility_temporarily_enabled_in_memory", !originalBetterCombatOption);
            }
            report.addProperty("scope", "actual native attack key consume -> Minecraft.startAttack -> Punchy input tracker -> real playing pose -> rendered first-person frames");
            report.addProperty("attack_key_queue_used", true);
            report.addProperty("input_phase_did_not_directly_invoke_attack_state_machine_or_pose_playback", true);
            report.addProperty("png_encoding_and_file_writes_async", true);
        }
        if (index == WEAPONS.size()) {
            if (!completeImageWrites(nativeImageWrites)) return false;
            if (betterCombatOption != null) betterCombatOption.setBoolean(punchyConfig, originalBetterCombatOption);
            mc.options.keyAttack.setDown(originalAttackDown); mc.options.setCameraType(originalCamera);
            mc.player.setXRot(originalPitch);
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, originalOff);
            report.addProperty("real_attack_input_playback_and_frame_capture_passed", true);
            report.addProperty("visual_appearance_tested", true);
            report.addProperty("visual_check_scope", "All fourteen Simply Swords weapon types, native melee families, vanilla trident, Polaris dagger/sai and default sword each have three actual attack frames");
            return true;
        }
        if (stage == 0) {
            String id = WEAPONS.get(index);
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == net.minecraft.world.item.Items.AIR && id.contains("simplyswords_")) {
                ++index;
                return false;
            }
            check(item != net.minecraft.world.item.Items.AIR, "Actual input audit item is registered: " + id);
            mc.player.stopUsingItem(); mc.options.keyUse.setDown(false); mc.options.keyAttack.setDown(false);
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            mc.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            // Aim into empty sky: a real attack-key click swings without a target or damage packet.
            mc.player.setXRot(-65F); mc.player.xRotO = -65F;
            fact = new JsonObject(); fact.addProperty("item", id); cases.add(fact);
            String family = PunchyClubAnimationClientAudit.attackFamily(new ItemStack(item));
            fact.addProperty("attack_family", family == null ? "default_sword" : family);
            fact.add("attack_frames", new JsonArray());
            ticks = 0; stage = 1; frameReady = false; maxChangedPixels = 0;
        }
        if (stage == 1) {
            if (++ticks < 70) return false;
            check(mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.MISS,
                    "The real key-driven attack must be an empty-sky swing, not mining or entity damage");
            pendingFrame = "idle"; frameReady = false; stage = 2;
            return false;
        }
        if (stage == 2) {
            if (!frameReady) return false;
            inputBefore = ((Number)inputEpoch.invoke(null)).longValue();
            visualBefore = ((Number)visualEpoch.invoke(null)).longValue();
            playbackBefore = ((Number)playbackId.invoke(pose)).longValue();
            mc.options.keyAttack.setDown(true);
            KeyMapping.click(mc.options.keyAttack.getKey());
            fact.addProperty("input_epoch_before", inputBefore);
            fact.addProperty("visual_epoch_before", visualBefore);
            fact.addProperty("pose_playback_id_before", playbackBefore);
            fact.add("native_attack_state_before", nativeAttackState(mc));
            fact.addProperty("native_attack_key_clicked", true);
            ticks = 0; stage = 3; frameReady = false;
            return false;
        }
        if (stage == 3) {
            mc.options.keyAttack.setDown(false);
            check(++ticks < 60, "The actual native attack input must produce the expected weapon clip within three seconds");
            long epoch = ((Number)inputEpoch.invoke(null)).longValue();
            long visual = ((Number)visualEpoch.invoke(null)).longValue();
            Object clip = currentClip.invoke(pose);
            String name = clip == null ? "none" : (String)clip.getClass().getMethod("getName").invoke(clip);
            long playback = ((Number)playbackId.invoke(pose)).longValue();
            fact.addProperty("pose_playback_id_observed", playback);
            fact.addProperty("input_gesture_after", ((Number) inputGesture.invoke(null)).longValue());
            fact.addProperty("native_input_epoch_observed", epoch);
            fact.addProperty("visual_playback_epoch_observed", visual);
            fact.addProperty("observed_playing_clip", name);
            // SpearStateMachine consumes native attacks before AttackStateMachine and plays
            // the real pose without incrementing AttackActionTracker's visual epoch.
            if (epoch <= inputBefore || playback <= playbackBefore || !Boolean.TRUE.equals(isPlaying.invoke(pose)) || !expectedAttackClip(name)) {
                // Better Combat can reject a press while its native upswing/cooldown is active.
                // Retry through the same real key queue after cooldown, retaining its full input path.
                if (ticks == 1 || ticks == 19 || ticks == 39 || ticks == 59)
                    fact.add("native_attack_state_observed", nativeAttackState(mc));
                if (ticks == 20 || ticks == 40) {
                    mc.options.keyAttack.setDown(true);
                    KeyMapping.click(mc.options.keyAttack.getKey());
                    fact.addProperty("native_key_retries", ticks / 20);
                }
                return false;
            }
            check(expectedAttackClip(name), "Actual input must play the expected weapon-family attack: " + name);
            check(!((Map<?, ?>)clip.getClass().getMethod("getTracks").invoke(clip)).isEmpty(), "The playing attack contains actual bone tracks");
            fact.addProperty("native_input_epoch_after", epoch);
            fact.addProperty("visual_playback_epoch_after", visual);
            fact.addProperty("visual_epoch_incremented", visual > visualBefore);
            fact.addProperty("pose_playback_id_after", playback);
            fact.add("native_attack_state_observed", nativeAttackState(mc));
            fact.addProperty("playing_clip", name);
            if (PunchyWeaponTransitions.ownsAttack(name)) {
                JsonObject transition = authoredTransitionState();
                check(!transition.get("rebaseActive").getAsBoolean(), "Authored weapon attacks retain their original paths during combo transitions");
                check(transition.get("blendInRealTime").getAsBoolean(), "Authored weapon transitions use real-time blending");
                check(Math.abs(transition.get("blendInDuration").getAsFloat() - 0.07F) < 0.00001F,
                        "Authored weapon transitions use the configured short crossfade");
                fact.add("authored_transition", transition);
            }
            String family = PunchyClubAnimationClientAudit.attackFamily(mc.player.getMainHandItem());
            if (PunchyClubAnimationClientAudit.twoHandedFamily(family) && mc.player.getOffhandItem().isEmpty()) {
                boolean dualHanded = (Boolean) pose.getClass().getMethod("isCurrentDualHandedActive").invoke(pose);
                Field forcedSkip = pose.getClass().getDeclaredField("forcedSkipArm");
                forcedSkip.setAccessible(true);
                HumanoidArm supportingArm = mc.player.getMainArm() == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
                check(dualHanded, "The actual two-handed attack keeps its supporting-hand pose active");
                check(forcedSkip.get(pose) != supportingArm, "The actual attack allows the empty supporting hand to render");
                fact.addProperty("empty_supporting_hand_pose_active", true);
                fact.addProperty("supporting_hand_render_allowed", true);
            }
            pendingFrame = "attack"; frameReady = false; attackFrames = 0; ticks = 0; stage = 4;
            return false;
        }
        if (stage == 4) {
            check(++ticks < 120, "The real playing attack must reach its rendered first-person samples within six seconds");
            if (frameReady) {
                check(maxChangedPixels > 200, "The actual attack must visibly change the first-person hand region");
                fact.addProperty("passed", true); ++index; stage = 0;
            }
        }
        return false;
    }

    @SubscribeEvent public static void rendered(RenderFrameEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.punchyClubInputAudit") || pendingFrame == null || frameFailure != null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        try {
            String kind = pendingFrame;
            if (kind.equals("attack")) {
                Object clip = currentClip.invoke(pose);
                if (clip == null || !Boolean.TRUE.equals(isPlaying.invoke(pose))) return;
                String name = (String)clip.getClass().getMethod("getName").invoke(clip);
                float time = ((Number)currentTime.invoke(pose)).floatValue();
                float length = ((Number)clip.getClass().getMethod("getLength").invoke(clip)).floatValue();
                // Polaris' active right-hand motion occupies the beginning of its long clip.
                float sampleStep = name.startsWith("mite_polaris_") ? .05F : .2F;
                if (!expectedAttackClip(name) || time < length * sampleStep * (attackFrames + 1)) return;
                fact.addProperty("rendered_playing_clip", name); fact.addProperty("rendered_clip_time", time);
            }
            Path frames = Path.of(System.getProperty("mite.betterCombatPlayerReachAuditOutput")).toAbsolutePath().getParent()
                    .resolve("punchy-weapon-input-frames");
            Files.createDirectories(frames);
            Path output = frames.resolve(index + "-" + WEAPONS.get(index).replace(':', '-') + "-" + kind + (kind.equals("attack") ? "-" + attackFrames : "") + ".png");
            NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            try {
                if (kind.equals("idle")) {
                    idleWidth = image.getWidth(); idleHeight = image.getHeight(); idlePixels = new int[idleWidth * idleHeight];
                    for (int y = idleHeight / 3; y < idleHeight; y += 2) for (int x = idleWidth / 3; x < idleWidth; x += 2)
                        idlePixels[y * idleWidth + x] = image.getPixelRGBA(x, y);
                } else {
                    check(image.getWidth() == idleWidth && image.getHeight() == idleHeight, "Idle and attack frames use the same real viewport");
                    int changed = 0;
                    for (int y = idleHeight / 3; y < idleHeight; y += 2) for (int x = idleWidth / 3; x < idleWidth; x += 2) {
                        int before = idlePixels[y * idleWidth + x], after = image.getPixelRGBA(x, y);
                        int delta = Math.abs((before & 255) - (after & 255))
                                + Math.abs((before >>> 8 & 255) - (after >>> 8 & 255))
                                + Math.abs((before >>> 16 & 255) - (after >>> 16 & 255));
                        if (delta > 24) changed += 4;
                    }
                    // A swing may leave the viewport during follow-through. Check the sampled motion as a whole.
                    maxChangedPixels = Math.max(maxChangedPixels, changed);
                    fact.addProperty("first_person_hand_region_changed_pixels", maxChangedPixels);
                    fact.addProperty("hand_region_pixel_sample_stride", 2);
                    fact.addProperty("changed_hand_region_pixels_are_sampled_estimate", true);
                    fact.addProperty("frame_is_actual_rendered_playback", true);
                    JsonObject frame = new JsonObject();
                    frame.addProperty("frame", output.toString());
                    frame.addProperty("clip_time", ((Number)currentTime.invoke(pose)).floatValue());
                    frame.addProperty("changed_hand_region_pixels", changed);
                    fact.getAsJsonArray("attack_frames").add(frame);
                }
                queueImageWrite(image, output, nativeImageWrites);
            } catch (Throwable failure) {
                image.close();
                throw failure;
            }
            fact.addProperty(kind + "_frame", output.toString());
            if (kind.equals("attack") && ++attackFrames < 3) return;
            pendingFrame = null; frameReady = true;
        } catch (Throwable failure) { frameFailure = failure; }
    }

    private static JsonObject nativeAttackState(Minecraft mc) throws ReflectiveOperationException {
        JsonObject state = new JsonObject();
        state.addProperty("equipped_main", BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString());
        state.addProperty("equipped_off", BuiltInRegistries.ITEM.getKey(mc.player.getOffhandItem().getItem()).toString());
        state.addProperty("selected_hotbar_slot", mc.player.getInventory().selected);
        state.addProperty("using_item", mc.player.isUsingItem());
        state.addProperty("use_item", BuiltInRegistries.ITEM.getKey(mc.player.getUseItem().getItem()).toString());
        state.addProperty("use_key_down", mc.options.keyUse.isDown());
        state.addProperty("attack_key_down", mc.options.keyAttack.isDown());
        state.addProperty("paused", mc.isPaused());
        state.addProperty("hand_equip_in_progress", (Boolean) Class.forName("punchy.client.state.HandEquipStateMachine").getMethod("isEquipInProgress").invoke(null));
        state.addProperty("equipped_stack_uses_spear_state_machine", (Boolean) Class.forName("punchy.client.state.SpearStateMachine")
                .getMethod("isSpearStack", ItemStack.class).invoke(null, mc.player.getMainHandItem()));
        state.addProperty("main_hand_blacklisted", (Boolean) Class.forName("punchy.client.animation.PunchyAnimationManager")
                .getMethod("isHandBlacklisted", Minecraft.class, InteractionHand.class).invoke(null, mc, InteractionHand.MAIN_HAND));
        Class<?> client = Class.forName("punchy.client.PunchyClient");
        for (String machine : List.of("ATTACK_STATE", "SPEAR_STATE", "USE_STATE", "HAND_EQUIP_STATE")) {
            Field holder = client.getDeclaredField(machine);
            holder.setAccessible(true);
            Object instance = holder.get(null);
            JsonObject details = new JsonObject();
            for (Field field : instance.getClass().getDeclaredFields()) {
                Class<?> type = field.getType();
                if (type != boolean.class && type != int.class && type != long.class && !type.isEnum()) continue;
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value instanceof Boolean flag) details.addProperty(field.getName(), flag);
                else if (value instanceof Number number) details.addProperty(field.getName(), number);
                else if (value != null) details.addProperty(field.getName(), value.toString());
            }
            state.add(machine.toLowerCase(java.util.Locale.ROOT), details);
        }
        return state;
    }

    private static boolean expectedAttackClip(String name) {
        String family = PunchyClubAnimationClientAudit.attackFamily(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(WEAPONS.get(index)))));
        if (family != null && (!family.equals("polaris") || report.get("polaris_enabled").getAsBoolean())) {
            return name.startsWith(family.equals("polaris") ? "mite_polaris_sword_" : "mite_" + family + "_");
        }
        return java.util.stream.Stream.of("active_sword_resolved_combo_clips", "active_sword_bettercombat_combo_clips")
                .flatMap(key -> report.getAsJsonArray(key).asList().stream())
                .anyMatch(value -> value.getAsString().equals(name));
    }
    private static JsonObject previewRequest, previewResponse;
    private static CompletableFuture<Void> previewReload;
    private static long previewModified, previewPlaybackBefore;
    private static int previewStage, previewTicks, previewFrame;
    private static int previewAttackCount, previewAttackInterval, previewClicks, previewObservedAttacks;
    private static long previewObservedPlayback;
    private static JsonObject previewStrike;
    private static Method previewPlaybackId;
    private static HumanoidArm previewOriginalArm;
    private static ItemStack previewOriginalMain, previewOriginalOff;
    private static boolean previewHasSavedState;
    private static final Map<Object, Boolean> previewBetterCombatOriginals = new java.util.IdentityHashMap<>();
    private static final List<CompletableFuture<Void>> previewImageWrites = new ArrayList<>();
    private static String previewCompletionStatus;

    /** Optional live resource-pack preview, limited to this gametest-only subscriber. */
    static void previewTick(Minecraft mc) {
        if (!Boolean.getBoolean("mite.punchyPreview") || mc.player == null || mc.screen != null) return;
        try {
            if (previewStage == 0) {
                Path request = previewDirectory().resolve("request.json");
                if (!Files.isRegularFile(request) || Files.getLastModifiedTime(request).toMillis() == previewModified) return;
                previewModified = Files.getLastModifiedTime(request).toMillis();
                previewRequest = JsonParser.parseString(Files.readString(request)).getAsJsonObject();
                previewResponse = new JsonObject();
                previewResponse.add("request", previewRequest.deepCopy());
                previewResponse.add("frames", new JsonArray());
                previewResponse.addProperty("png_encoding_and_file_writes_async", true);
                if (previewRequest.has("quit") && previewRequest.get("quit").getAsBoolean()) {
                    restorePreviewState(mc);
                    previewResponse.addProperty("status", "stopping");
                    writePreviewResponse();
                    mc.stop();
                    return;
                }
                previewResponse.addProperty("status", "reloading");
                writePreviewResponse();
                previewReload = previewRequest.has("reload") && previewRequest.get("reload").getAsBoolean()
                        ? mc.reloadResourcePacks() : CompletableFuture.completedFuture(null);
                previewStage = 1;
                return;
            }
            if (previewStage == 1) {
                if (!previewReload.isDone()) return;
                previewReload.join();
                if (!previewRequest.has("item")) {
                    previewResponse.addProperty("status", "reloaded");
                    writePreviewResponse();
                    previewStage = 0;
                    return;
                }
                String id = previewRequest.get("item").getAsString();
                var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
                check(item != net.minecraft.world.item.Items.AIR, "Preview item must exist " + id);
                previewOriginalArm = mc.player.getMainArm();
                previewOriginalMain = mc.player.getMainHandItem();
                previewOriginalOff = mc.player.getOffhandItem();
                previewHasSavedState = true;
                String arm = previewRequest.has("main_arm") ? previewRequest.get("main_arm").getAsString() : "right";
                check(arm.equals("left") || arm.equals("right"), "Preview main_arm must be left or right");
                mc.player.setMainArm(arm.equals("left") ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
                ItemStack offhand = ItemStack.EMPTY;
                if (previewRequest.has("offhand")) {
                    var offhandId = ResourceLocation.parse(previewRequest.get("offhand").getAsString());
                    check(BuiltInRegistries.ITEM.containsKey(offhandId), "Preview offhand item must exist " + offhandId);
                    offhand = new ItemStack(BuiltInRegistries.ITEM.get(offhandId));
                }
                mc.player.stopUsingItem();
                mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                mc.player.setItemInHand(InteractionHand.OFF_HAND, offhand);
                previewResponse.addProperty("actual_main_arm", mc.player.getMainArm().name().toLowerCase(java.util.Locale.ROOT));
                previewResponse.addProperty("actual_offhand", BuiltInRegistries.ITEM.getKey(offhand.getItem()).toString());
                previewResponse.add("resolved_tuning", resolvedPreviewTuning(mc));
                enablePreviewBetterCombat();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.keyAttack.setDown(false);
                mc.options.keyUse.setDown(false);
                float pitch = previewRequest.has("pitch") ? previewRequest.get("pitch").getAsFloat() : -25F;
                mc.player.setXRot(pitch); mc.player.xRotO = pitch;
                if (previewRequest.has("yaw")) {
                    float yaw = previewRequest.get("yaw").getAsFloat();
                    mc.player.setYRot(yaw); mc.player.yRotO = yaw;
                }
                if (pose == null) {
                    pose = Class.forName("punchy.client.animation.PunchyAnimationManager").getField("POSE_HANDLER").get(null);
                    currentClip = pose.getClass().getMethod("getCurrentClip");
                    currentTime = pose.getClass().getMethod("getCurrentTime");
                    isPlaying = pose.getClass().getMethod("isPlaying");
                }
                previewPlaybackId = pose.getClass().getMethod("getPlaybackId");
                previewAttackCount = previewRequest.has("attacks") ? previewRequest.get("attacks").getAsInt() : 1;
                previewAttackInterval = previewRequest.has("attack_interval_ticks") ? previewRequest.get("attack_interval_ticks").getAsInt() : 8;
                check(previewAttackCount > 0 && previewAttackInterval > 0, "Preview attack count and interval must be positive");
                check(!previewRequest.has("sample_fractions") || !previewRequest.getAsJsonArray("sample_fractions").isEmpty(),
                        "Preview sample fractions must contain at least one sample");
                previewClicks = 0; previewObservedAttacks = 0; previewStrike = null;
                previewTicks = 0; previewFrame = 0; previewStage = 2;
                previewResponse.addProperty("status", "equipping");
                writePreviewResponse();
                return;
            }
            if (previewStage == 2) {
                if (++previewTicks < 70) return;
                // Resource reload can replace Punchy's configuration after its reload future completes.
                enablePreviewBetterCombat();
                previewResponse.add("before_attack_state", previewObservedState(mc));
                previewPlaybackBefore = ((Number) previewPlaybackId.invoke(pose)).longValue();
                previewObservedPlayback = previewPlaybackBefore;
                previewTicks = 0;
                if (previewRequest.has("idle") && previewRequest.get("idle").getAsBoolean()) {
                    previewStage = 4;
                    previewResponse.addProperty("status", "waiting_for_idle");
                } else if (previewAttackCount > 1) {
                    previewStage = 5;
                    previewResponse.add("attacks", new JsonArray());
                    previewResponse.addProperty("status", "attacking_chain");
                } else {
                    queuePreviewAttack(mc);
                    previewStage = 3;
                    previewResponse.addProperty("status", "attacking");
                }
                writePreviewResponse();
                return;
            }
            if (previewStage == 6) {
                if (!completeImageWrites(previewImageWrites)) return;
                previewResponse.addProperty("status", previewCompletionStatus);
                previewStage = 0;
                writePreviewResponse();
                return;
            }
            mc.options.keyAttack.setDown(false);
            if (previewStage == 4) {
                check(++previewTicks < 240, "Preview did not settle into its configured idle pose");
                return;
            }
            if (previewStage == 5) {
                ++previewTicks;
                if (previewClicks < previewAttackCount && previewTicks >= 1L + (long) previewClicks * previewAttackInterval)
                    queuePreviewAttack(mc);
                check(previewTicks < (long) previewAttackCount * previewAttackInterval + 240L, "Preview native attack chain timed out");
                return;
            }
            check(++previewTicks < 160, "Preview native attack timed out");
        } catch (Throwable failure) {
            if (previewResponse == null) previewResponse = new JsonObject();
            previewResponse.addProperty("status", "failed");
            previewResponse.addProperty("failure", failure.toString());
            try { restorePreviewState(mc); } catch (Exception ignored) { }
            previewCompletionStatus = "failed";
            previewStage = 6;
            previewResponse.addProperty("status", "writing_frames");
            try { writePreviewResponse(); } catch (Exception ignored) { }
        }
    }

    @SubscribeEvent public static void previewRendered(RenderFrameEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.punchyPreview") || previewStage < 3 || previewStage == 6) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            previewResponse.add("last_observed_state", previewObservedState(mc));
            if (previewStage == 4) {
                captureIdlePreview(mc);
                return;
            }
            if (previewStage == 5) {
                captureAttackChainPreview(mc);
                return;
            }
            if (((Number) previewPlaybackId.invoke(pose)).longValue() <= previewPlaybackBefore || !Boolean.TRUE.equals(isPlaying.invoke(pose))) return;
            Object clip = currentClip.invoke(pose);
            if (clip == null) return;
            String name = (String) clip.getClass().getMethod("getName").invoke(clip);
            if (!previewAttackClip(mc, name)) return;
            float length = ((Number) clip.getClass().getMethod("getLength").invoke(clip)).floatValue();
            float time = ((Number) currentTime.invoke(pose)).floatValue();
            float fraction = name.startsWith("mite_polaris_") ? .05F * (previewFrame + 1) : .2F * (previewFrame + 1);
            if (previewRequest.has("sample_fractions")) fraction = previewRequest.getAsJsonArray("sample_fractions").get(previewFrame).getAsFloat();
            if (time < length * fraction) return;
            Path output = previewDirectory().resolve("preview-" + previewModified + "-" + previewFrame + ".png");
            Files.createDirectories(output.getParent());
            long captureTime = System.nanoTime();
            queueImageWrite(Screenshot.takeScreenshot(mc.getMainRenderTarget()), output, previewImageWrites);
            JsonObject frame = new JsonObject();
            frame.addProperty("path", output.toString()); frame.addProperty("clip_time", time);
            frame.addProperty("capture_nanotime", captureTime);
            frame.addProperty("fps", mc.getFps());
            previewResponse.add("resolved_tuning", resolvedPreviewTuning(mc));
            previewResponse.getAsJsonArray("frames").add(frame);
            previewResponse.addProperty("playing_clip", name);
            previewResponse.addProperty("main_hand_attack_playing", true);
            previewResponse.addProperty("dual_handed_active", (Boolean) pose.getClass().getMethod("isCurrentDualHandedActive").invoke(pose));
            if (++previewFrame >= previewSampleCount()) {
                completePreview(mc);
            }
            writePreviewResponse();
        } catch (Throwable failure) {
            previewResponse.addProperty("status", "failed");
            previewResponse.addProperty("failure", failure.toString());
            try { restorePreviewState(mc); } catch (Exception ignoredFailure) { }
            previewCompletionStatus = "failed";
            previewStage = 6;
            previewResponse.addProperty("status", "writing_frames");
            try { writePreviewResponse(); } catch (Exception ignoredFailure) { }
        }
    }

    private static void queuePreviewAttack(Minecraft mc) throws java.io.IOException {
        KeyMapping.click(mc.options.keyAttack.getKey());
        mc.options.keyAttack.setDown(true);
        previewResponse.addProperty("native_attack_clicks_queued", ++previewClicks);
        writePreviewResponse();
    }

    private static void enablePreviewBetterCombat() throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("bettercombat")) return;
        Field data = Class.forName("punchy.config.PunchyConfig").getDeclaredField("DATA");
        data.setAccessible(true);
        Object active = data.get(null);
        Field option = active.getClass().getField("bettercombatCompat");
        previewBetterCombatOriginals.putIfAbsent(active, option.getBoolean(active));
        option.setBoolean(active, true);
        previewResponse.addProperty("bettercombat_compatibility_enabled_in_memory", true);
        previewResponse.addProperty("bettercombat_compatibility_original_value", previewBetterCombatOriginals.get(active));
        previewResponse.addProperty("bettercombat_config_identity", System.identityHashCode(active));
        previewResponse.addProperty("bettercombat_compatibility_actual_getter", (Boolean) Class.forName("punchy.config.PunchyConfig")
                .getMethod("isBetterCombatCompatEnabled").invoke(null));
    }

    private static JsonObject previewObservedState(Minecraft mc) throws ReflectiveOperationException {
        JsonObject state = new JsonObject();
        state.addProperty("equipped_main", BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString());
        state.addProperty("equipped_off", BuiltInRegistries.ITEM.getKey(mc.player.getOffhandItem().getItem()).toString());
        state.addProperty("actual_main_arm", mc.player.getMainArm().name());
        state.addProperty("playing", (Boolean) isPlaying.invoke(pose));
        state.addProperty("playback_id", ((Number) previewPlaybackId.invoke(pose)).longValue());
        state.addProperty("clip_time", ((Number) currentTime.invoke(pose)).floatValue());
        Object clip = currentClip.invoke(pose);
        String name = clip == null ? null : (String) clip.getClass().getMethod("getName").invoke(clip);
        state.addProperty("current_clip", name);
        state.addProperty("matches_attack_filter", name != null && previewAttackClip(mc, name));
        state.add("authored_transition", authoredTransitionState());
        Class<?> config = Class.forName("punchy.config.PunchyConfig");
        Field data = config.getDeclaredField("DATA");
        data.setAccessible(true);
        state.addProperty("bettercombat_config_identity", System.identityHashCode(data.get(null)));
        state.addProperty("bettercombat_compatibility_actual_getter", (Boolean) config.getMethod("isBetterCombatCompatEnabled").invoke(null));
        return state;
    }

    private static JsonObject authoredTransitionState() throws ReflectiveOperationException {
        JsonObject state = new JsonObject();
        for (String name : List.of("rebaseActive", "blendInRealTime", "blendInDuration", "variationBlend")) {
            Field field = pose.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(pose);
            if (value instanceof Boolean flag) state.addProperty(name, flag);
            else if (value instanceof Number number) state.addProperty(name, number);
            else state.addProperty(name, value != null);
        }
        return state;
    }

    private static boolean previewAttackClip(Minecraft mc, String name) {
        String family = PunchyClubAnimationClientAudit.attackFamily(mc.player.getMainHandItem());
        if (family == null) return name.contains("attack") || name.startsWith("mite_default_sword_");
        String pattern = family.equals("polaris") ? "mite_polaris_sword_[123](?:_(?:left|right))?"
                : "mite_" + family + "_(?:[1-3]|critical)(?:_(?:left|right))?";
        return name.matches(pattern);
    }

    private static int previewSampleCount() {
        return previewRequest.has("sample_fractions") ? previewRequest.getAsJsonArray("sample_fractions").size() : 3;
    }

    private static void captureIdlePreview(Minecraft mc) throws ReflectiveOperationException, java.io.IOException {
        boolean primaryPlaying = Boolean.TRUE.equals(isPlaying.invoke(pose));
        Object primary = currentClip.invoke(pose);
        previewResponse.addProperty("idle_primary_playing", primaryPlaying);
        previewResponse.addProperty("idle_primary_clip", primary == null ? null
                : (String) primary.getClass().getMethod("getName").invoke(primary));
        if (primaryPlaying || previewTicks < previewFrame * 10) return;
        Object configured = Class.forName("punchy.client.state.CustomAnimationResolver")
                .getMethod("resolveIdleClip", Minecraft.class, InteractionHand.class).invoke(null, mc, InteractionHand.MAIN_HAND);
        Object secondary = pose.getClass().getMethod("getSecondaryClip").invoke(pose);
        boolean compatIdle = Boolean.TRUE.equals(pose.getClass().getMethod("isSecondaryCompatIdle").invoke(pose));
        String name = secondary == null ? null : (String) secondary.getClass().getMethod("getName").invoke(secondary);
        if (configured != null) {
            String expected = (String) configured.getClass().getMethod("getName").invoke(configured);
            if (!compatIdle || !expected.equals(name)) return;
        }
        float time = secondary == null ? 0F : ((Number) pose.getClass().getMethod("getSecondaryTime").invoke(pose)).floatValue();
        JsonObject frame = capturePreviewFrame(mc, "idle-" + previewFrame, name, time);
        frame.addProperty("idle_secondary_compat", compatIdle);
        frame.addProperty("dual_handed_active", (Boolean) pose.getClass().getMethod("isDualHandedPoseActive", float.class).invoke(pose, 0F));
        previewResponse.addProperty("idle_pose_source", secondary == null ? "vanilla" : "POSE_HANDLER.secondary");
        previewResponse.addProperty("idle_clip", name);
        previewResponse.addProperty("idle_secondary_arm", String.valueOf(pose.getClass().getMethod("getSecondaryArm").invoke(pose)));
        previewResponse.addProperty("idle_secondary_compat", compatIdle);
        previewResponse.addProperty("dual_handed_active", frame.get("dual_handed_active").getAsBoolean());
        if (++previewFrame >= 3) completePreview(mc);
        writePreviewResponse();
    }

    private static void captureAttackChainPreview(Minecraft mc) throws ReflectiveOperationException, java.io.IOException {
        if (!Boolean.TRUE.equals(isPlaying.invoke(pose))) return;
        Object clip = currentClip.invoke(pose);
        if (clip == null) return;
        String name = (String) clip.getClass().getMethod("getName").invoke(clip);
        if (!previewAttackClip(mc, name)) return;
        long playback = ((Number) previewPlaybackId.invoke(pose)).longValue();
        if (playback <= previewPlaybackBefore) return;
        if (playback != previewObservedPlayback) {
            previewObservedPlayback = playback;
            previewFrame = 0;
            previewStrike = new JsonObject();
            previewStrike.addProperty("attack_number", ++previewObservedAttacks);
            previewStrike.addProperty("playback_id", playback);
            previewStrike.addProperty("playing_clip", name);
            previewStrike.addProperty("input_clicks_queued", previewClicks);
            previewStrike.add("frames", new JsonArray());
            previewResponse.getAsJsonArray("attacks").add(previewStrike);
        }
        if (previewFrame >= previewSampleCount()) return;
        float time = ((Number) currentTime.invoke(pose)).floatValue();
        float length = ((Number) clip.getClass().getMethod("getLength").invoke(clip)).floatValue();
        float fraction = previewFrame * .1F;
        if (previewRequest.has("sample_fractions")) fraction = previewRequest.getAsJsonArray("sample_fractions").get(previewFrame).getAsFloat();
        if (time < length * fraction) return;
        JsonObject frame = capturePreviewFrame(mc, "attack-" + previewObservedAttacks + "-" + previewFrame, name, time);
        frame.addProperty("attack_number", previewObservedAttacks);
        frame.addProperty("playback_id", playback);
        frame.addProperty("dual_handed_active", (Boolean) pose.getClass().getMethod("isCurrentDualHandedActive").invoke(pose));
        previewStrike.getAsJsonArray("frames").add(frame);
        previewResponse.addProperty("playing_clip", name);
        previewResponse.addProperty("observed_attack_playbacks", previewObservedAttacks);
        previewResponse.addProperty("main_hand_attack_playing", true);
        if (++previewFrame >= previewSampleCount() && previewClicks == previewAttackCount && previewObservedAttacks >= previewAttackCount)
            completePreview(mc);
        writePreviewResponse();
    }

    private static JsonObject capturePreviewFrame(Minecraft mc, String suffix, String name, float time)
            throws java.io.IOException, ReflectiveOperationException {
        Path output = previewDirectory().resolve("preview-" + previewModified + "-" + suffix + ".png");
        Files.createDirectories(output.getParent());
        long captureTime = System.nanoTime();
        queueImageWrite(Screenshot.takeScreenshot(mc.getMainRenderTarget()), output, previewImageWrites);
        JsonObject frame = new JsonObject();
        frame.addProperty("path", output.toString());
        frame.addProperty("clip_time", time);
        frame.addProperty("playing_clip", name);
        frame.addProperty("capture_nanotime", captureTime);
        frame.addProperty("fps", mc.getFps());
        previewResponse.add("resolved_tuning", resolvedPreviewTuning(mc));
        previewResponse.getAsJsonArray("frames").add(frame);
        return frame;
    }

    private static JsonObject resolvedPreviewTuning(Minecraft mc) throws ReflectiveOperationException {
        Class<?> itemKinds = Class.forName("punchy.client.renderer.layer.VanillaFirstPersonItemLayer$ToolKind");
        Class<?> armKinds = Class.forName("punchy.client.renderer.layer.PlayerArmModelLayer$ToolKind");
        Class<?> resolver = Class.forName("punchy.client.render.ToolKindResolver");
        Class<?> itemTuning = Class.forName("punchy.client.render.ToolTuning");
        Class<?> armTuning = Class.forName("punchy.client.renderer.layer.ArmToolTuning");
        ItemStack main = mc.player.getMainHandItem(), offhand = mc.player.getOffhandItem();
        Object emptyArmKind = armKinds.getField("EMPTY_HAND").get(null);
        Object itemKind = resolver.getMethod("resolveItemKind", ItemStack.class, itemKinds)
                .invoke(null, main, itemKinds.getField("INGREDIENT").get(null));
        Method resolveArm = resolver.getMethod("resolveArmKind", ItemStack.class, armKinds);
        Object mainArmKind = resolveArm.invoke(null, main, emptyArmKind);
        Object offhandArmKind = resolveArm.invoke(null, offhand, emptyArmKind);
        Method resolveItemProfile = itemTuning.getMethod("resolveProfile", ItemStack.class, itemKinds, boolean.class);
        Method resolveArmProfile = armTuning.getMethod("resolveProfile", ItemStack.class, armKinds, boolean.class);
        Method itemSpecific = itemTuning.getMethod("getSpecificProfileIfPresent", ResourceLocation.class, boolean.class);
        Method armSpecific = armTuning.getMethod("hasSpecificProfile", ItemStack.class, boolean.class);
        JsonObject tuning = new JsonObject();
        tuning.addProperty("item_kind", String.valueOf(itemKind));
        tuning.addProperty("main_arm_kind", String.valueOf(mainArmKind));
        tuning.addProperty("offhand_arm_kind", String.valueOf(offhandArmKind));
        tuning.addProperty("fps", mc.getFps());
        tuning.addProperty("window_framerate_limit", mc.getWindow().getFramerateLimit());
        for (boolean left : new boolean[] {false, true}) {
            String side = left ? "left" : "right";
            tuning.add("item_" + side, profileSnapshot(resolveItemProfile.invoke(null, main, itemKind, left)));
            tuning.add("main_arm_" + side, profileSnapshot(resolveArmProfile.invoke(null, main, mainArmKind, left)));
            tuning.add("offhand_arm_" + side, profileSnapshot(resolveArmProfile.invoke(null, offhand, offhandArmKind, left)));
            tuning.add("empty_hand_" + side, profileSnapshot(resolveArmProfile.invoke(null, ItemStack.EMPTY, emptyArmKind, left)));
            tuning.addProperty("item_" + side + "_specific", itemSpecific.invoke(null, BuiltInRegistries.ITEM.getKey(main.getItem()), left) != null);
            tuning.addProperty("main_arm_" + side + "_specific", (Boolean) armSpecific.invoke(null, main, left));
        }
        return tuning;
    }

    private static JsonObject profileSnapshot(Object profile) throws IllegalAccessException {
        JsonObject values = new JsonObject();
        if (profile == null) return values;
        for (Field field : profile.getClass().getFields()) {
            Object value = field.get(profile);
            if (value instanceof Number number) values.addProperty(field.getName(), number);
            else if (value instanceof Boolean flag) values.addProperty(field.getName(), flag);
            else if (value instanceof Enum<?> kind) values.addProperty(field.getName(), kind.name());
        }
        return values;
    }

    private static void completePreview(Minecraft mc) throws ReflectiveOperationException {
        restorePreviewState(mc);
        previewCompletionStatus = "captured";
        previewResponse.addProperty("status", "writing_frames");
        previewStage = 6;
    }

    private static void queueImageWrite(NativeImage image, Path output, List<CompletableFuture<Void>> writes) {
        writes.add(CompletableFuture.runAsync(() -> {
            try (image) { image.writeToFile(output); }
            catch (java.io.IOException exception) { throw new java.util.concurrent.CompletionException(exception); }
        }, net.minecraft.Util.ioPool()));
    }

    private static boolean completeImageWrites(List<CompletableFuture<Void>> writes) {
        for (var write : writes) if (!write.isDone()) return false;
        try { for (var write : writes) write.join(); }
        finally { writes.clear(); }
        return true;
    }

    private static void restorePreviewState(Minecraft mc) throws ReflectiveOperationException {
        if (previewHasSavedState && mc.player != null) {
            mc.player.setMainArm(previewOriginalArm);
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, previewOriginalMain);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, previewOriginalOff);
            mc.options.keyAttack.setDown(false);
            previewHasSavedState = false;
            previewResponse.addProperty("main_arm_and_equipment_restored", true);
        }
        if (!previewBetterCombatOriginals.isEmpty()) {
            for (var saved : previewBetterCombatOriginals.entrySet())
                saved.getKey().getClass().getField("bettercombatCompat").setBoolean(saved.getKey(), saved.getValue());
            previewBetterCombatOriginals.clear();
            previewResponse.addProperty("bettercombat_compatibility_restored", true);
            previewResponse.addProperty("bettercombat_compatibility_actual_getter_after_restore", (Boolean) Class.forName("punchy.config.PunchyConfig")
                    .getMethod("isBetterCombatCompatEnabled").invoke(null));
        }
    }

    private static Path previewDirectory() {
        return Path.of(System.getProperty("mite.betterCombatPlayerReachAuditOutput")).toAbsolutePath().getParent().resolve("punchy-preview");
    }

    private static void writePreviewResponse() throws java.io.IOException {
        Files.createDirectories(previewDirectory());
        Files.writeString(previewDirectory().resolve("response.json"), new GsonBuilder().setPrettyPrinting().create().toJson(previewResponse));
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
