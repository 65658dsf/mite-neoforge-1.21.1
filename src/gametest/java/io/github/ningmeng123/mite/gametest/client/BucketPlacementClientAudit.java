package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Defines the actual transformed client controller without a Minecraft instance, world or window. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BucketPlacementClientAudit implements DataProvider {
    private static final String TARGET = "net.minecraft.client.multiplayer.MultiPlayerGameMode";
    private static final String MIXIN = "io.github.ningmeng123.mite.mixin.client.BucketPlacementInputMixin";
    private final Path output;

    private BucketPlacementClientAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.bucketPlacementClientAudit")) return;
        event.getGenerator().addProvider(true, new BucketPlacementClientAudit(Path.of(System.getProperty(
                "mite.bucketPlacementClientAuditOutput", "build/verification/bucket-client-audit.json"))));
    }

    @Override public String getName() { return "Transformed bucket Ctrl prediction and packet-order audit without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("target", TARGET);
        report.addProperty("mixin", MIXIN);
        report.addProperty("target_initialization_requested", false);
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("opengl_or_render_invoked", false);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "The audit requires actual transformed-class export");
            Class.forName(TARGET, false, getClass().getClassLoader());
            Path export = Path.of(".mixin.out", "class", TARGET.replace('.', '/') + ".class");
            check(Files.isRegularFile(export), "The real client controller was transformed and exported");
            ClassNode type = new ClassNode();
            new ClassReader(Files.readAllBytes(export)).accept(type, 0);
            MethodNode mode = handler(type, "mite$predictBucketMode");
            MethodNode input = handler(type, "mite$sendBucketModeFirst");
            check(calls(mode, "io/github/ningmeng123/mite/world/BucketPlacementController", "withIntent"),
                    "The merged prediction handler enters the shared placement mode scope");
            check(calls(input, "io/github/ningmeng123/mite/client/BucketPlacementInput", "beforeSend"),
                    "The merged packet handler sends Ctrl intent before the vanilla packet");
            MethodNode use = method(type, "useItem", "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;");
            MethodNode prediction = method(type, "startPrediction", "(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V");
            check(reachable(type, use, mode), "The actual native useItem path reaches its merged prediction handler");
            check(reachable(type, prediction, input), "The actual native startPrediction path reaches its merged packet handler");
            String bucketTarget = "net.minecraft.world.item.BucketItem";
            Class.forName(bucketTarget, false, getClass().getClassLoader());
            ClassNode bucket = new ClassNode();
            new ClassReader(Files.readAllBytes(Path.of(".mixin.out", "class", bucketTarget.replace('.', '/') + ".class")))
                    .accept(bucket, 0);
            String pickupMixin = "io.github.ningmeng123.mite.mixin.LiquidBucketFillMixin";
            MethodNode targeting = handler(bucket, "mite$targetFlowingLiquid", pickupMixin);
            MethodNode collection = handler(bucket, "mite$collectLiquid", pickupMixin);
            MethodNode bucketUse = method(bucket, "use", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;");
            check(reachable(bucket, bucketUse, targeting)
                            && calls(targeting, "io/github/ningmeng123/mite/survival/LiquidBucketFilling", "includeFlowingLiquids"),
                    "The actual client bucket use targets source and flowing liquid through the shared helper");
            check(reachable(bucket, bucketUse, collection)
                            && calls(collection, "io/github/ningmeng123/mite/survival/LiquidBucketFilling", "pickup"),
                    "Client bucket collection reaches the same Ctrl-only removal logic as the server");
            report.addProperty("actual_client_bucket_reaches_flowing_liquid_targeting", true);
            report.addProperty("actual_client_bucket_reaches_controlled_pickup", true);
            String minecraftTarget = "net.minecraft.client.Minecraft";
            Class.forName(minecraftTarget, false, getClass().getClassLoader());
            ClassNode minecraft = new ClassNode();
            new ClassReader(Files.readAllBytes(Path.of(".mixin.out", "class", minecraftTarget.replace('.', '/') + ".class")))
                    .accept(minecraft, 0);
            MethodNode repeat = handler(minecraft, "mite$synchronizePlacementRepeat",
                    "io.github.ningmeng123.mite.mixin.client.PlacementCooldownMinecraftMixin");
            check(reachable(minecraft, method(minecraft, "startUseItem", "()V"), repeat),
                    "The native repeated-use path reaches placement-specific timing synchronization");
            check(calls(repeat, "io/github/ningmeng123/mite/world/PlacementDelay", "blocked")
                            && calls(repeat, "io/github/ningmeng123/mite/world/PlacementDelay", "remainingMillis"),
                    "The native use gate checks the shared real-time placement deadline");
            MethodNode poll = handler(minecraft, "mite$pollPlacementRepeat",
                    "io.github.ningmeng123.mite.mixin.client.PlacementCooldownMinecraftMixin");
            check(reachable(minecraft, method(minecraft, "handleKeybinds", "()V"), poll),
                    "The actual native input loop reaches real-time placement polling");
            check(calls(poll, "io/github/ningmeng123/mite/world/PlacementDelay", "remainingMillis"),
                    "Held placement resumes from the shared millisecond deadline");
            check(calls(repeat, "io/github/ningmeng123/mite/client/PlacementClickTiming", "begin")
                            && calls(repeat, "io/github/ningmeng123/mite/client/PlacementClickTiming", "consumeAttempted")
                            && calls(repeat, "io/github/ningmeng123/mite/client/PlacementClickTiming", "rightClickDelay"),
                    "Native use scopes placement attempts and applies their retry timing");
            MethodNode consumed = handler(minecraft, "mite$clearAttemptAfterConsumedUse",
                    "io.github.ningmeng123.mite.mixin.client.PlacementCooldownMinecraftMixin");
            check(reachable(minecraft, method(minecraft, "startUseItem", "()V"), consumed)
                            && calls(consumed, "io/github/ningmeng123/mite/client/PlacementClickTiming", "actionConsumed"),
                    "Successful block, item and entity use clears earlier failed placement attempts");
            String delayTarget = "io.github.ningmeng123.mite.world.PlacementDelay";
            Class.forName(delayTarget, false, getClass().getClassLoader());
            ClassNode delay = new ClassNode();
            new ClassReader(Files.readAllBytes(Path.of(".mixin.out", "class", delayTarget.replace('.', '/') + ".class")))
                    .accept(delay, 0);
            MethodNode attempt = handler(delay, "mite$recordPlacementAttempt",
                    "io.github.ningmeng123.mite.mixin.client.PlacementCooldownAttemptMixin");
            check(reachable(delay, method(delay, "placementBlocked", "(Lnet/minecraft/world/entity/player/Player;)Z"), attempt)
                            && calls(attempt, "io/github/ningmeng123/mite/client/PlacementClickTiming", "attempted"),
                    "Actual block and bucket placement checks record local attempts for immediate failure retries");
            report.addProperty("native_start_use_reaches_placement_timing", true);
            report.addProperty("native_keybinds_reach_placement_deadline_polling", true);
            report.addProperty("placement_input_uses_shared_millisecond_deadline", true);
            report.addProperty("placement_gate_reaches_client_attempt_recording", true);
            report.addProperty("native_start_use_reaches_placement_retry_timing", true);
            report.addProperty("successful_use_clears_prior_placement_attempt", true);
            report.addProperty("transformed_export", export.toAbsolutePath().normalize().toString());
            report.addProperty("prediction_scope_handler", mode.name);
            report.addProperty("packet_order_handler", input.name);
            report.addProperty("actual_native_use_reaches_scope", true);
            report.addProperty("actual_prediction_reaches_input_packet", true);
            report.addProperty("status", "passed_transformed_client_bucket_input_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Exception failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (IOException secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static MethodNode handler(ClassNode type, String name) {
        return handler(type, name, MIXIN);
    }

    private static MethodNode handler(ClassNode type, String name, String mixin) {
        // Mixin also merges a lambda named lambda$mite$predictBucketMode$0. Only the
        // actual WrapMethod/WrapOperation handler ends with the original handler name.
        var matches = type.methods.stream().filter(method -> method.name.endsWith(name)
                && (merged(method.visibleAnnotations, mixin) || merged(method.invisibleAnnotations, mixin))).toList();
        check(matches.size() == 1, "Exactly one actual bucket Mixin handler is merged for " + name + "; found " + matches.size());
        return matches.getFirst();
    }

    private static boolean merged(List<AnnotationNode> annotations, String mixin) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                && annotation.values != null) for (int i = 0; i + 1 < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals(mixin)) return true;
        return false;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing native method " + name + descriptor));
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) return true;
        return false;
    }

    private static boolean reachable(ClassNode type, MethodNode start, MethodNode target) {
        Set<MethodNode> seen = new HashSet<>();
        ArrayDeque<MethodNode> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            MethodNode current = pending.removeFirst();
            if (!seen.add(current)) continue;
            if (current == target) return true;
            for (var instruction : current.instructions) {
                if (instruction instanceof MethodInsnNode call) enqueue(type, pending, call.owner, call.name, call.desc);
                if (instruction instanceof InvokeDynamicInsnNode call) for (Object argument : call.bsmArgs)
                    if (argument instanceof Handle handle) enqueue(type, pending, handle.getOwner(), handle.getName(), handle.getDesc());
            }
        }
        return false;
    }

    private static void enqueue(ClassNode type, ArrayDeque<MethodNode> pending, String owner, String name, String descriptor) {
        if (owner.equals(type.name)) type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).forEach(pending::addLast);
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private void write(JsonObject report) throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
