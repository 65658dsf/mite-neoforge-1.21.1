package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Datagen verifies actual transformed bow pose branches without any game, world or GL. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SkeletonBowClientAudit implements DataProvider {
    private static final String MIXIN = "io.github.ningmeng123.mite.mixin.client.SkeletonBowModelMixin";
    private final Path output;

    private SkeletonBowClientAudit(Path output) { this.output = output; }

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.enchantmentClientAudit")) return;
        Path report = Path.of(System.getProperty("mite.enchantmentClientAuditOutput", "build/verification/enchantment-client-audit.json"));
        event.getGenerator().addProvider(true, new SkeletonBowClientAudit(report.resolveSibling("skeleton-bow-client-audit.json")));
    }

    @Override public String getName() { return "Transformed native skeleton bow pose audit without rendering"; }

    @Override
    public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("visual_appearance_passed", false);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "Enable transformed-class export for the actual client model audit");
            Class.forName("net.minecraft.client.model.SkeletonModel", false, getClass().getClassLoader());
            Path export = Path.of(".mixin.out", "class", "net/minecraft/client/model/SkeletonModel.class");
            check(Files.isRegularFile(export), "The real transformed native skeleton model was exported");
            ClassNode model = read(Files.readAllBytes(export));
            List<MethodNode> handlers = model.methods.stream().filter(SkeletonBowClientAudit::merged).toList();
            check(handlers.size() == 1, "Exactly one shared material bow recognition handler was applied");
            MethodNode handler = handlers.getFirst();
            boolean bowClass = false;
            for (var instruction : handler.instructions) if (instruction instanceof TypeInsnNode type
                    && type.getOpcode() == Opcodes.INSTANCEOF && type.desc.equals("net/minecraft/world/item/BowItem")) bowClass = true;
            check(bowClass, "Actual transformed recognition accepts the BowItem family");
            JsonArray methods = new JsonArray();
            for (var signature : List.of(List.of("prepareMobModel", "(Lnet/minecraft/world/entity/Mob;FFF)V"),
                    List.of("setupAnim", "(Lnet/minecraft/world/entity/Mob;FFFFF)V"))) {
                MethodNode method = model.methods.stream().filter(m -> m.name.equals(signature.getFirst()) && m.desc.equals(signature.getLast())).findFirst().orElseThrow();
                long redirects = 0, exactBowChecks = 0;
                for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals(model.name) && call.name.equals(handler.name) && call.desc.equals(handler.desc)) redirects++;
                    if (call.owner.equals("net/minecraft/world/item/ItemStack") && call.name.equals("is")
                            && call.desc.equals("(Lnet/minecraft/world/item/Item;)Z")) exactBowChecks++;
                }
                check(redirects == 1 && exactBowChecks == 0, "Native " + method.name + " uses the transformed BowItem predicate exactly once");
                JsonObject fact = new JsonObject(); fact.addProperty("method", method.name); fact.addProperty("descriptor", method.desc);
                fact.addProperty("material_bow_calls", redirects); fact.addProperty("remaining_exact_item_checks", exactBowChecks); methods.add(fact);
            }
            try (var input = getClass().getClassLoader().getResourceAsStream("io/github/ningmeng123/mite/client/mob/LegacySkeletonModel.class")) {
                check(input != null, "Source skeleton model class is available");
                ClassNode legacy = read(input.readAllBytes());
                check(legacy.methods.stream().noneMatch(m -> m.name.equals("setupAnim") || m.name.equals("prepareMobModel")),
                        "Source geometry inherits native animated pose instead of overriding it with static angles");
            }
            report.add("transformed_pose_methods", methods);
            report.addProperty("source_model_inherits_native_animation", true);
            report.addProperty("native_model_shared_by_source_and_modern_skeletons", true);
            report.addProperty("status", "passed_transformed_client_pose_audit_without_rendering");
            write(report); return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed"); report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static boolean merged(MethodNode method) { return merged(method.visibleAnnotations) || merged(method.invisibleAnnotations); }
    private static boolean merged(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null)
            for (int index = 0; index + 1 < annotation.values.size(); index += 2)
                if (annotation.values.get(index).equals("mixin") && annotation.values.get(index + 1).equals(MIXIN)) return true;
        return false;
    }

    private static ClassNode read(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void write(JsonObject report) throws Exception {
        Path absolute = output.toAbsolutePath().normalize(); Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
