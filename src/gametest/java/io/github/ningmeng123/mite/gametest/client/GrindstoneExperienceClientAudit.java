package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks actual client registration and tooltip content without a game, world or GL. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GrindstoneExperienceClientAudit implements DataProvider {
    private static final String SUBSCRIBER = "io.github.ningmeng123.mite.client.GrindstoneExperienceTooltip";
    private static final String PREFIX = "tooltip.mite.grindstone.";
    private final Path output;

    private GrindstoneExperienceClientAudit(Path output) { this.output = output; }

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.enchantmentClientAudit")) return;
        Path report = Path.of(System.getProperty("mite.enchantmentClientAuditOutput", "build/verification/enchantment-client-audit.json"));
        event.getGenerator().addProvider(true, new GrindstoneExperienceClientAudit(report.resolveSibling("grindstone-experience-client-audit.json")));
    }

    @Override public String getName() { return "Grindstone cost tooltip content, registration and slot-scoping audit without rendering"; }

    @Override
    public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("visual_appearance_passed", false);
        report.addProperty("manual_listener_registrations", 0);
        try {
            var listenersField = NeoForge.EVENT_BUS.getClass().getDeclaredField("listeners");
            listenersField.setAccessible(true);
            Map<?, ?> listeners = (Map<?, ?>)listenersField.get(NeoForge.EVENT_BUS);
            check(listeners.keySet().stream().anyMatch(key -> key instanceof Class<?> type && type.getName().equals(SUBSCRIBER)),
                    "The real game bus must automatically discover the client tooltip subscriber");
            report.addProperty("automatic_subscriber_registration", true);

            Class<?> subscriber = Class.forName(SUBSCRIBER, false, getClass().getClassLoader());
            Method append = subscriber.getDeclaredMethod("appendCost", List.class, int.class, boolean.class);
            append.setAccessible(true);
            JsonArray cases = new JsonArray();
            checkCase(append, -1, false, cases);
            checkCase(append, 0, true, cases);
            checkCase(append, 2, true, cases);
            checkCase(append, 2, false, cases);
            report.add("actual_tooltip_helper_cases", cases);

            auditSlotScope(report);
            JsonArray languages = new JsonArray();
            for (String locale : List.of("en_us", "zh_cn")) {
                Map<String, String> translations = new LinkedHashMap<>();
                try (var stream = getClass().getClassLoader().getResourceAsStream("assets/mite/lang/" + locale + ".json")) {
                    check(stream != null, "Missing " + locale + " language resource");
                    Language.loadFromJson(stream, translations::put);
                }
                for (String suffix : List.of("cost", "insufficient", "pending")) {
                    String value = translations.get(PREFIX + suffix);
                    check(value != null && !value.isBlank() && !value.equals(PREFIX + suffix), "Missing resolved " + locale + " " + suffix);
                }
                String pattern = translations.get(PREFIX + "cost");
                String formatted = String.format(Locale.ROOT, pattern, 2);
                check(pattern.contains("%s") && formatted.contains("2") && !formatted.contains("%s"),
                        "The localized cost must accept the single experience-point argument");
                check(!translations.get(PREFIX + "pending").contains("%s") && !translations.get(PREFIX + "insufficient").contains("%s"),
                        "Pending and insufficient lines have no numeric arguments");
                JsonObject language = new JsonObject();
                language.addProperty("locale", locale);
                language.addProperty("parser", "net.minecraft.locale.Language.loadFromJson");
                language.addProperty("formatted_two_point_cost", formatted);
                languages.add(language);
            }
            report.add("languages", languages);
            report.addProperty("status", "passed_client_tooltip_content_and_scope_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void checkCase(Method append, int cost, boolean affordable, JsonArray cases) throws Exception {
        Component original = Component.literal("Original item tooltip");
        List<Component> lines = new ArrayList<>(List.of(original));
        append.invoke(null, lines, cost, affordable);
        check(lines.getFirst() == original, "The actual tooltip helper must preserve existing item lines");
        if (cost < 0) {
            check(lines.size() == 2, "Unsynchronized costs append only the pending line");
            checkLine(lines.get(1), "pending", ChatFormatting.GRAY);
        } else {
            check(lines.size() == (affordable ? 2 : 3), "Affordability adds exactly the expected cost and warning lines");
            checkLine(lines.get(1), "cost", affordable ? ChatFormatting.GREEN : ChatFormatting.RED, cost);
            if (!affordable) checkLine(lines.get(2), "insufficient", ChatFormatting.RED);
        }
        JsonObject fact = new JsonObject();
        fact.addProperty("cost", cost);
        fact.addProperty("affordable", affordable);
        fact.addProperty("appended_lines", lines.size() - 1);
        fact.addProperty("original_item_line_preserved", true);
        cases.add(fact);
    }

    private static void checkLine(Component line, String suffix, ChatFormatting color, Object... arguments) {
        check(line.getContents() instanceof TranslatableContents, "Tooltip rows must stay translatable");
        TranslatableContents text = (TranslatableContents)line.getContents();
        check(text.getKey().equals(PREFIX + suffix), "Unexpected tooltip translation key");
        check(Arrays.equals(text.getArgs(), arguments), "Tooltip preserves the exact point-cost arguments");
        check(line.getStyle().getColor() != null && line.getStyle().getColor().getValue() == color.getColor(), "Unexpected tooltip line color");
    }

    private void auditSlotScope(JsonObject report) throws Exception {
        ClassNode type = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream(SUBSCRIBER.replace('.', '/') + ".class")) {
            check(stream != null, "Missing actual client subscriber bytecode");
            new ClassReader(stream).accept(type, 0);
        }
        MethodNode scope = method(type, "hoveredResult");
        MethodInsnNode resultSlot = call(scope, "getSlot");
        AbstractInsnNode previous = resultSlot.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        check(previous != null && previous.getOpcode() == Opcodes.ICONST_2, "The tooltip must select the grindstone result slot 2");
        call(scope, "getSlotUnderMouse");
        call(scope, "hasItem");
        check(hasReferenceComparison(scope), "Hover scoping must compare the exact result-slot instance");
        MethodNode tooltip = method(type, "tooltip");
        call(tooltip, "hoveredResult");
        call(tooltip, "getItemStack");
        call(tooltip, "getItem");
        check(hasReferenceComparison(tooltip), "The actual tooltip must compare stack identity to exclude matching inventory items");
        call(tooltip, "mite$experienceCost");
        call(tooltip, "mite$canTake");
        call(tooltip, "appendCost");
        MethodNode carried = method(type, "renderWhileCarrying");
        call(carried, "getCarried");
        call(carried, "isEmpty");
        call(carried, "hoveredResult");
        call(carried, "getTooltipFromItem");
        call(carried, "getTooltipImage");
        call(carried, "renderTooltip");
        report.addProperty("compiled_result_slot_and_stack_identity_guards", true);
        report.addProperty("compiled_carried_stack_path_preserves_full_item_tooltip", true);
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(method -> method.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing actual client method " + name));
    }

    private static MethodInsnNode call(MethodNode method, String name) {
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) return call;
        throw new IllegalStateException("Missing client call " + method.name + " -> " + name);
    }

    private static boolean hasReferenceComparison(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction.getOpcode() == Opcodes.IF_ACMPEQ || instruction.getOpcode() == Opcodes.IF_ACMPNE) return true;
        return false;
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
