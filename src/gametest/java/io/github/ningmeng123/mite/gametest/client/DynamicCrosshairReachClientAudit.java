package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.Mite;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Executes the real merged optional hooks with reach fixtures in a headless client data run. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DynamicCrosshairReachClientAudit {
    private static final String TARGET = "mod.crend.dynamiccrosshair.component.CrosshairHandler";
    private static final String PROBE = "io/github/ningmeng123/mite/gametest/client/DynamicCrosshairHookProbe";
    private static final String SOURCE = "io/github/ningmeng123/mite/combat/SourcePlayerReach";
    private DynamicCrosshairReachClientAudit() {}

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.dynamicCrosshairReachAudit")) return;
        Path report = Path.of(System.getProperty("mite.dynamicCrosshairReachAuditOutput",
                "build/verification/dynamic-crosshair-reach-client-audit.json")).toAbsolutePath();
        event.getGenerator().addProvider(true, new Audit(report));
    }

    private record Audit(Path report) implements DataProvider {
        @Override public String getName() { return "Dynamic Crosshair actual merged reach hooks and cache transitions"; }
        @Override public CompletableFuture<?> run(CachedOutput output) {
            var result = new JsonObject();
            result.addProperty("author", "NingMeng123");
            var loader = DynamicCrosshairReachClientAudit.class.getClassLoader();
            if (loader.getResource(TARGET.replace('.', '/') + ".class") == null) {
                result.addProperty("status", "skipped_optional_mod_absent");
                return DataProvider.saveStable(output, result, report);
            }
            try {
                Class.forName(TARGET, false, loader);
                var target = new ClassNode();
                new ClassReader(Files.readAllBytes(Path.of(".mixin.out", "class", TARGET.replace('.', '/') + ".class"))).accept(target, 0);
                MethodNode refresh = hook(target, "mite$refreshAttackReach");
                MethodNode indicator = hook(target, "mite$attackableEntityType");
                check(calls(target, refresh.name) == 1, "The installed handler invokes the actual reach refresh hook once");
                check(calls(target, indicator.name) == 1, "The installed handler invokes the actual entity indicator hook once");
                check(calls(refresh, "canTargetMelee") == 1, "The real refresh hook delegates to the shared attack-point check");
                var probe = probe(target, refresh, indicator);
                Method refreshMethod = probe.getDeclaredMethod(refresh.name, Object.class, HitResult.class,
                        net.minecraft.client.player.LocalPlayer.class, Operation.class);
                Method indicatorMethod = probe.getDeclaredMethod(indicator.name, HitResult.Type.class);
                var fixture = probe.getField("fixtureReachable");
                var hit = new EntityHitResult((Entity)null, Vec3.ZERO);
                int[] originalCalls = {0};
                Operation<Boolean> unchanged = arguments -> { originalCalls[0]++; return false; };
                boolean[] values = {false, true, true, false, false, true};
                boolean[] invalidates = {false, true, false, true, false, true};
                for (int index = 0; index < values.length; index++) {
                    fixture.setBoolean(null, values[index]);
                    boolean changed = (boolean)refreshMethod.invoke(null, null, hit, null, unchanged);
                    check(changed == invalidates[index], "The real merged cache hook handles transition " + index);
                    HitResult.Type type = (HitResult.Type)indicatorMethod.invoke(null, HitResult.Type.ENTITY);
                    check(type == (values[index] ? HitResult.Type.ENTITY : HitResult.Type.MISS),
                            "The real merged indicator follows the current reach fixture " + index);
                }
                check(originalCalls[0] == values.length, "Every reach query runs Dynamic Crosshair's own state update exactly once");
                Operation<Boolean> originalChanged = arguments -> true;
                check((boolean)refreshMethod.invoke(null, null, hit, null, originalChanged), "Original context changes still invalidate the cached crosshair");
                check(indicatorMethod.invoke(null, HitResult.Type.BLOCK) == HitResult.Type.BLOCK, "Block indicators keep their own targeting type");
                check(indicatorMethod.invoke(null, HitResult.Type.MISS) == HitResult.Type.MISS, "Misses retain their targeting type");
                result.addProperty("status", "passed_merged_hooks_and_cache_transitions");
                result.addProperty("reach_transitions", values.length);
                result.addProperty("actual_optional_handler_transformed", true);
                result.addProperty("shared_geometry_check_verified_by_gametest", true);
                result.addProperty("minecraft_initialized", false);
                result.addProperty("world_created", false);
                result.addProperty("rendering_tested", false);
                return DataProvider.saveStable(output, result, report);
            } catch (ReflectiveOperationException | java.io.IOException error) {
                throw new IllegalStateException("Dynamic Crosshair merged hook audit failed", error);
            }
        }
    }

    private static Class<?> probe(ClassNode target, MethodNode refresh, MethodNode indicator) {
        var node = new ClassNode();
        node.version = Opcodes.V21;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = PROBE;
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fixtureReachable", "Z", null, null));
        String checkDescriptor = null;
        for (MethodNode original : new MethodNode[]{refresh, indicator}) {
            var copy = new MethodNode(original.access, original.name, original.desc, original.signature,
                    original.exceptions.toArray(String[]::new));
            original.accept(copy);
            copy.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
            copy.visibleAnnotations = null;
            copy.invisibleAnnotations = null;
            for (var instruction : copy.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.equals(target.name)) {
                    field.owner = PROBE;
                    if (node.fields.stream().noneMatch(existing -> existing.name.equals(field.name)))
                        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, field.name, field.desc, null, null));
                }
                if (instruction instanceof MethodInsnNode call && call.owner.equals(SOURCE) && call.name.equals("canTargetMelee")) {
                    checkDescriptor = call.desc;
                    call.owner = PROBE;
                }
            }
            node.methods.add(copy);
        }
        check(checkDescriptor != null, "The transformed refresh method contains its real shared geometry call");
        var check = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "canTargetMelee", checkDescriptor, null, null);
        check.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, PROBE, "fixtureReachable", "Z"));
        check.instructions.add(new InsnNode(Opcodes.IRETURN));
        node.methods.add(check);
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return new ProbeLoader().define(writer.toByteArray());
    }

    private static final class ProbeLoader extends ClassLoader {
        ProbeLoader() { super(DynamicCrosshairReachClientAudit.class.getClassLoader()); }
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }

    private static MethodNode hook(ClassNode node, String suffix) {
        return node.methods.stream().filter(method -> method.name.contains(suffix)).findFirst()
                .orElseThrow(() -> new IllegalStateException("The actual optional class is missing hook " + suffix));
    }
    private static int calls(ClassNode node, String name) { return node.methods.stream().mapToInt(method -> calls(method, name)).sum(); }
    private static int calls(MethodNode method, String name) {
        int count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.contains(name)) count++;
        return count;
    }
    private static void check(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
}
