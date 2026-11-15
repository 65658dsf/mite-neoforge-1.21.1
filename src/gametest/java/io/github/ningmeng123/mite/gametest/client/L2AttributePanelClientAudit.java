package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.L2AttributePanel;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Actual optional L2 panel hooks and display-only rows without creating a window. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class L2AttributePanelClientAudit implements DataProvider {
    private static final String SCREEN = "dev.xkmc.l2tabs.tabs.contents.BaseAttributeScreen";
    private static final String ENTRY = "dev.xkmc.l2tabs.init.data.AttrDispEntry";
    private static final String HELPER = "io/github/ningmeng123/mite/client/L2AttributePanel";
    private static final String MIXINS = "io.github.ningmeng123.mite.mixin.client.";
    private final Path output;

    private L2AttributePanelClientAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.l2AttributePanelAudit")) return;
        Path output = Path.of(System.getProperty("mite.l2AttributePanelAuditOutput",
                "build/verification/l2-attribute-panel-client-audit.json"));
        event.getGenerator().addProvider(true, new L2AttributePanelClientAudit(output));
    }

    @Override public String getName() { return "Optional L2 attribute panel hooks and display rows without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        var report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("visual_appearance_tested", false);
        report.addProperty("screen_initialization_requested", false);
        report.addProperty("server_player_constructor_invoked", false);
        try {
            int registeredAttributes = BuiltInRegistries.ATTRIBUTE.size();
            var melee = L2AttributePanel.MELEE_REACH;
            check(BuiltInRegistries.ATTRIBUTE.getKey(melee.value()) == null, "The melee display attribute has no registry key");
            check(!melee.isBound() || melee.unwrapKey().isEmpty(), "The melee display holder has no registered identity");
            List<?> empty = List.of();
            check(L2AttributePanel.entries(null, empty) == empty, "Nonplayer rows pass through without loading optional metadata");
            check(L2AttributePanel.value(null, Attributes.ATTACK_DAMAGE) == null, "Unrelated nonplayer attributes use the native fallback");
            report.addProperty("base_helper_loaded_without_optional_entry_resolution", true);

            Class<?> entryClass;
            try { entryClass = Class.forName(ENTRY, false, getClass().getClassLoader()); }
            catch (ClassNotFoundException absent) {
                check(BuiltInRegistries.ATTRIBUTE.size() == registeredAttributes, "Loading the base helper preserves the attribute registry");
                report.addProperty("optional_l2_loaded", false);
                report.addProperty("attribute_registry_preserved", true);
                report.addProperty("status", "passed_without_optional_l2");
                write(report);
                return CompletableFuture.completedFuture(null);
            }
            report.addProperty("optional_l2_loaded", true);
            check(Boolean.getBoolean("mixin.debug.export"), "Actual transformed-class export is enabled");
            Class.forName(SCREEN, false, getClass().getClassLoader());
            ClassNode screen = transformed(SCREEN);
            ClassNode entry = transformed(ENTRY);
            auditScreen(screen, report);
            auditEntries(entryClass, entry, report);
            check(BuiltInRegistries.ATTRIBUTE.size() == registeredAttributes
                    && BuiltInRegistries.ATTRIBUTE.getKey(melee.value()) == null,
                    "Display row creation leaves the real attribute registry intact");
            report.addProperty("attribute_registry_preserved", true);
            report.addProperty("status", "passed_optional_l2_panel_hooks_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void auditScreen(ClassNode screen, JsonObject report) {
        MethodNode render = method(screen, "render",
                "(Lnet/minecraft/client/gui/GuiGraphics;IIFLnet/minecraft/world/entity/LivingEntity;Ljava/util/List;)V");
        MethodNode wrapper = merged(screen, MIXINS + "L2AttributePanelMixin", "mite$displayEffectiveRange");
        check(reachable(screen, render).contains(wrapper), "The actual list renderer reaches the merged value wrapper");
        check(calls(wrapper, HELPER, "value"), "The value wrapper reads the shared effective range helper");
        check(calls(wrapper, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call"),
                "The wrapper retains the original attribute query");
        boolean nullFallback = false;
        for (var instruction : wrapper.instructions) if (instruction instanceof JumpInsnNode jump
                && (jump.getOpcode() == Opcodes.IFNULL || jump.getOpcode() == Opcodes.IFNONNULL)) nullFallback = true;
        check(nullFallback, "A null projection selects the ordinary attribute fallback");
        check(reachable(screen, render).stream().anyMatch(node -> calls(node,
                "net/minecraft/world/entity/LivingEntity", "getAttributeValue")),
                "The actual rendering call graph retains native attribute reads");

        MethodNode details = method(screen, "getAttributeDetail",
                "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/Holder;)Ljava/util/List;");
        MethodNode explanation = merged(screen, MIXINS + "L2AttributePanelMixin", "mite$explainEffectiveRange");
        check(calls(details, screen.name, explanation.name), "The actual detail method invokes the merged handler");
        check(callIndex(details, screen.name, explanation.name)
                        < callIndex(details, "net/minecraft/world/entity/LivingEntity", "getAttribute"),
                "Effective descriptions are selected before the native attribute calculation");
        check(calls(explanation, HELPER, "handles") && calls(explanation, HELPER, "details")
                        && calls(explanation, "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", "setReturnValue"),
                "The detail handler cancels only through the shared range projection");
        check(calls(explanation, "net/minecraft/client/gui/screens/Screen", "hasShiftDown"),
                "The detail handler reads the real client Shift state");
        report.addProperty("actual_renderer_reaches_value_projection", true);
        report.addProperty("unrelated_attributes_retain_native_fallback", true);
        report.addProperty("actual_detail_head_reaches_range_explanation", true);
    }

    private static void auditEntries(Class<?> entryClass, ClassNode entry, JsonObject report) throws Exception {
        MethodNode get = method(entry, "get", "(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;");
        MethodNode handler = merged(entry, MIXINS + "L2AttributeEntriesMixin", "mite$includeEffectiveMeleeRange");
        check((handler.access & Opcodes.ACC_STATIC) != 0 && calls(get, entry.name, handler.name)
                        && calls(handler, HELPER, "entries"),
                "The actual static list return invokes the merged entry hook");
        Object metadata = entryClass.getConstructor(boolean.class, int.class, double.class).newInstance(false, 9000, 0D);
        List<?> original = List.of(Pair.of(Attributes.MAX_HEALTH, metadata),
                Pair.of(Attributes.ENTITY_INTERACTION_RANGE, metadata), Pair.of(Attributes.LUCK, metadata));
        List<?> snapshot = new ArrayList<>(original);
        LivingEntity player = uninitializedPlayer();
        List<?> projected = L2AttributePanel.entries(player, original);
        check(original.equals(snapshot) && original.size() == 3, "The source attribute list is preserved");
        check(projected.size() == 4 && ((Pair<?, ?>)projected.get(2)).getFirst().equals(L2AttributePanel.MELEE_REACH),
                "The effective melee row follows entity interaction range");
        check(projected.stream().filter(row -> row instanceof Pair<?, ?> pair
                && pair.getFirst().equals(L2AttributePanel.MELEE_REACH)).count() == 1,
                "The projected list contains exactly one melee row");
        check(L2AttributePanel.entries(player, projected) == projected, "Repeated projection reuses a list that already has the melee row");
        Object insertedMetadata = ((Pair<?, ?>)projected.get(2)).getSecond();
        check(entryClass.isInstance(insertedMetadata)
                        && Boolean.FALSE.equals(entryClass.getMethod("usePercent").invoke(insertedMetadata))
                        && ((Number)entryClass.getMethod("intrinsic").invoke(insertedMetadata)).doubleValue() == 0D,
                "The melee row uses native absolute-value metadata with zero intrinsic value");
        var actualHandler = entryClass.getDeclaredMethod(handler.name, List.class, LivingEntity.class);
        actualHandler.setAccessible(true);
        check(projected.equals(actualHandler.invoke(null, original, player)), "The actual merged return hook executes the same row projection");
        check(actualHandler.invoke(null, original, null) == original, "The actual hook preserves a nonplayer list");
        var withoutEntityRange = List.of(Pair.of(Attributes.MAX_HEALTH, metadata));
        List<?> appended = L2AttributePanel.entries(player, withoutEntityRange);
        check(appended.size() == 2 && ((Pair<?, ?>)appended.getLast()).getFirst().equals(L2AttributePanel.MELEE_REACH),
                "A list without an interaction row receives one melee row at its end");
        report.addProperty("actual_static_entry_hook_executed", true);
        report.addProperty("virtual_melee_row_inserted_once_after_entity_range", true);
        report.addProperty("original_entry_list_preserved", true);
        report.addProperty("melee_row_uses_absolute_value_metadata", true);
        report.addProperty("uninitialized_player_used_only_for_entries", true);
        report.addProperty("uninitialized_player_value_queries", 0);
    }

    private static LivingEntity uninitializedPlayer() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field instance = unsafeClass.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        return (LivingEntity)unsafeClass.getMethod("allocateInstance", Class.class).invoke(instance.get(null), ServerPlayer.class);
    }

    private static ClassNode transformed(String name) throws Exception {
        Path path = Path.of(".mixin.out", "class", name.replace('.', '/') + ".class");
        check(Files.isRegularFile(path), "A real transformed export exists for " + name);
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(path)).accept(node, 0);
        return node;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(node -> node.name.equals(name) && node.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing method " + type.name + "." + name + descriptor));
    }

    private static MethodNode merged(ClassNode type, String mixin, String name) {
        return type.methods.stream().filter(node -> node.name.contains(name)
                        && (merged(node.visibleAnnotations, mixin) || merged(node.invisibleAnnotations, mixin)))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing merged handler " + mixin + "." + name));
    }

    private static boolean merged(List<AnnotationNode> annotations, String mixin) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                && annotation.values != null) {
            for (int index = 0; index + 1 < annotation.values.size(); index += 2)
                if (annotation.values.get(index).equals("mixin") && annotation.values.get(index + 1).equals(mixin)) return true;
        }
        return false;
    }

    private static boolean calls(MethodNode method, String owner, String name) { return callIndex(method, owner, name) >= 0; }

    private static int callIndex(MethodNode method, String owner, String name) {
        int index = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return index;
            index++;
        }
        return -1;
    }

    private static Set<MethodNode> reachable(ClassNode type, MethodNode root) {
        var result = new LinkedHashSet<MethodNode>();
        var pending = new ArrayDeque<MethodNode>();
        pending.add(root);
        while (!pending.isEmpty()) {
            MethodNode current = pending.removeFirst();
            if (!result.add(current)) continue;
            for (var instruction : current.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(type.name))
                    pending.add(method(type, call.name, call.desc));
                if (instruction instanceof InvokeDynamicInsnNode dynamic) for (Object argument : dynamic.bsmArgs)
                    if (argument instanceof Handle handle && handle.getOwner().equals(type.name))
                        pending.add(method(type, handle.getName(), handle.getDesc()));
            }
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void write(JsonObject report) throws Exception {
        Path target = output.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
    }
}
