package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import java.lang.reflect.Method;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Native attack-key queue through Punchy's real tick and first-person rendered frame. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class PunchyClubAttackInputAudit {
    private static final List<String> WEAPONS = List.of("mite:mithril_sword", "modern_ite:club_mithril");
    private static JsonObject report, fact;
    private static JsonArray cases;
    private static ItemStack originalMain, originalOff;
    private static CameraType originalCamera;
    private static float originalPitch;
    private static boolean originalAttackDown;
    private static Object pose;
    private static Method isPlaying, currentClip, currentTime, inputEpoch, visualEpoch;
    private static long inputBefore, visualBefore;
    private static int index, ticks, stage;
    private static int[] idlePixels;
    private static int idleWidth, idleHeight;
    private static boolean frameReady;
    private static String pendingFrame;
    private static Throwable frameFailure;

    private PunchyClubAttackInputAudit() {}

    static boolean tick(Minecraft mc, JsonObject shared) throws ReflectiveOperationException {
        if (frameFailure != null) throw new IllegalStateException("Actual Punchy attack frame failed", frameFailure);
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
            Class<?> tracker = Class.forName("punchy.client.state.AttackActionTracker");
            inputEpoch = tracker.getMethod("peekEpoch"); visualEpoch = tracker.getMethod("peekVisualEpoch");
            report.addProperty("scope", "actual native attack key consume -> Minecraft.startAttack -> Punchy input tracker -> real playing pose -> rendered first-person frames");
            report.addProperty("attack_key_queue_used", true);
            report.addProperty("input_phase_did_not_directly_invoke_attack_state_machine_or_pose_playback", true);
        }
        if (index == WEAPONS.size()) {
            mc.options.keyAttack.setDown(originalAttackDown); mc.options.setCameraType(originalCamera);
            mc.player.setXRot(originalPitch);
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, originalOff);
            report.addProperty("real_attack_input_playback_and_frame_capture_passed", true);
            report.addProperty("visual_appearance_tested", true);
            report.addProperty("visual_check_scope", "Both equipped weapons have changing first-person hand-region pixels during real Punchy attack playback; previews record the actual result");
            return true;
        }
        if (stage == 0) {
            String id = WEAPONS.get(index);
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            check(item != net.minecraft.world.item.Items.AIR, "Actual input audit item is registered: " + id);
            mc.player.stopUsingItem(); mc.options.keyUse.setDown(false); mc.options.keyAttack.setDown(false);
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            mc.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            // Aim into empty sky: a real attack-key click swings without a target or damage packet.
            mc.player.setXRot(-65F); mc.player.xRotO = -65F;
            fact = new JsonObject(); fact.addProperty("item", id); cases.add(fact);
            ticks = 0; stage = 1; frameReady = false;
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
            mc.options.keyAttack.setDown(true);
            KeyMapping.click(mc.options.keyAttack.getKey());
            fact.addProperty("input_epoch_before", inputBefore);
            fact.addProperty("visual_epoch_before", visualBefore);
            fact.addProperty("native_attack_key_clicked", true);
            ticks = 0; stage = 3; frameReady = false;
            return false;
        }
        if (stage == 3) {
            mc.options.keyAttack.setDown(false);
            check(++ticks < 60, "The actual native attack input must produce a playing Punchy sword clip within three seconds");
            long epoch = ((Number)inputEpoch.invoke(null)).longValue();
            long visual = ((Number)visualEpoch.invoke(null)).longValue();
            Object clip = currentClip.invoke(pose);
            if (epoch <= inputBefore || visual <= visualBefore || !Boolean.TRUE.equals(isPlaying.invoke(pose)) || clip == null) return false;
            String name = (String)clip.getClass().getMethod("getName").invoke(clip);
            check(activeSwordClip(name), "Actual input must play a current resource-pack sword attack, not generic punch: " + name);
            check(!((Map<?, ?>)clip.getClass().getMethod("getTracks").invoke(clip)).isEmpty(), "The playing attack contains actual bone tracks");
            fact.addProperty("native_input_epoch_after", epoch);
            fact.addProperty("visual_playback_epoch_after", visual);
            fact.addProperty("playing_clip", name);
            pendingFrame = "attack"; frameReady = false; ticks = 0; stage = 4;
            return false;
        }
        if (stage == 4) {
            check(++ticks < 60, "The real playing attack must reach a rendered first-person frame within three seconds");
            if (frameReady) { fact.addProperty("passed", true); ++index; stage = 0; }
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
                if (!activeSwordClip(name) || time <= .001F) return;
                fact.addProperty("rendered_playing_clip", name); fact.addProperty("rendered_clip_time", time);
            }
            Path frames = Path.of(System.getProperty("mite.betterCombatPlayerReachAuditOutput")).toAbsolutePath().getParent()
                    .resolve("punchy-club-input-frames");
            Files.createDirectories(frames);
            Path output = frames.resolve(index + "-" + WEAPONS.get(index).replace(':', '-') + "-" + kind + ".png");
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(output);
                if (kind.equals("idle")) {
                    idleWidth = image.getWidth(); idleHeight = image.getHeight(); idlePixels = new int[idleWidth * idleHeight];
                    for (int y = 0; y < idleHeight; ++y) for (int x = 0; x < idleWidth; ++x)
                        idlePixels[y * idleWidth + x] = image.getPixelRGBA(x, y);
                } else {
                    check(image.getWidth() == idleWidth && image.getHeight() == idleHeight, "Idle and attack frames use the same real viewport");
                    int changed = 0;
                    for (int y = idleHeight / 3; y < idleHeight; ++y) for (int x = idleWidth / 3; x < idleWidth; ++x) {
                        int before = idlePixels[y * idleWidth + x], after = image.getPixelRGBA(x, y);
                        int delta = Math.abs((before & 255) - (after & 255))
                                + Math.abs((before >>> 8 & 255) - (after >>> 8 & 255))
                                + Math.abs((before >>> 16 & 255) - (after >>> 16 & 255));
                        if (delta > 24) ++changed;
                    }
                    check(changed > 200, "A real playing sword animation must visibly change first-person hand-region pixels: " + changed);
                    fact.addProperty("first_person_hand_region_changed_pixels", changed);
                    fact.addProperty("frame_is_actual_rendered_playback", true);
                }
            }
            fact.addProperty(kind + "_frame", output.toString());
            pendingFrame = null; frameReady = true;
        } catch (Throwable failure) { frameFailure = failure; }
    }

    private static boolean activeSwordClip(String name) {
        return report.getAsJsonArray("active_sword_resolved_combo_clips").asList().stream().anyMatch(value -> value.getAsString().equals(name));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
