package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.locale.Language;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Real transformed client classes and native language parser; no game, world or GL initialization. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class EnchantmentClientAudit implements DataProvider {
    private static final String ROOT = "io/github/ningmeng123/mite/";
    private static final String MIXINS = "io.github.ningmeng123.mite.mixin.";
    private static final Set<String> ENCHANTMENTS = Set.of("arrow_recovery", "stun", "fishing_fortune", "fertility", "tree_felling",
            "vampiric", "speed", "regeneration", "free_action", "quickness", "true_flight", "poison", "disarming", "piercing", "harvesting", "butchering", "endurance");
    private static final Set<String> UI = Set.of("cost", "points", "energy", "available");
    private final Path output;

    private EnchantmentClientAudit(Path output) { this.output = output; }

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.enchantmentClientAudit")) return;
        Path report = Path.of(System.getProperty("mite.enchantmentClientAuditOutput", "build/verification/enchantment-client-audit.json"));
        event.getGenerator().addProvider(true, new EnchantmentClientAudit(report));
    }

    @Override public String getName() { return "R196 transformed enchantment screen, bow FOV and language audit without rendering"; }

    @Override
    public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("target_initialization_requested", false);
        report.addProperty("visual_appearance_passed", false);
        report.addProperty("property_callbacks_executed", false);
        JsonArray targets = new JsonArray(), languageFacts = new JsonArray();
        report.add("targets", targets); report.add("languages", languageFacts);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "Mixin transformed-class export must be enabled");
            ClassNode screen = transformed("net.minecraft.client.gui.screens.inventory.EnchantmentScreen", targets);
            check(hasMerged(screen, MIXINS + "EnchantmentScreenMixin"), "The source point-cost screen Mixin was actually applied");
            MethodNode background = method(screen, "renderBg", "(Lnet/minecraft/client/gui/GuiGraphics;FII)V");
            MethodNode render = method(screen, "render", "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V");
            requireCall(screen, background, ROOT + "enchantment/R196EnchantmentTable", "experienceCost");
            requireCall(screen, background, ROOT + "enchantment/R196EnchantmentMenuAccess", "mite$usesSourceEnchanting");
            requireCall(screen, render, ROOT + "enchantment/R196EnchantmentTable", "experienceCost");
            requireCall(screen, background, ROOT + "enchantment/R196EnchantmentTable", "hasLapis");
            requireCall(screen, render, "net/minecraft/client/gui/GuiGraphics", "renderComponentTooltip");
            Set<String> keys = translationKeys(screen, background);
            keys.addAll(translationKeys(screen, render));
            Set<String> expectedKeys = new LinkedHashSet<>(); UI.forEach(name -> expectedKeys.add("gui.mite.enchant." + name));
            check(keys.equals(expectedKeys), "The source screen uses only cost plus the three requested tooltip keys");
            Set<String> tooltipKeys = translationKeys(screen, render);
            check(tooltipKeys.equals(Set.of("gui.mite.enchant.energy", "gui.mite.enchant.available", "gui.mite.enchant.points")),
                    "Only energy, available points and required points remain in the actual transformed tooltip");
            report.addProperty("tooltip_keys_exactly_three", true);
            report.addProperty("native_lapis_affordability_checked", true);
            JsonArray screenKeys = new JsonArray(); keys.forEach(screenKeys::add); report.add("screen_translation_keys", screenKeys);
            report.addProperty("source_screen_cost_and_tooltip_paths", true);

            ClassNode clientPlayer = transformed("net.minecraft.client.player.AbstractClientPlayer", targets);
            check(hasMerged(clientPlayer, MIXINS + "client.EnchantedBowFovMixin"), "The Quickness FOV Mixin was actually applied");
            MethodNode fov = method(clientPlayer, "getFieldOfViewModifier", "()F");
            requireCall(clientPlayer, fov, ROOT + "enchantment/RangedEnchantments", "pullTicks");
            long fovHandlers = clientPlayer.methods.stream().filter(m -> merged(m, MIXINS + "client.EnchantedBowFovMixin")).count();
            check(fovHandlers == 2, "The Quickness duration and reinforced-bow FOV handlers both exist");
            report.addProperty("quickness_fov_handlers", fovHandlers);

            // This class need not have any Mixin. Define it without running its large native static initializer.
            Class<?> propertyClass = Class.forName("net.minecraft.client.renderer.item.ItemProperties", false, getClass().getClassLoader());
            var register = java.util.Arrays.stream(propertyClass.getDeclaredMethods()).filter(m -> m.getName().equals("register")
                    && Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 3).toList();
            check(register.size() == 1, "Native public item-property registration API resolves in the real client loader");
            JsonObject propertyTarget = new JsonObject(); propertyTarget.addProperty("class", propertyClass.getName());
            propertyTarget.addProperty("initialized", false); propertyTarget.addProperty("public_registration_methods", register.size()); targets.add(propertyTarget);
            auditPropertyRegistration(report);

            ENCHANTMENTS.forEach(name -> expectedKeys.add("enchantment.mite." + name));
            for (String locale : List.of("en_us", "zh_cn")) {
                String path = "assets/mite/lang/" + locale + ".json";
                byte[] bytes = resource(path);
                Map<String, String> translations = new LinkedHashMap<>();
                try (var stream = new java.io.ByteArrayInputStream(bytes)) { Language.loadFromJson(stream, translations::put); }
                for (String key : expectedKeys) {
                    check(translations.containsKey(key), "Missing native-parsed translation " + locale + ": " + key);
                    check(!translations.get(key).isBlank() && !translations.get(key).equals(key), "Unresolved native-parsed translation " + locale + ": " + key);
                }
                for (String suffix : List.of("cost", "points", "energy", "available")) {
                    String value = translations.get("gui.mite.enchant." + suffix);
                    check(value.contains("%s") && !String.format(java.util.Locale.ROOT, value, 123).contains("%s"), "Point display formats its actual numeric argument in " + locale);
                }
                JsonObject fact = new JsonObject(); fact.addProperty("locale", locale); fact.addProperty("resource", path);
                fact.addProperty("sha256", sha256(bytes)); fact.addProperty("parser", "net.minecraft.locale.Language.loadFromJson");
                fact.addProperty("required_keys_loaded", expectedKeys.size()); fact.addProperty("total_keys_loaded", translations.size());
                fact.addProperty("numeric_placeholders_formatted", 4); languageFacts.add(fact);
            }
            report.addProperty("status", "passed_transformed_client_classes_and_native_language_parser_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed"); report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void auditPropertyRegistration(JsonObject report) throws Exception {
        // Defining WeaponRendering is safe, but executing its setup/renderer callbacks would initialize client globals.
        String name = "io.github.ningmeng123.mite.client.WeaponRendering";
        Class.forName(name, false, EnchantmentClientAudit.class.getClassLoader());
        ClassNode type = new ClassNode(); new ClassReader(resource(name.replace('.', '/') + ".class")).accept(type, 0);
        MethodNode setup = method(type, "setup", "(Lnet/neoforged/fml/event/lifecycle/FMLClientSetupEvent;)V");
        long registrations = 0, sourcePullCalls = 0; boolean vanillaBow = false;
        for (MethodNode method : reachable(type, setup)) for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call) {
                if (call.owner.equals("net/minecraft/client/renderer/item/ItemProperties") && call.name.equals("register")) registrations++;
                if (call.owner.equals(ROOT + "enchantment/RangedEnchantments") && call.name.equals("pull")) sourcePullCalls++;
            }
            if (insn instanceof FieldInsnNode field && field.owner.equals("net/minecraft/world/item/Items") && field.name.equals("BOW")) vanillaBow = true;
        }
        check(registrations == 4, "Native bow pull plus reinforced pull/pulling/material registration sites are reachable");
        check(sourcePullCalls == 2 && vanillaBow, "Native and reinforced bow model callbacks both call the shared Quickness pull helper");
        JsonObject fact = new JsonObject(); fact.addProperty("registration_call_sites", registrations);
        fact.addProperty("source_pull_callback_call_sites", sourcePullCalls); fact.addProperty("vanilla_bow_registration_present", vanillaBow);
        fact.addProperty("callbacks_executed", false); fact.addProperty("class_resource_sha256", sha256(resource(name.replace('.', '/') + ".class")));
        report.add("bow_model_registration_bytecode", fact);
    }

    private static ClassNode transformed(String name, JsonArray facts) throws Exception {
        Class.forName(name, false, EnchantmentClientAudit.class.getClassLoader());
        Path export = Path.of(".mixin.out", "class", name.replace('.', '/') + ".class");
        check(Files.isRegularFile(export), "Missing transformed client export " + export);
        byte[] bytes = Files.readAllBytes(export); ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        JsonObject fact = new JsonObject(); fact.addProperty("class", name); fact.addProperty("initialized", false);
        fact.addProperty("transformed_export", export.toAbsolutePath().normalize().toString()); fact.addProperty("sha256", sha256(bytes)); facts.add(fact);
        return node;
    }

    private static Set<String> translationKeys(ClassNode type, MethodNode start) {
        Set<String> result = new LinkedHashSet<>();
        for (MethodNode method : reachable(type, start)) for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String key && key.startsWith("gui.mite.enchant.")) result.add(key);
        return result;
    }

    private static void requireCall(ClassNode type, MethodNode start, String owner, String name) {
        for (MethodNode method : reachable(type, start)) for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return;
        throw new IllegalStateException("Missing reachable transformed call " + type.name + "." + start.name + " -> " + owner + "." + name);
    }

    private static List<MethodNode> reachable(ClassNode type, MethodNode start) {
        Map<String, MethodNode> seen = new LinkedHashMap<>(); ArrayDeque<MethodNode> pending = new ArrayDeque<>(); pending.add(start);
        while (!pending.isEmpty()) {
            MethodNode current = pending.removeFirst();
            if (seen.putIfAbsent(current.name + current.desc, current) != null) continue;
            for (AbstractInsnNode instruction : current.instructions) {
                if (instruction instanceof MethodInsnNode call) enqueue(type, call.owner, call.name, call.desc, pending);
                else if (instruction instanceof InvokeDynamicInsnNode call) {
                    handle(type, call.bsm, pending); for (Object argument : call.bsmArgs) handle(type, argument, pending);
                }
            }
        }
        return List.copyOf(seen.values());
    }

    private static void handle(ClassNode type, Object value, ArrayDeque<MethodNode> pending) {
        if (value instanceof Handle handle) enqueue(type, handle.getOwner(), handle.getName(), handle.getDesc(), pending);
        else if (value instanceof ConstantDynamic constant) {
            handle(type, constant.getBootstrapMethod(), pending);
            for (int index = 0; index < constant.getBootstrapMethodArgumentCount(); index++) handle(type, constant.getBootstrapMethodArgument(index), pending);
        }
    }

    private static void enqueue(ClassNode type, String owner, String name, String desc, ArrayDeque<MethodNode> pending) {
        if (owner.equals(type.name)) type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(desc)).forEach(pending::addLast);
    }

    private static boolean hasMerged(ClassNode type, String mixin) { return type.methods.stream().anyMatch(method -> merged(method, mixin)); }
    private static boolean merged(MethodNode method, String mixin) { return merged(method.visibleAnnotations, mixin) || merged(method.invisibleAnnotations, mixin); }
    private static boolean merged(List<AnnotationNode> annotations, String mixin) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null)
            for (int index = 0; index + 1 < annotation.values.size(); index += 2)
                if (annotation.values.get(index).equals("mixin") && annotation.values.get(index + 1).equals(mixin)) return true;
        return false;
    }

    private static MethodNode method(ClassNode type, String name, String desc) {
        return type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned method " + type.name + "." + name + desc));
    }

    private static byte[] resource(String name) throws Exception {
        try (var stream = EnchantmentClientAudit.class.getClassLoader().getResourceAsStream(name)) {
            check(stream != null, "Missing runtime classpath resource " + name); return stream.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void write(JsonObject report) throws Exception {
        Path absolute = output.toAbsolutePath().normalize(); Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
