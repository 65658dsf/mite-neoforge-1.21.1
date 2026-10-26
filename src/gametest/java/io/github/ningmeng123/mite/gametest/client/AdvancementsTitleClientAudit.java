package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.advancement.StructureAdvancements;
import io.github.ningmeng123.mite.advancement.StructureAdvancements.Entry;
import io.github.ningmeng123.mite.client.StructureProgressTooltip;
import io.github.ningmeng123.mite.network.StructureProgressPayload;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks the real transformed advancement screen without initializing Minecraft or creating a window. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AdvancementsTitleClientAudit implements DataProvider {
    private static final String TARGET = "net.minecraft.client.gui.screens.advancements.AdvancementsScreen";
    private static final String MIXIN = "io.github.ningmeng123.mite.mixin.client.MiteAdvancementsTitleMixin";
    private static final String COMPONENT = "Lnet/minecraft/network/chat/Component;";
    private final Path output;

    private AdvancementsTitleClientAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.advancementsTitleAudit")) return;
        event.getGenerator().addProvider(true, new AdvancementsTitleClientAudit(Path.of(System.getProperty(
                "mite.advancementsTitleAuditOutput", "build/verification/advancements-title-audit.json"))));
    }

    @Override public String getName() { return "Transformed MITE advancement screen title audit without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("target", TARGET);
        report.addProperty("target_initialization_requested", false);
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "The audit requires actual transformed-class export");
            Class.forName(TARGET, false, getClass().getClassLoader());
            Path export = Path.of(".mixin.out", "class", TARGET.replace('.', '/') + ".class");
            check(Files.isRegularFile(export), "The advancement screen was transformed and exported");
            ClassNode type = new ClassNode();
            new ClassReader(Files.readAllBytes(export)).accept(type, 0);
            var handlers = type.methods.stream().filter(method -> method.name.endsWith("mite$replaceTitle")
                    && (merged(method.visibleAnnotations) || merged(method.invisibleAnnotations))).toList();
            check(handlers.size() == 1, "Exactly one title replacement handler is merged");
            MethodNode handler = handlers.getFirst();
            check(literal(handler, "MITE") && calls(handler, "net/minecraft/network/chat/Component", "literal")
                            && titleField(type, handler, Opcodes.PUTSTATIC),
                    "The actual merged handler assigns the literal MITE to the screen TITLE");
            MethodNode initializer = method(type, "<clinit>", "()V");
            check(calls(initializer, type.name, handler.name), "The real static initializer invokes the title replacement");
            MethodNode constructor = method(type, "<init>",
                    "(Lnet/minecraft/client/multiplayer/ClientAdvancements;Lnet/minecraft/client/gui/screens/Screen;)V");
            check(titleField(type, constructor, Opcodes.GETSTATIC), "Screen narration reads the same replacement TITLE");
            MethodNode init = method(type, "init", "()V");
            check(titleField(type, init, Opcodes.GETSTATIC)
                            && calls(init, "net/minecraft/client/gui/layouts/HeaderAndFooterLayout", "addTitleHeader"),
                    "The native top header reads the replacement TITLE");
            MethodNode renderWindow = method(type, "renderWindow", "(Lnet/minecraft/client/gui/GuiGraphics;II)V");
            check(titleField(type, renderWindow, Opcodes.GETSTATIC), "The empty-tab window fallback uses the replacement TITLE");
            report.addProperty("title", "MITE");
            report.addProperty("native_header_uses_replacement", true);
            report.addProperty("screen_title_uses_replacement", true);
            report.addProperty("transformed_export", export.toAbsolutePath().normalize().toString());
            structureTooltips(report);
            report.addProperty("status", "passed_transformed_advancement_screen_and_structure_tooltips_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Exception failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (IOException secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private void structureTooltips(JsonObject report) throws Exception {
        String target = "net.minecraft.client.gui.screens.advancements.AdvancementWidget";
        Class<?> widget = Class.forName(target, false, getClass().getClassLoader());
        var injected = java.util.Arrays.stream(widget.getDeclaredMethods())
                .filter(method -> method.getName().endsWith("mite$showCurrentRequirements")).toList();
        check(injected.size() == 1, "The actual client widget contains the dynamic structure tooltip handler");
        check(!Modifier.isFinal(widget.getDeclaredField("description").getModifiers())
                        && !Modifier.isFinal(widget.getDeclaredField("width").getModifiers()),
                "Mixin made the actual tooltip lines and width replaceable");
        Path export = Path.of(".mixin.out", "class", target.replace('.', '/') + ".class");
        ClassNode type = new ClassNode();
        new ClassReader(Files.readAllBytes(export)).accept(type, 0);
        var hover = method(type, "drawHover", "(Lnet/minecraft/client/gui/GuiGraphics;IIFII)V");
        check(calls(hover, type.name, injected.getFirst().getName()),
                "Native drawHover invokes the merged dynamic tooltip handler");

        Entry entry = Entry.SHIPWRECK;
        for (long value : new long[]{25_000L, 50_000L, 50_001L}) {
            var snapshot = new StructureProgressPayload(StructureAdvancements.ALL_ENABLED, 32L, 255,
                    Map.of(Metric.OCEAN_SAIL_CM, value));
            var lines = StructureProgressTooltip.lines(entry, snapshot);
            check(lines.size() == 5, "A shipwreck displays sharing, enabled, date, exact distance and new-chunk guidance");
            var date = translation(lines.get(2), "structure_progress.mite.date");
            check(date.getArgs().length == 2 && date.getArgs()[0].equals(32L) && date.getArgs()[1].equals(32L),
                    "Date tooltip exposes the actual and required day");
            var distance = translation(lines.get(3), "structure_progress.mite.distance");
            check(distance.getArgs()[1].equals(String.format(java.util.Locale.ROOT, "%.2f", value / 100D))
                            && distance.getArgs()[2].equals("500.01"),
                    "The tooltip retains centimeters instead of rounding a strict 500-block boundary to complete");
            boolean complete = value >= 50_001L;
            check(lines.get(3).getString().startsWith(complete ? "✓ " : "□ "),
                    "500.00 blocks stays unchecked; 500.01 checks the requirement");
            check(lines.get(3).getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(
                            complete ? ChatFormatting.GREEN : ChatFormatting.GRAY)),
                    "The current requirement color agrees with its exact completion state");
        }

        var maxima = new EnumMap<Metric, Long>(Metric.class);
        for (Metric metric : Metric.values()) maxima.put(metric, metric.cap());
        var complete = new StructureProgressPayload(StructureAdvancements.ALL_ENABLED, 60L, 255, maxima);
        check(Entry.values().length == 21, "Every non-exempt structure family has a tooltip");
        for (String locale : List.of("en_us", "zh_cn")) {
            String path = "assets/mite/lang/" + locale + ".json";
            JsonObject language;
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                check(stream != null, "The client classpath contains " + path);
                language = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            for (Entry structure : Entry.values()) {
                check(language.has("advancement.mite.structures." + structure.path() + ".title")
                                && language.has("advancement.mite.structures." + structure.path() + ".description"),
                        "Both locales name every structure and its static requirements");
                var lines = StructureProgressTooltip.lines(structure, complete);
                check(lines.size() == 4 + structure.flagRequirements().size() + structure.definition().requirements().size(),
                        "Tooltip contains every shared flag and metric: " + structure);
                for (int i = 1; i < lines.size() - 1; i++) check(lines.get(i).getString().startsWith("✓ "),
                        "A complete snapshot checks every condition: " + structure);
                for (Component line : lines) translationsExist(line, language);
            }
            for (String key : List.of("enabled", "required_date", "required_distance", "required_count", "new_chunks"))
                check(language.has("structure_progress.mite." + key), "Static descriptions have localized requirement formatters");
        }
        report.addProperty("structure_widget_transformed", true);
        report.addProperty("native_hover_calls_dynamic_handler", true);
        report.addProperty("structure_families_checked", Entry.values().length);
        report.addProperty("strict_distance_samples_cm", "25000,50000,50001");
        report.addProperty("current_target_and_completion_checked", true);
        report.addProperty("locales_checked", "en_us,zh_cn");
        report.addProperty("structure_widget_export", export.toAbsolutePath().normalize().toString());
    }

    private static TranslatableContents translation(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents text && text.getKey().equals(key)) return text;
        for (Component sibling : component.getSiblings()) {
            try { return translation(sibling, key); } catch (IllegalStateException ignored) { }
        }
        throw new IllegalStateException("Missing tooltip translation " + key);
    }

    private static void translationsExist(Component component, JsonObject language) {
        if (component.getContents() instanceof TranslatableContents text) {
            check(language.has(text.getKey()) && !language.get(text.getKey()).getAsString().isBlank(),
                    "Every tooltip translation is present: " + text.getKey());
            for (Object argument : text.getArgs()) if (argument instanceof Component nested) translationsExist(nested, language);
        }
        for (Component sibling : component.getSiblings()) translationsExist(sibling, language);
    }

    private static boolean merged(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations)
            if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null)
                for (int i = 0; i + 1 < annotation.values.size(); i += 2)
                    if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals(MIXIN)) return true;
        return false;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing native method " + name + descriptor));
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        return false;
    }

    private static boolean literal(MethodNode method, String text) {
        for (var instruction : method.instructions)
            if (instruction instanceof LdcInsnNode constant && text.equals(constant.cst)) return true;
        return false;
    }

    private static boolean titleField(ClassNode type, MethodNode method, int opcode) {
        for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == opcode
                    && field.owner.equals(type.name) && field.name.equals("TITLE") && field.desc.equals(COMPONENT)) return true;
        return false;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private void write(JsonObject report) throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
