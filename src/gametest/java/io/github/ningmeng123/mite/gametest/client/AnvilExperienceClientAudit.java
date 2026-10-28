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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.locale.Language;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks the real transformed anvil labels without constructing a game, screen or GL context. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AnvilExperienceClientAudit implements DataProvider {
    private static final String MIXIN = "io.github.ningmeng123.mite.mixin.client.AnvilExperienceScreenMixin";
    private static final String EXPERIENCE = "io/github/ningmeng123/mite/crafting/AnvilExperienceAccess";
    private static final String COST_KEY = "gui.mite.anvil.experience_cost";
    private final Path output;

    private AnvilExperienceClientAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.anvilExperienceAudit")) return;
        event.getGenerator().addProvider(true, new AnvilExperienceClientAudit(Path.of(System.getProperty(
                "mite.anvilExperienceAuditOutput", "build/verification/book-anvil-client-audit.json"))));
    }

    @Override public String getName() { return "Transformed anvil XP labels and uncapped point fee without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("screen_created", false);
        report.addProperty("target_initialization_requested", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("visual_appearance_passed", false);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "Enable Mixin transformed-class exports for this audit");
            ClassNode screen = transformed("net.minecraft.client.gui.screens.inventory.AnvilScreen", report);
            MethodNode labels = method(screen, "renderLabels", "(Lnet/minecraft/client/gui/GuiGraphics;II)V");
            MethodNode cost = mergedHandler(screen, "mite$showExperiencePoints");
            MethodNode noCap = mergedHandler(screen, "mite$pointFeesHaveNoLevelCap");
            requireCall(screen, labels, screen.name, cost.name);
            requireCall(screen, labels, screen.name, noCap.name);
            requireCall(screen, cost, EXPERIENCE, "mite$usesExperiencePoints");
            requireCall(screen, cost, EXPERIENCE, "mite$experienceCost");
            requireCall(screen, noCap, EXPERIENCE, "mite$ignoresLevelLimit");
            check(hasString(cost, COST_KEY), "The actual merged point-label handler uses the XP translation");
            check(hasString(labels, "container.repair.cost"), "The native label remains for operations outside XP mode");
            check(hasField(labels, "net/minecraft/world/entity/player/Abilities", "instabuild"),
                    "The point-mode bypass wraps the native creative exception for the level cap");
            requireCall(screen, labels, "net/minecraft/world/inventory/Slot", "mayPickup");
            requireCall(screen, labels, "net/minecraft/client/gui/GuiGraphics", "drawString");
            report.addProperty("actual_transformed_xp_label_handler_applied", true);
            report.addProperty("actual_transformed_book_and_repair_level_cap_bypass_handler_applied", true);
            report.addProperty("point_mode_and_server_point_cost_reachable_from_labels", true);
            report.addProperty("native_result_pickup_check_controls_affordability_color", true);
            report.addProperty("native_cost_label_retained_outside_point_mode", true);

            JsonArray languages = new JsonArray();
            for (String locale : List.of("zh_cn", "en_us")) {
                String name = "assets/mite/lang/" + locale + ".json";
                byte[] bytes = resource(name);
                Map<String, String> translations = new LinkedHashMap<>();
                try (var stream = new java.io.ByteArrayInputStream(bytes)) {
                    Language.loadFromJson(stream, translations::put);
                }
                String text = translations.get(COST_KEY);
                String expected = locale.equals("zh_cn") ? "附魔花费：%s XP" : "Enchantment cost: %s XP";
                check(expected.equals(text), "The native language parser resolves the exact XP label for " + locale);
                for (int points : new int[] {8000, 64000}) {
                    String formatted = String.format(Locale.ROOT, text, points);
                    check(formatted.contains(Integer.toString(points)) && formatted.endsWith(" XP") && !formatted.contains("%s"),
                            "The XP label formats the actual point quote in " + locale);
                }
                JsonObject fact = new JsonObject();
                fact.addProperty("locale", locale);
                fact.addProperty("resource", name);
                fact.addProperty("resource_sha256", sha256(bytes));
                fact.addProperty("parser", "net.minecraft.locale.Language.loadFromJson");
                fact.addProperty("resolved_cost_format", text);
                fact.addProperty("equipment_example", String.format(Locale.ROOT, text, 8000));
                fact.addProperty("book_example", String.format(Locale.ROOT, text, 8000));
                languages.add(fact);
            }
            report.add("languages", languages);
            report.addProperty("status", "passed_transformed_anvil_xp_labels_and_native_language_parser_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static ClassNode transformed(String name, JsonObject report) throws Exception {
        Class.forName(name, false, AnvilExperienceClientAudit.class.getClassLoader());
        Path export = Path.of(".mixin.out", "class", name.replace('.', '/') + ".class");
        check(Files.isRegularFile(export), "Missing transformed client export " + export);
        byte[] bytes = Files.readAllBytes(export);
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        report.addProperty("target_class", name);
        report.addProperty("transformed_export", export.toAbsolutePath().normalize().toString());
        report.addProperty("transformed_sha256", sha256(bytes));
        return type;
    }

    private static MethodNode mergedHandler(ClassNode type, String fragment) {
        return type.methods.stream().filter(method -> method.name.contains(fragment)
                && (merged(method.visibleAnnotations) || merged(method.invisibleAnnotations))).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing actual merged handler " + fragment));
    }

    private static boolean merged(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (var annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") || annotation.values == null) continue;
            for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
                if (annotation.values.get(index).equals("mixin") && annotation.values.get(index + 1).equals(MIXIN)) return true;
            }
        }
        return false;
    }

    private static void requireCall(ClassNode type, MethodNode start, String owner, String name) {
        Map<String, MethodNode> seen = new LinkedHashMap<>();
        ArrayDeque<MethodNode> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            MethodNode method = pending.removeFirst();
            if (seen.putIfAbsent(method.name + method.desc, method) != null) continue;
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(owner) && call.name.equals(name)) return;
                if (call.owner.equals(type.name)) type.methods.stream()
                        .filter(candidate -> candidate.name.equals(call.name) && candidate.desc.equals(call.desc)).forEach(pending::addLast);
            }
        }
        throw new IllegalStateException("Missing reachable transformed call " + start.name + " -> " + owner + "." + name);
    }

    private static boolean hasString(MethodNode method, String value) {
        for (var instruction : method.instructions) if (instruction instanceof LdcInsnNode ldc && value.equals(ldc.cst)) return true;
        return false;
    }

    private static boolean hasField(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.owner.equals(owner) && field.name.equals(name)) return true;
        return false;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned method " + type.name + "." + name + descriptor));
    }

    private static byte[] resource(String name) throws Exception {
        try (var stream = AnvilExperienceClientAudit.class.getClassLoader().getResourceAsStream(name)) {
            check(stream != null, "Missing runtime resource " + name);
            return stream.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void write(JsonObject report) throws Exception {
        Path absolute = output.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
