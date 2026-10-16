package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
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
            report.addProperty("status", "passed_transformed_advancement_screen_title_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Exception failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (IOException secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
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
