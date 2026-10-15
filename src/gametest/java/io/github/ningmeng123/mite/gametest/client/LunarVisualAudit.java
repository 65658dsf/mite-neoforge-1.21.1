package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.LunarVisuals;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Opt-in transformed lunar injection/resource audit: no Minecraft instance, world, rendering, GL or UI. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LunarVisualAudit implements DataProvider {
    private static final String ROOT = "io/github/ningmeng123/mite/";
    private static final String MIXINS = "io.github.ningmeng123.mite.mixin.client.";
    private static final String SKY_DESCRIPTOR = "(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V";
    private static final List<Texture> TEXTURES = List.of(
            new Texture("moon_phases.png", 128, 64, "a6b22cc9fabbb51964fde3605016be83f97735fa9bdb422b2575976651387a00"),
            new Texture("halo_256.png", 256, 256, "a56b03b076b6abe6624814dafa0ed04101d3ed192806db428d3128c4a8643095"),
            new Texture("ring_128.png", 128, 128, "65cad5ba4deba74e862d6d0a4215f2602840f061c75f86252dc9ce4f003022d5"));
    private final Path output;
    private LunarVisualAudit(Path output) { this.output = output; }
    private record Texture(String name, int width, int height, String sha256) {}

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.lunarVisualAudit")) return;
        Path workspace = event.getGenerator().getPackOutput().getOutputFolder().toAbsolutePath().resolve("../../..").normalize();
        Path report = Path.of(System.getProperty("mite.lunarVisualAuditOutput",
                workspace.resolve("build/verification/lunar-visual-audit.json").toString()));
        event.getGenerator().addProvider(true, new LunarVisualAudit(report));
    }
    @Override public String getName() { return "R196 transformed lunar visuals and original source PNG audit without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("visual_appearance_passed", false);
        report.addProperty("target_initialization_requested", false);
        JsonArray targets = new JsonArray(), textures = new JsonArray(), nativeCalls = new JsonArray();
        report.add("targets", targets); report.add("textures", textures); report.add("native_calls", nativeCalls);
        report.addProperty("call_graph_edges", "same-class method calls and invokedynamic bootstrap method handles");
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "Mixin transformed-class export must be enabled");
            ClassNode sky = transformed("net.minecraft.client.renderer.LevelRenderer", targets);
            MethodNode render = method(sky, "renderSky", SKY_DESCRIPTOR);
            check(hasMerged(sky, "LunarSkyMixin"), "Missing lunar sky Mixin");
            requireReachable(sky, render, ROOT + "client/LunarSkyRendering", "beforeMoon");
            requireReachable(sky, render, ROOT + "client/LunarSkyRendering", "afterMoon");
            requireReachable(sky, render, ROOT + "client/LunarSkyRendering", "restoreColor");
            auditIrisMoonGate(sky, render, report);
            requireNativeCallCount(sky, render, "net/minecraft/client/multiplayer/ClientLevel", "getMoonPhase", "()I", 1, nativeCalls);
            requireNativeCallCount(sky, render, "net/minecraft/client/multiplayer/ClientLevel", "getStarBrightness", "(F)F", 1, nativeCalls);
            check(hasMerged(sky, "LunarPrecipitationMixin"), "Missing blood-period precipitation Mixin");
            MethodNode rain = method(sky, "renderSnowAndRain", "(Lnet/minecraft/client/renderer/LightTexture;FDDD)V");
            MethodNode rainTick = method(sky, "tickRain", "(Lnet/minecraft/client/Camera;)V");
            requireReachable(sky, rain, ROOT + "world/LunarWeather", "precipitation");
            requireReachable(sky, rainTick, ROOT + "world/LunarWeather", "precipitation");
            requireReachable(sky, rain, ROOT + "rules/LunarCalendar", "state");

            ClassNode atmosphere = transformed("net.minecraft.client.multiplayer.ClientLevel", targets);
            check(hasMerged(atmosphere, "BloodMoonAtmosphereMixin"), "Missing requested daytime blood-rain atmosphere Mixin");
            requireReachable(atmosphere, method(atmosphere, "getSkyColor", "(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"),
                    ROOT + "client/BloodMoonAtmosphere", "color");
            requireReachable(atmosphere, method(atmosphere, "getCloudColor", "(F)Lnet/minecraft/world/phys/Vec3;"),
                    ROOT + "client/BloodMoonAtmosphere", "color");
            ClassNode atmosphereHooks = resourceClass(ROOT + "client/BloodMoonAtmosphere");
            MethodNode fog = method(atmosphereHooks, "fog", "(Lnet/neoforged/neoforge/client/event/ViewportEvent$ComputeFogColor;)V");
            requireReachable(atmosphereHooks, fog, "net/minecraft/client/Camera", "getFluidInCamera");
            requireReachable(atmosphereHooks, fog, ROOT + "client/BloodMoonAtmosphere", "color");
            for (String channel : List.of("setRed", "setGreen", "setBlue"))
                requireReachable(atmosphereHooks, fog, "net/neoforged/neoforge/client/event/ViewportEvent$ComputeFogColor", channel);
            auditDaytimeAtmosphere(report);

            ClassNode light = transformed("net.minecraft.client.renderer.LightTexture", targets);
            MethodNode update = method(light, "updateLightTexture", "(F)V");
            check(hasMerged(light, "LunarLightTextureMixin"), "Missing moon lightmap Mixin");
            requireReachable(light, update, ROOT + "client/LunarVisuals", "lightState");
            requireReachable(light, update, ROOT + "client/LunarVisuals$LightState", "skyMultiplier");
            requireReachable(light, update, ROOT + "client/LunarVisuals", "tintLightmap");
            requireNativeCallCount(light, update, "com/mojang/blaze3d/platform/NativeImage", "setPixelRGBA", "(III)V", 1, nativeCalls);
            requireNativeCallCount(light, update, "net/minecraft/client/renderer/LightTexture", "getBrightness",
                    "(Lnet/minecraft/world/level/dimension/DimensionType;I)F", 2, nativeCalls);

            ClassLoader loader = LunarVisualAudit.class.getClassLoader();
            for (Texture texture : TEXTURES) {
                String path = "assets/mite/textures/environment/" + texture.name();
                byte[] bytes;
                try (var stream = loader.getResourceAsStream(path)) {
                    check(stream != null, "Missing source moon texture " + path);
                    bytes = stream.readAllBytes();
                }
                check(bytes.length >= 33 && ByteBuffer.wrap(bytes).getLong() == 0x89504e470d0a1a0aL, "PNG signature " + path);
                int width = ByteBuffer.wrap(bytes, 16, 4).getInt(), height = ByteBuffer.wrap(bytes, 20, 4).getInt();
                check(width == texture.width() && height == texture.height(), "Original UV dimensions " + path);
                String hash = sha256(bytes);
                check(hash.equals(texture.sha256()), "Unmodified original PNG bytes " + path);
                JsonObject entry = new JsonObject(); entry.addProperty("path", path);
                entry.addProperty("width", width); entry.addProperty("height", height); entry.addProperty("sha256", hash);
                textures.add(entry);
            }
            report.addProperty("status", "passed_transformed_bytecode_and_resource_audit_only");
            write(report); return CompletableFuture.completedFuture(null);
        } catch (Throwable error) {
            report.addProperty("status", "failed"); report.addProperty("failure", error.toString());
            try { write(report); } catch (Exception secondary) { error.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(error);
        }
    }

    private static ClassNode transformed(String name, JsonArray targets) throws Exception {
        // false is essential: defining the type applies Mixin without initializing Minecraft or RenderSystem.
        Class.forName(name, false, LunarVisualAudit.class.getClassLoader());
        Path path = Path.of(".mixin.out", "class", name.replace('.', '/') + ".class");
        check(Files.isRegularFile(path), "Missing transformed class export " + path);
        byte[] bytes = Files.readAllBytes(path);
        ClassNode type = new ClassNode(); new ClassReader(bytes).accept(type, 0);
        JsonObject entry = new JsonObject(); entry.addProperty("class", name);
        entry.addProperty("export", path.toAbsolutePath().normalize().toString()); entry.addProperty("sha256", sha256(bytes));
        targets.add(entry); return type;
    }
    private static ClassNode resourceClass(String name) throws Exception {
        try (var stream = LunarVisualAudit.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(stream != null, "Missing atmosphere hook class resource");
            ClassNode type = new ClassNode(); new ClassReader(stream.readAllBytes()).accept(type, 0); return type;
        }
    }
    private static void auditDaytimeAtmosphere(JsonObject report) {
        var original = new LunarVisuals.Color(.1F, .2F, .3F);
        var day = LunarCalendar.state(31L * 24000L + 6000L, true);
        var red = LunarVisuals.bloodRainAtmosphere(day, 1F, original);
        check(Math.abs(red.red() - .16F) < .00001F && Math.abs(red.green() - .024F) < .00001F
                && Math.abs(red.blue() - .036F) < .00001F, "Requested full-rain daylight has the independent dark-red RGB fixture");
        check(LunarVisuals.bloodRainAtmosphere(day, 0F, original) == original, "No-rain sky remains native");
        for (long time : new long[]{31L * 24000L + 18000L, 23L * 24000L + 6000L, 127L * 24000L + 6000L})
            check(LunarVisuals.bloodRainAtmosphere(LunarCalendar.state(time, true), 1F, original) == original,
                    "Blood night, harvest and blue moon retain their existing atmosphere");
        check(LunarVisuals.bloodRainAtmosphere(LunarCalendar.state(31L * 24000L + 6000L, false), 1F, original) == original,
                "Non-overworld atmosphere remains native");
        report.addProperty("daytime_blood_rain_atmosphere", "passed_native_sky_cloud_hooks_and_air_fog_event");
        report.addProperty("daytime_atmosphere_is_user_requested_modern_extension", true);
        report.addProperty("existing_moon_disk_glow_and_night_lightmap_preserved", true);
    }
    private static void requireReachable(ClassNode type, MethodNode start, String owner, String name) {
        check(reachesCall(type, start, owner, name), "Missing reachable injected call " + type.name + "." + start.name + " -> " + owner + "." + name);
    }
    private static boolean reachesCall(ClassNode type, MethodNode start, String owner, String name) {
        for (MethodNode method : reachableMethods(type, start)) {
            for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(owner) && call.name.equals(name)) return true;
            }
        }
        return false;
    }
    private static boolean moonLayer(ClassNode type, MethodNode method) {
        return reachesCall(type, method, ROOT + "client/LunarSkyRendering", "beforeMoon")
                && reachesCall(type, method, ROOT + "client/LunarSkyRendering", "afterMoon");
    }
    private static void auditIrisMoonGate(ClassNode type, MethodNode render, JsonObject report) {
        JsonObject fact = new JsonObject(); report.add("iris_moon_gate", fact);
        boolean loaded = ModList.get().isLoaded("iris"); fact.addProperty("iris_loaded", loaded);
        if (!loaded) { fact.addProperty("status", "skipped_iris_not_loaded"); return; }
        var gates = type.methods.stream().filter(method -> method.name.endsWith("$iris$beforeDrawMoon")).toList();
        check(gates.size() == 1, "Expected the unique Iris moon-toggle wrapper in the transformed renderer");
        MethodNode gate = gates.getFirst(); int directGates = 0, directLayers = 0;
        JsonArray implementations = new JsonArray();
        Map<String, MethodNode> dynamicRoots = new LinkedHashMap<>();
        for (AbstractInsnNode instruction : render.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(type.name)) {
                if (call.name.equals(gate.name) && call.desc.equals(gate.desc)) directGates++;
                for (MethodNode candidate : type.methods) if (candidate.name.equals(call.name) && candidate.desc.equals(call.desc) && moonLayer(type, candidate))
                    directLayers++;
            } else if (instruction instanceof InvokeDynamicInsnNode call) {
                ArrayDeque<MethodNode> roots = new ArrayDeque<>();
                for (Object argument : call.bsmArgs) enqueueHandle(type, argument, roots);
                for (MethodNode candidate : roots) if (moonLayer(type, candidate)) dynamicRoots.putIfAbsent(candidate.name + candidate.desc, candidate);
            }
        }
        check(directGates == 1, "renderSky must directly call the outer Iris moon toggle exactly once");
        check(directLayers == 0, "renderSky must not directly bypass Iris via the Mite moon handler or its sugar bridge");
        check(!dynamicRoots.isEmpty(), "The outer Iris operation must have an invokedynamic implementation reaching both Mite moon layers");
        dynamicRoots.values().forEach(method -> implementations.add(method.name + method.desc));
        int operationCalls = 0, closes = 0, operationIndex = -1, closeIndex = -1;
        boolean moonToggle = false;
        for (int index = 0; index < gate.instructions.size(); index++) {
            AbstractInsnNode instruction = gate.instructions.get(index);
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation") && call.name.equals("call")
                        && call.desc.equals("([Ljava/lang/Object;)Ljava/lang/Object;")) { operationCalls++; operationIndex = index; }
                if (call.owner.equals("com/mojang/blaze3d/vertex/MeshData") && call.name.equals("close") && call.desc.equals("()V"))
                    { closes++; closeIndex = index; }
            } else if (instruction instanceof InvokeDynamicInsnNode call) for (Object argument : call.bsmArgs)
                if (argument instanceof Handle handle && handle.getOwner().equals("net/irisshaders/iris/pipeline/WorldRenderingPipeline")
                        && handle.getName().equals("shouldRenderMoon") && handle.getDesc().equals("()Z")) moonToggle = true;
        }
        check(moonToggle, "The Iris gate must still consult WorldRenderingPipeline.shouldRenderMoon");
        check(operationCalls == 1 && closes == 1, "Iris moon gate must retain one original-operation path and one mesh-close path");
        boolean falseBranchSkipsLayers = false;
        for (int index = 0; index < gate.instructions.size(); index++) if (gate.instructions.get(index) instanceof JumpInsnNode jump
                && jump.getOpcode() == Opcodes.IFEQ && index < operationIndex) {
            int target = gate.instructions.indexOf(jump.label);
            if (target > operationIndex && target <= closeIndex) falseBranchSkipsLayers = true;
        }
        check(falseBranchSkipsLayers, "The Iris disabled-moon branch must skip Operation.call and close the mesh instead");
        fact.addProperty("status", "passed_outer_iris_toggle_guards_disk_and_glow");
        fact.addProperty("gate", gate.name + gate.desc); fact.addProperty("direct_gate_calls", directGates);
        fact.addProperty("direct_mite_layer_calls", directLayers); fact.add("mite_operation_implementations", implementations);
        fact.addProperty("operation_call_instruction_index", operationIndex); fact.addProperty("mesh_close_instruction_index", closeIndex);
        fact.addProperty("should_render_moon_handle", true);
        fact.addProperty("disabled_branch_skips_mite_layers", true);
    }
    private static List<MethodNode> reachableMethods(ClassNode type, MethodNode start) {
        // MixinExtras Operations are lambdas. Their original calls can reside in same-class
        // implementation handles, even when the wrapping method calls only Operation.call.
        Map<String, MethodNode> seen = new LinkedHashMap<>();
        ArrayDeque<MethodNode> queue = new ArrayDeque<>(); queue.add(start);
        while (!queue.isEmpty()) {
            MethodNode current = queue.removeFirst();
            if (seen.putIfAbsent(current.name + current.desc, current) != null) continue;
            for (AbstractInsnNode instruction : current.instructions) {
                if (instruction instanceof MethodInsnNode call) enqueueMethod(type, call.owner, call.name, call.desc, queue);
                else if (instruction instanceof InvokeDynamicInsnNode call) {
                    enqueueHandle(type, call.bsm, queue);
                    for (Object argument : call.bsmArgs) enqueueHandle(type, argument, queue);
                }
            }
        }
        return List.copyOf(seen.values());
    }
    private static void enqueueHandle(ClassNode type, Object value, ArrayDeque<MethodNode> queue) {
        if (value instanceof Handle handle) enqueueMethod(type, handle.getOwner(), handle.getName(), handle.getDesc(), queue);
        else if (value instanceof ConstantDynamic constant) {
            enqueueHandle(type, constant.getBootstrapMethod(), queue);
            for (int index = 0; index < constant.getBootstrapMethodArgumentCount(); index++)
                enqueueHandle(type, constant.getBootstrapMethodArgument(index), queue);
        }
    }
    private static void enqueueMethod(ClassNode type, String owner, String name, String descriptor, ArrayDeque<MethodNode> queue) {
        if (!owner.equals(type.name)) return;
        type.methods.stream().filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor)).forEach(queue::addLast);
    }
    private static void requireNativeCallCount(ClassNode type, MethodNode start, String owner, String name,
            String descriptor, int expected, JsonArray facts) {
        // Count static call sites once per reachable method, rather than multiplying wrappers
        // or requiring an optional mod to leave the invocation directly inside the entrypoint.
        JsonArray sites = new JsonArray();
        for (MethodNode method : reachableMethods(type, start)) {
            int index = 0;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) {
                    JsonObject site = new JsonObject(); site.addProperty("method", method.name + method.desc);
                    site.addProperty("instruction_index", index); sites.add(site);
                }
                index++;
            }
        }
        JsonObject fact = new JsonObject(); fact.addProperty("entrypoint", type.name + "." + start.name + start.desc);
        fact.addProperty("native_target", owner + "." + name + descriptor);
        fact.addProperty("expected_call_sites", expected); fact.addProperty("reachable_call_sites", sites.size());
        fact.add("sites", sites); facts.add(fact);
        check(sites.size() == expected, "Expected " + expected + " reachable native call sites for " + owner + "." + name + descriptor
                + " from " + type.name + "." + start.name + ", found " + sites.size());
    }
    private static boolean hasMerged(ClassNode type, String simpleName) {
        String mixin = MIXINS + simpleName;
        return type.methods.stream().anyMatch(method -> mergedFrom(method.visibleAnnotations, mixin) || mergedFrom(method.invisibleAnnotations, mixin));
    }
    private static boolean mergedFrom(List<AnnotationNode> annotations, String mixin) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null)
            for (int i = 0; i + 1 < annotation.values.size(); i += 2)
                if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals(mixin)) return true;
        return false;
    }
    private static MethodNode method(ClassNode type, String name, String desc) {
        return type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned typed method " + type.name + "." + name + desc));
    }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private void write(JsonObject report) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
