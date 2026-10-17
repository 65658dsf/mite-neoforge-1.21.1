package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.LinkedHashSet;
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
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Actual transformed skeleton weapon layer and pose wiring, without a game, world or GL. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SkeletonWeaponDisplayClientAudit implements DataProvider {
    private static final String MIXINS = "io.github.ningmeng123.mite.mixin.client.";
    private static final String DISPLAY = "io/github/ningmeng123/mite/entity/SkeletonWeaponDisplay";
    private static final String FRAME = DISPLAY + "$Frame";
    private static final String POSES = "com/mojang/blaze3d/vertex/PoseStack";
    private static final String ARM_DESCRIPTOR = "(Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;"
            + "Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    private static final String ANIMATION_DESCRIPTOR = "(Lnet/minecraft/world/entity/Mob;FFFFF)V";
    private final Path output;

    private SkeletonWeaponDisplayClientAudit(Path output) { this.output = output; }

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.skeletonWeaponDisplayAudit")) return;
        event.getGenerator().addProvider(true, new SkeletonWeaponDisplayClientAudit(Path.of(System.getProperty(
                "mite.skeletonWeaponDisplayAuditOutput", "build/verification/skeleton-weapon-display-client-audit.json"))));
    }

    @Override public String getName() { return "Transformed skeleton single weapon and swap pose audit without rendering"; }

    @Override
    public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("target_initialization_requested", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("visual_appearance_passed", false);
        report.addProperty("scope", "actual client Mixin transformations and call sites; not a visual or interactive test");
        JsonArray targets = new JsonArray(); report.add("targets", targets);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "Actual transformed-class export is required");
            auditItemLayer(transformed("net/minecraft/client/renderer/entity/layers/ItemInHandLayer", targets), report);
            auditPose(transformed("net/minecraft/client/model/SkeletonModel", targets), report);
            report.addProperty("status", "passed_actual_skeleton_weapon_display_transforms_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed"); report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void auditItemLayer(ClassNode layer, JsonObject report) {
        MethodNode nativeEntry = method(layer, "renderArmWithItem", ARM_DESCRIPTOR);
        MethodNode wrapper = merged(layer, "SkeletonWeaponItemLayerMixin", "mite$renderOnlyActiveWeapon");
        Set<MethodNode> reachable = reachableMethods(layer, nativeEntry);
        check(reachable.contains(wrapper), "Native hand rendering reaches the actual merged single-weapon wrapper");
        check(calls(wrapper, DISPLAY, "hidesOffhand") == 1,
                "The merged wrapper uses the shared offhand visibility policy exactly once");
        check(calls(wrapper, DISPLAY, "frame") == 1 && calls(wrapper, FRAME, "stack") >= 1,
                "The actual wrapper draws the visible swap-frame stack rather than both equipped stacks");
        check(calls(wrapper, FRAME, "lowering") >= 1 && calls(wrapper, POSES, "translate") >= 1,
                "The merged item wrapper applies the shared lowering frame to the held weapon");
        check(calls(wrapper, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call") >= 1,
                "The shared layer retains native rendering through its original operation");
        List<MethodNode> nativeBodies = reachable.stream().filter(candidate -> calls(candidate,
                "net/minecraft/client/renderer/ItemInHandRenderer", "renderItem") > 0).toList();
        check(nativeBodies.size() == 1, "Exactly one reachable original native item-rendering body is retained");
        MethodNode nativeBody = nativeBodies.getFirst();
        check(calls(nativeBody, "net/minecraft/client/renderer/ItemInHandRenderer", "renderItem") == 1
                && calls(nativeBody, "net/minecraft/client/model/ArmedModel", "translateToHand") == 1
                && calls(nativeBody, "net/minecraft/world/item/ItemStack", "isEmpty") == 1
                && calls(nativeBody, POSES, "pushPose") == 1 && calls(nativeBody, POSES, "popPose") == 1,
                "The original native empty check, arm attachment, item draw and pose scope remain intact");
        JsonObject facts = new JsonObject();
        facts.addProperty("entrypoint", nativeEntry.name + nativeEntry.desc);
        facts.addProperty("merged_wrapper", wrapper.name + wrapper.desc);
        facts.addProperty("reachable_original_body", nativeBody.name + nativeBody.desc);
        facts.addProperty("shared_offhand_gate", true);
        facts.addProperty("frame_stack_and_lowering_wired", true);
        facts.addProperty("native_empty_check_hand_attachment_and_item_draw_preserved", true);
        report.add("item_layer", facts);
    }

    private static void auditPose(ClassNode model, JsonObject report) {
        MethodNode animation = method(model, "setupAnim", ANIMATION_DESCRIPTOR);
        MethodNode pose = merged(model, "SkeletonWeaponPoseMixin", "mite$lowerArmsDuringWeaponSwap");
        check(calls(animation, model.name, pose.name) == 1,
                "The real native skeleton animation invokes the swap arm-pose handler exactly once");
        AbstractInsnNode last = previousExecutable(animation.instructions.getLast());
        check(last != null && last.getOpcode() == Opcodes.RETURN, "The native animation has its normal final return");
        AbstractInsnNode beforeReturn = previousExecutable(last.getPrevious());
        check(beforeReturn instanceof MethodInsnNode call && call.owner.equals(model.name)
                && call.name.equals(pose.name) && call.desc.equals(pose.desc),
                "The arm-lowering handler runs at the actual tail after the native skeleton pose");
        check(calls(pose, DISPLAY, "frame") == 1 && calls(pose, FRAME, "lowering") >= 1,
                "The actual pose handler uses the same swap-frame lowering as the item layer");
        check(calls(pose, "net/minecraft/world/entity/Mob", "getMainArm")
                + calls(pose, "net/minecraft/world/entity/LivingEntity", "getMainArm")
                + calls(pose, "net/minecraft/world/entity/monster/AbstractSkeleton", "getMainArm") >= 1,
                "The actual pose handler selects the entity's physical main arm");
        JsonArray visibleReads = new JsonArray();
        auditVisibleWeaponRead(model, method(model, "prepareMobModel", "(Lnet/minecraft/world/entity/Mob;FFF)V"),
                "mite$posePreparingVisibleWeapon", "getItemInHand",
                "(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;", true, visibleReads);
        auditVisibleWeaponRead(model, animation, "mite$poseAnimatingVisibleWeapon", "getMainHandItem",
                "()Lnet/minecraft/world/item/ItemStack;", false, visibleReads);
        MethodNode bow = merged(model, "SkeletonBowModelMixin", "mite$materialBowPose");
        boolean bowFamily = false;
        for (var instruction : bow.instructions) if (instruction instanceof TypeInsnNode type
                && type.getOpcode() == Opcodes.INSTANCEOF && type.desc.equals("net/minecraft/world/item/BowItem")) bowFamily = true;
        check(bowFamily, "The existing shared recognition still accepts material BowItem subclasses");
        JsonArray bowBranches = new JsonArray();
        for (var signature : List.of(List.of("prepareMobModel", "(Lnet/minecraft/world/entity/Mob;FFF)V"),
                List.of("setupAnim", ANIMATION_DESCRIPTOR))) {
            MethodNode method = method(model, signature.getFirst(), signature.getLast());
            check(calls(method, model.name, bow.name) == 1
                    && calls(method, "net/minecraft/world/item/ItemStack", "is", "(Lnet/minecraft/world/item/Item;)Z") == 0,
                    "Native " + method.name + " keeps the transformed BowItem branch alongside the swap animation");
            JsonObject fact = new JsonObject(); fact.addProperty("method", method.name + method.desc);
            fact.addProperty("bow_family_handler_calls", 1); fact.addProperty("remaining_exact_item_checks", 0); bowBranches.add(fact);
        }
        JsonObject facts = new JsonObject();
        facts.addProperty("merged_tail_handler", pose.name + pose.desc);
        facts.addProperty("after_native_pose", true);
        facts.addProperty("shared_frame_lowering_and_physical_main_arm_wired", true);
        facts.add("visible_weapon_reads", visibleReads);
        facts.add("retained_material_bow_branches", bowBranches);
        report.add("skeleton_pose", facts);
    }

    private static void auditVisibleWeaponRead(ClassNode model, MethodNode nativeEntry, String name,
            String getter, String descriptor, boolean explicitHand, JsonArray facts) {
        MethodNode wrapper = merged(model, "SkeletonWeaponPoseMixin", name);
        Set<MethodNode> reachable = reachableMethods(model, nativeEntry);
        check(reachable.contains(wrapper) && calls(nativeEntry, model.name, wrapper.name) == 1,
                "Actual native " + nativeEntry.name + " wraps its weapon getter exactly once");
        check(calls(wrapper, DISPLAY, "frame") == 1 && calls(wrapper, FRAME, "stack") == 1,
                "The real " + name + " selects the shared visible frame weapon for native pose recognition");
        check(calls(wrapper, "io/github/ningmeng123/mite/entity/SkeletonWeapons", "eligible") == 1,
                "The actual visible-pose read is limited to eligible skeletons");
        check(calls(wrapper, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call") == 1,
                "The real " + name + " preserves the original getter for other cases");
        check(reachable.stream().mapToLong(candidate -> calls(candidate, "net/minecraft/world/entity/Mob", getter, descriptor)).sum() == 1,
                "One original native " + getter + " remains reachable through the actual operation");
        if (explicitHand) {
            boolean mainHand = false;
            for (var instruction : wrapper.instructions) if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals("net/minecraft/world/InteractionHand")
                    && field.name.equals("MAIN_HAND")) mainHand = true;
            check(mainHand, "The actual prepare-pose wrapper only replaces the MAIN_HAND read");
        }
        JsonObject fact = new JsonObject(); fact.addProperty("native_method", nativeEntry.name + nativeEntry.desc);
        fact.addProperty("merged_getter_wrapper", wrapper.name + wrapper.desc);
        fact.addProperty("visible_frame_stack", true); fact.addProperty("eligible_skeleton_gate", true);
        fact.addProperty("explicit_main_hand_gate", explicitHand); fact.addProperty("original_getter_preserved", true);
        facts.add(fact);
    }

    private static AbstractInsnNode previousExecutable(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getPrevious();
        return instruction;
    }

    private static Set<MethodNode> reachableMethods(ClassNode type, MethodNode start) {
        Set<MethodNode> seen = new LinkedHashSet<>();
        ArrayDeque<MethodNode> pending = new ArrayDeque<>(); pending.add(start);
        while (!pending.isEmpty()) {
            MethodNode current = pending.removeFirst();
            if (!seen.add(current)) continue;
            for (var instruction : current.instructions) {
                if (instruction instanceof MethodInsnNode call) enqueue(type, pending, call.owner, call.name, call.desc);
                else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    enqueueHandle(type, pending, dynamic.bsm);
                    for (Object argument : dynamic.bsmArgs) enqueueHandle(type, pending, argument);
                }
            }
        }
        return seen;
    }

    private static void enqueueHandle(ClassNode type, ArrayDeque<MethodNode> pending, Object value) {
        if (value instanceof Handle handle) enqueue(type, pending, handle.getOwner(), handle.getName(), handle.getDesc());
        else if (value instanceof ConstantDynamic constant) {
            enqueueHandle(type, pending, constant.getBootstrapMethod());
            for (int index = 0; index < constant.getBootstrapMethodArgumentCount(); index++)
                enqueueHandle(type, pending, constant.getBootstrapMethodArgument(index));
        }
    }

    private static void enqueue(ClassNode type, ArrayDeque<MethodNode> pending, String owner, String name, String descriptor) {
        if (owner.equals(type.name)) type.methods.stream().filter(method -> method.name.equals(name)
                && method.desc.equals(descriptor)).forEach(pending::addLast);
    }

    private static ClassNode transformed(String name, JsonArray targets) throws Exception {
        Class.forName(name.replace('/', '.'), false, SkeletonWeaponDisplayClientAudit.class.getClassLoader());
        Path exported = Path.of(".mixin.out", "class", name + ".class");
        check(Files.isRegularFile(exported), "The real transformed client target was exported: " + name);
        byte[] bytes = Files.readAllBytes(exported);
        JsonObject fact = new JsonObject(); fact.addProperty("class", name); fact.addProperty("initialized", false);
        fact.addProperty("export", exported.toAbsolutePath().normalize().toString());
        fact.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); targets.add(fact);
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing actual native method " + type.name + "." + name + descriptor));
    }

    private static MethodNode merged(ClassNode type, String simpleMixin, String name) {
        var methods = type.methods.stream().filter(method -> method.name.endsWith(name)
                && (mergedFrom(method.visibleAnnotations, MIXINS + simpleMixin)
                || mergedFrom(method.invisibleAnnotations, MIXINS + simpleMixin))).toList();
        check(methods.size() == 1, "Exactly one actual merged handler " + simpleMixin + "." + name + "; found " + methods.size());
        return methods.getFirst();
    }

    private static boolean mergedFrom(List<AnnotationNode> annotations, String mixin) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                && annotation.values != null) for (int index = 0; index + 1 < annotation.values.size(); index += 2)
            if (annotation.values.get(index).equals("mixin") && annotation.values.get(index + 1).equals(mixin)) return true;
        return false;
    }

    private static long calls(MethodNode method, String owner, String name) { return calls(method, owner, name, null); }

    private static long calls(MethodNode method, String owner, String name, String descriptor) {
        long result = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && (descriptor == null || call.desc.equals(descriptor))) result++;
        return result;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private void write(JsonObject report) throws Exception {
        Path target = output.toAbsolutePath().normalize(); Files.createDirectories(target.getParent());
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
