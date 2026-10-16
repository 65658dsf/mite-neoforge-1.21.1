package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.Mite;
import java.lang.reflect.Field;
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
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.ParticleStatus;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.network.chat.Component;
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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Real transformed option values and call paths without constructing Minecraft, options, widgets or a window. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSettingsAudit implements DataProvider {
    private static final String OPTION = "net/minecraft/client/OptionInstance";
    private static final String OPTIONS = "net/minecraft/client/Options";
    private static final String MIXIN = "io.github.ningmeng123.mite.mixin.client.FixedBrightnessOptionMixin";
    private static final String SODIUM_MIXIN = "io.github.ningmeng123.mite.mixin.client.SodiumBrightnessOptionMixin";
    private static final String SODIUM_OPTION = "net.caffeinemc.mods.sodium.client.config.structure.Option";
    private final Path output;

    private ClientSettingsAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.clientSettingsAudit")) return;
        event.getGenerator().addProvider(true, new ClientSettingsAudit(Path.of(System.getProperty(
                "mite.clientSettingsAuditOutput", "build/verification/client-settings-audit.json"))));
    }

    @Override public String getName() { return "Transformed fixed-brightness values and optional settings integration without a game window"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("options_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("widget_created", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("visual_appearance_passed", false);
        JsonArray exports = new JsonArray(); report.add("transformed_targets", exports);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "The actual transformed classes must be exported");
            ClassNode option = transformed(OPTION.replace('/', '.'), exports);
            auditValues(report);
            auditOptionHooks(option, report);
            auditNativePersistence(exports, report);
            auditLightmap(exports, report);
            auditOptionalSodium(exports, report);
            report.addProperty("status", "passed_real_option_values_and_transformed_call_paths_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable error) {
            report.addProperty("status", "failed"); report.addProperty("failure", error.toString());
            try { write(report); } catch (Exception secondary) { error.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(error);
        }
    }

    private static void auditValues(JsonObject report) throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        OptionInstance<Double> gamma = new OptionInstance<>("options.gamma", OptionInstance.noTooltip(),
                (caption, value) -> caption, OptionInstance.UnitDouble.INSTANCE, .5D, value -> callbacks.incrementAndGet());
        Field valueField = OptionInstance.class.getDeclaredField("value"); valueField.setAccessible(true);
        check(Double.valueOf(0D).equals(valueField.get(gamma)) && Double.valueOf(0D).equals(gamma.get()),
                "Constructing the real gamma option fixes both its stored and reported value to the lowest brightness");
        JsonArray cases = new JsonArray();
        for (Double requested : new Double[]{0D, .5D, 1D, 100D, -1D, Double.NaN, null}) {
            // Make the field dirty before each real setter call: the setter must repair it,
            // including inputs that native validation would reject or throw on.
            valueField.set(gamma, .75D);
            gamma.set(requested);
            check(Double.valueOf(0D).equals(valueField.get(gamma)) && Double.valueOf(0D).equals(gamma.get()),
                    "The real gamma setter rejects the requested brightness " + requested);
            check(callbacks.get() == 0, "Locked brightness changes do not invoke callbacks");
            JsonObject result = new JsonObject(); result.addProperty("requested", String.valueOf(requested));
            result.addProperty("stored", (Double)valueField.get(gamma)); result.addProperty("reported", gamma.get()); cases.add(result);
        }
        valueField.set(gamma, 1D);
        check(Double.valueOf(0D).equals(gamma.get()), "The getter remains minimum even after an external raw-field write");
        gamma.set(.5D);

        OptionInstance<Double> volume = new OptionInstance<>("soundCategory.master", OptionInstance.noTooltip(),
                (caption, value) -> caption, OptionInstance.UnitDouble.INSTANCE, .35D, value -> callbacks.incrementAndGet());
        OptionInstance<Integer> distance = new OptionInstance<>("options.renderDistance", OptionInstance.noTooltip(),
                (caption, value) -> caption, new OptionInstance.IntRange(2, 32), 12, value -> callbacks.incrementAndGet());
        OptionInstance<ParticleStatus> particles = new OptionInstance<>("options.particles", OptionInstance.noTooltip(),
                OptionInstance.forOptionEnum(), new OptionInstance.Enum<>(List.of(ParticleStatus.values()),
                        Codec.INT.xmap(ParticleStatus::byId, ParticleStatus::getId)), ParticleStatus.DECREASED,
                value -> callbacks.incrementAndGet());
        check(Double.valueOf(.35D).equals(volume.get()) && Double.valueOf(.35D).equals(valueField.get(volume)),
                "Ordinary sound settings retain their selected value");
        check(Integer.valueOf(12).equals(distance.get()) && Integer.valueOf(12).equals(valueField.get(distance)),
                "Ordinary view-distance settings retain their selected value");
        check(particles.get() == ParticleStatus.DECREASED && valueField.get(particles) == ParticleStatus.DECREASED,
                "Ordinary particle settings retain their selected value");
        check(callbacks.get() == 0, "No option constructor or fixed-brightness request caused a side effect");
        report.add("real_gamma_set_requests", cases);
        report.addProperty("real_gamma_constructor_stored_zero", true);
        report.addProperty("raw_field_write_cannot_bypass_gamma_getter", true);
        report.addProperty("gamma_callbacks_invoked", callbacks.get());
        report.addProperty("unrelated_volume_render_distance_particles_preserved", true);
    }

    private static void auditOptionHooks(ClassNode option, JsonObject report) {
        JsonArray hooks = new JsonArray();
        List<MethodNode> constructors = option.methods.stream().filter(method -> method.name.equals("<init>")).toList();
        check(constructors.stream().anyMatch(method -> reachesMixin(option, method, MIXIN)), "An actual constructor reaches the merged initialization guard");
        hooks.add("constructor");
        MethodNode get = method(option, "get", "()Ljava/lang/Object;");
        MethodNode set = method(option, "set", "(Ljava/lang/Object;)V");
        MethodNode widget = method(option, "createButton", "(Lnet/minecraft/client/Options;IIILjava/util/function/Consumer;)Lnet/minecraft/client/gui/components/AbstractWidget;");
        for (MethodNode entry : List.of(get, set, widget)) {
            check(reachesMixin(option, entry, MIXIN), "Actual " + entry.name + entry.desc + " reaches its merged brightness guard"); hooks.add(entry.name + entry.desc);
        }
        boolean disables = false, tooltip = false;
        for (MethodNode reachable : reachable(option, widget)) if (merged(reachable, MIXIN)) {
            for (AbstractInsnNode instruction : reachable.instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                        && field.owner.equals("net/minecraft/client/gui/components/AbstractWidget") && field.name.equals("active")) disables = true;
                if (instruction instanceof MethodInsnNode call && call.name.equals("setTooltip")) tooltip = true;
            }
        }
        check(disables && tooltip, "Native gamma widget is disabled with an explanatory tooltip");
        for (MethodNode entry : List.of(get, set)) for (MethodNode reachable : reachable(option, entry)) if (merged(reachable, MIXIN)) {
            check(!hasCall(reachable, OPTIONS, "save"), "Brightness enforcement must not save options");
            check(!hasCall(reachable, "java/util/function/Consumer", "accept"), "Locked brightness enforcement must not invoke option callbacks");
            check(!hasCall(reachable, "net/minecraft/client/Minecraft", "getInstance"), "Brightness enforcement has no game-instance requirement");
        }
        report.add("actual_option_entrypoints_reach_guard", hooks);
        report.addProperty("native_gamma_widget_disabled_with_tooltip", true);
        report.addProperty("brightness_guard_does_not_save_or_invoke_callbacks", true);
    }

    private static void auditNativePersistence(JsonArray exports, JsonObject report) throws Exception {
        ClassNode options = definedBytecode(OPTIONS.replace('/', '.'), exports);
        MethodNode load = method(options, "load", "(Z)V"), save = method(options, "save", "()V");
        boolean loadsThroughSetter = false, savesThroughGetter = false;
        for (String name : nativeAnonymousTypes(options, load)) {
            ClassNode access = definedBytecode(name.replace('/', '.'), exports);
            for (MethodNode processor : access.methods) if (processor.name.equals("process"))
                if (hasCallOrHandle(processor, OPTION, "set") && hasCall(processor, "com/mojang/serialization/Codec", "parse")) loadsThroughSetter = true;
        }
        for (String name : nativeAnonymousTypes(options, save)) {
            ClassNode access = definedBytecode(name.replace('/', '.'), exports);
            for (MethodNode processor : access.methods) if (processor.name.equals("process"))
                if (hasCallOrHandle(processor, OPTION, "get") && hasCall(processor, "com/mojang/serialization/Codec", "encodeStart")) savesThroughGetter = true;
        }
        check(loadsThroughSetter, "The actual native options load processor decodes values through OptionInstance.set");
        check(savesThroughGetter, "The actual native options save processor encodes OptionInstance.get");
        report.addProperty("native_options_loading_uses_guarded_setter", true);
        report.addProperty("native_options_saving_uses_guarded_getter", true);
        report.addProperty("options_file_read_or_written_by_audit", false);
    }

    private static List<String> nativeAnonymousTypes(ClassNode options, MethodNode start) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (MethodNode method : reachable(options, start)) for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.startsWith(OPTIONS + "$"))
                result.put(type.desc, true);
        check(!result.isEmpty(), "The native options persistence path still constructs its field processor");
        return List.copyOf(result.keySet());
    }

    private static void auditLightmap(JsonArray exports, JsonObject report) throws Exception {
        ClassNode light = transformed("net.minecraft.client.renderer.LightTexture", exports);
        MethodNode update = method(light, "updateLightTexture", "(F)V");
        List<MethodNode> path = reachable(light, update);
        check(path.stream().anyMatch(method -> hasCall(method, OPTIONS, "gamma")), "The real lightmap still reads the guarded gamma option");
        check(path.stream().anyMatch(method -> hasCall(method, OPTION, "get")), "The real lightmap still reads option values through get");
        check(path.stream().anyMatch(method -> hasCall(method, "net/minecraft/client/renderer/GameRenderer", "getNightVisionScale")),
                "The actual native night-vision brightness path remains present");
        check(path.stream().anyMatch(method -> hasCall(method, "net/minecraft/client/player/LocalPlayer", "getWaterVision")),
                "The actual native water-vision brightness path remains present");
        check(path.stream().anyMatch(method -> hasCall(method, "net/minecraft/client/renderer/LightTexture", "calculateDarknessScale")),
                "The actual native darkness-effect path remains present");
        check(path.stream().anyMatch(method -> hasCall(method, OPTIONS, "darknessEffectScale")),
                "The accessibility darkness-effect scale remains independent");
        report.addProperty("lightmap_reads_guarded_gamma", true);
        report.addProperty("native_night_vision_water_vision_darkness_paths_preserved", true);
    }

    private static void auditOptionalSodium(JsonArray exports, JsonObject report) throws Exception {
        JsonObject fact = new JsonObject(); report.add("optional_sodium", fact);
        boolean loaded = ModList.get().isLoaded("sodium"); fact.addProperty("loaded", loaded);
        fact.addProperty("reeses_loaded", ModList.get().isLoaded("reeses_sodium_options"));
        fact.addProperty("sodium_extra_loaded", ModList.get().isLoaded("sodium_extra"));
        ClassLoader loader = ClientSettingsAudit.class.getClassLoader();
        ClassNode optionalMixin = resourceClass(SODIUM_MIXIN.replace('.', '/'));
        check(optionalMixin.fields.stream().noneMatch(field -> field.desc.contains("net/caffeinemc/"))
                        && optionalMixin.methods.stream().noneMatch(method -> method.desc.contains("net/caffeinemc/")),
                "The optional settings Mixin has no Sodium types in its signatures");
        for (MethodNode method : optionalMixin.methods) for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call) check(!call.owner.startsWith("net/caffeinemc/"),
                    "The optional settings Mixin has no direct Sodium invocation dependency");
        if (!loaded) {
            check(loader.getResource(SODIUM_OPTION.replace('.', '/') + ".class") == null, "Sodium classes really are absent in the no-mods run");
            try { Class.forName(SODIUM_OPTION, false, loader); throw new IllegalStateException("Unexpected Sodium type in the no-mods audit"); }
            catch (ClassNotFoundException expected) { /* Prove absence rather than merely trusting ModList. */ }
            fact.addProperty("status", "passed_real_absence_and_no_hard_dependency"); return;
        }
        ClassNode sodium = transformed(SODIUM_OPTION, exports);
        check(reachesMixin(sodium, method(sodium, "isEnabled", "()Z"), SODIUM_MIXIN),
                "The actual optional settings model reaches its merged disabled-brightness handler");
        ClassNode binding = definedBytecode("net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder", exports);
        boolean getter = false, setter = false;
        for (MethodNode method : binding.methods) if (hasCallOrHandle(method, OPTIONS, "gamma")) {
            for (MethodNode part : reachable(binding, method)) {
                getter |= hasCallOrHandle(part, OPTION, "get"); setter |= hasCallOrHandle(part, OPTION, "set");
            }
        }
        check(getter && setter, "Sodium's actual brightness bindings read and write the guarded vanilla option");
        fact.addProperty("brightness_model_disabled", true);
        fact.addProperty("brightness_binding_reads_and_writes_guarded_vanilla_option", true);
        fact.addProperty("status", "passed_actual_transformed_optional_model_and_binding");
    }

    private static ClassNode transformed(String name, JsonArray exports) throws Exception {
        // Defining without initialization applies the real Mixin transformer without a game/window.
        Class.forName(name, false, ClientSettingsAudit.class.getClassLoader());
        Path path = Path.of(".mixin.out", "class", name.replace('.', '/') + ".class");
        check(Files.isRegularFile(path), "Missing actual transformed-class export " + path);
        byte[] bytes = Files.readAllBytes(path); ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        JsonObject fact = new JsonObject(); fact.addProperty("class", name); fact.addProperty("export", path.toAbsolutePath().normalize().toString());
        fact.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); exports.add(fact);
        return node;
    }

    private static ClassNode definedBytecode(String name, JsonArray exports) throws Exception {
        // Options/its anonymous processors have no Mite Mixin. In a genuinely empty
        // mods folder Mixin therefore need not emit those class files at all. Define
        // the classes first, prefer their transformed export if one exists, and
        // explicitly distinguish native classpath evidence from transformed evidence.
        Class.forName(name, false, ClientSettingsAudit.class.getClassLoader());
        Path path = Path.of(".mixin.out", "class", name.replace('.', '/') + ".class");
        if (Files.isRegularFile(path)) return transformed(name, exports);
        ClassNode node = resourceClass(name.replace('.', '/'));
        JsonObject fact = new JsonObject(); fact.addProperty("class", name);
        fact.addProperty("bytecode_source", "native_classpath_resource_no_mixin_export");
        fact.addProperty("class_defined_without_initialization", true); exports.add(fact);
        return node;
    }

    private static ClassNode resourceClass(String name) throws Exception {
        try (var stream = ClientSettingsAudit.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(stream != null, "Missing compiled audit target " + name); ClassNode node = new ClassNode(); new ClassReader(stream.readAllBytes()).accept(node, 0); return node;
        }
    }

    private static MethodNode method(ClassNode type, String name, String desc) {
        return type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned method " + type.name + "." + name + desc));
    }

    private static boolean reachesMixin(ClassNode type, MethodNode start, String mixin) {
        return reachable(type, start).stream().anyMatch(method -> merged(method, mixin));
    }

    private static boolean merged(MethodNode method, String mixin) {
        return merged(method.visibleAnnotations, mixin) || merged(method.invisibleAnnotations, mixin);
    }

    private static boolean merged(List<AnnotationNode> annotations, String mixin) {
        if (annotations != null) for (AnnotationNode annotation : annotations)
            if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null)
                for (int i = 0; i + 1 < annotation.values.size(); i += 2)
                    if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals(mixin)) return true;
        return false;
    }

    private static boolean hasCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        return false;
    }

    private static boolean hasCallOrHandle(MethodNode method, String owner, String name) {
        if (hasCall(method, owner, name)) return true;
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof InvokeDynamicInsnNode dynamic)
            for (Object argument : dynamic.bsmArgs) if (argument instanceof Handle handle && handle.getOwner().equals(owner) && handle.getName().equals(name)) return true;
        return false;
    }

    private static List<MethodNode> reachable(ClassNode type, MethodNode start) {
        Map<String, MethodNode> seen = new LinkedHashMap<>(); ArrayDeque<MethodNode> pending = new ArrayDeque<>(); pending.add(start);
        while (!pending.isEmpty()) {
            MethodNode current = pending.removeFirst(); if (seen.putIfAbsent(current.name + current.desc, current) != null) continue;
            for (AbstractInsnNode instruction : current.instructions) {
                if (instruction instanceof MethodInsnNode call) enqueue(type, call.owner, call.name, call.desc, pending);
                else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    enqueueHandle(type, dynamic.bsm, pending); for (Object argument : dynamic.bsmArgs) enqueueHandle(type, argument, pending);
                }
            }
        }
        return List.copyOf(seen.values());
    }

    private static void enqueueHandle(ClassNode type, Object argument, ArrayDeque<MethodNode> pending) {
        if (argument instanceof Handle handle) enqueue(type, handle.getOwner(), handle.getName(), handle.getDesc(), pending);
        else if (argument instanceof ConstantDynamic dynamic) {
            enqueueHandle(type, dynamic.getBootstrapMethod(), pending);
            for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) enqueueHandle(type, dynamic.getBootstrapMethodArgument(i), pending);
        }
    }

    private static void enqueue(ClassNode type, String owner, String name, String desc, ArrayDeque<MethodNode> pending) {
        if (owner.equals(type.name)) type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(desc)).forEach(pending::addLast);
    }

    private void write(JsonObject report) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
