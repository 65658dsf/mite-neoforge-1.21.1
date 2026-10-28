package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Actual client transforms and safe toggle execution, without a game, constructor, window or rendering. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MinimalDebugDisplayAudit implements DataProvider {
    private static final String DEBUG = "net/minecraft/client/gui/components/DebugScreenOverlay";
    private static final String GUI = "net/minecraft/client/gui/Gui";
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphics";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String MIXINS = "io.github.ningmeng123.mite.mixin.client.";
    private static final List<String> CHART_FIELDS = List.of("renderProfilerChart", "renderFpsCharts", "renderNetworkCharts");
    private final Path output;

    private MinimalDebugDisplayAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.minimalDebugAudit")) return;
        event.getGenerator().addProvider(true, new MinimalDebugDisplayAudit(Path.of(System.getProperty(
                "mite.minimalDebugAuditOutput", "build/verification/minimal-debug-audit.json"))));
    }

    @Override public String getName() { return "Actual minimal-debug overlay transformations and safe toggles without a game window"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("options_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("overlay_constructor_invoked", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("visual_appearance_passed", false);
        report.addProperty("sodium_loaded", ModList.get().isLoaded("sodium"));
        report.addProperty("modernfix_loaded", ModList.get().isLoaded("modernfix"));
        JsonArray exports = new JsonArray(); report.add("defined_targets", exports);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "Actual transformed-class export is required");
            ClassNode debug = defined(DEBUG, exports, true);
            ClassNode gui = defined(GUI, exports, true);
            ClassNode keyboard = defined("net/minecraft/client/KeyboardHandler", exports, true);
            auditRender(debug, report);
            auditWorldDay(debug, report);
            auditWorldTime(debug, report);
            auditHardwareScope(debug, report);
            auditHardwareSummary(debug, report);
            auditOptionalPerformancePaths(debug, exports, report);
            auditChartTransforms(debug, report);
            auditSafeRealToggles(debug, report);
            auditCrosshair(gui, report);
            auditKeyboard(keyboard, report);
            auditHideGui(debug, gui, report);
            report.addProperty("status", "passed_real_transforms_and_toggle_execution_without_rendering");
            write(report); return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed"); report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void auditRender(ClassNode debug, JsonObject report) {
        MethodNode render = method(debug, "render", "(L" + GRAPHICS + ";)V");
        MethodNode replacement = merged(debug, "MinimalDebugOverlayMixin", "mite$drawFpsAndPerformance");
        assertEarlyCancellation(debug, render, replacement);
        List<MethodNode> parts = lambdaParts(debug, replacement);
        check(calls(parts, MINECRAFT, "getFps") == 1 && calls(parts, "java/lang/Integer", "toString") == 1,
                "Replacement displays the actual native FPS as a bare number");
        String fpsDescriptor = "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I";
        check(calls(parts, GRAPHICS, "drawString") == 3
                        && calls(parts, GRAPHICS, "drawString", fpsDescriptor) == 1,
                "Exactly one bare FPS draw and localized day and game-time draws are used on the left");
        MethodInsnNode draw = call(parts, GRAPHICS, "drawString", fpsDescriptor);
        AbstractInsnNode shadow = previous(draw), color = previous(shadow), y = previous(color), x = previous(y);
        check(integer(x) == 2 && integer(y) == 2 && integer(color) == 0xE0E0E0 && integer(shadow) == 1,
                "FPS retains R196's (2,2), E0E0E0 and text shadow");
        check(calls(parts, DEBUG, "collectSystemInformationText") == 1 && calls(parts, DEBUG, "renderLines") == 1,
                "Native hardware information and right-side line layout remain active");
        MethodNode summary = merged(debug, "MinimalDebugOverlayMixin", "mite$hardwareSummary");
        MethodInsnNode lines = call(parts, DEBUG, "renderLines");
        check(integer(previous(lines)) == 0,
                "The only native line renderer is explicitly the right-side renderer");
        AbstractInsnNode filtered = previous(previous(lines));
        check(filtered instanceof MethodInsnNode filter && filter.owner.equals(DEBUG) && filter.name.equals(summary.name)
                        && calls(parts, DEBUG, summary.name) == 1,
                "The final hardware-summary result directly feeds the only right-side line renderer");
        MethodNode body = parts.stream().filter(part -> calls(part, DEBUG, summary.name) > 0).findFirst().orElseThrow();
        check(body.instructions.indexOf(call(body, "net/neoforged/bus/api/IEventBus", "post"))
                        < body.instructions.indexOf(filtered),
                "The exact-field hardware filter runs after all debug-text event listeners");
        check(calls(parts, DEBUG, "collectGameInformationText") == 0 && calls(parts, DEBUG, "getGameInformation") == 0,
                "Replacement never collects native coordinates or the full left-side debug information");
        check(calls(parts, GRAPHICS, "fill") == 0 && calls(replacement, GRAPHICS, "drawManaged") == 1,
                "The bare FPS counter has no added rectangle and retains the native managed flush scope");
        check(calls(parts, "net/neoforged/bus/api/IEventBus", "post") == 1,
                "The native debug-text extension event remains available for right-side performance statistics");
        report.addProperty("native_render_cancelled_before_world_picking_or_full_debug_draw", true);
        report.addProperty("fps_actual_bare_number_at_2_2_color_e0e0e0_with_shadow", true);
        report.addProperty("requested_hardware_fields_and_right_side_layout_preserved", true);
        report.addProperty("right_side_debug_text_extension_event_preserved", true);
        report.addProperty("post_event_hardware_filter_directly_controls_final_right_side_output", true);
        report.addProperty("native_coordinate_biome_chunk_and_full_debug_information_not_collected_or_drawn", true);
    }

    private static void auditWorldDay(ClassNode debug, JsonObject report) throws Exception {
        MethodNode replacement = merged(debug, "MinimalDebugOverlayMixin", "mite$drawFpsAndPerformance");
        List<MethodNode> parts = lambdaParts(debug, replacement);
        MethodNode day = merged(debug, "MinimalDebugOverlayMixin", "mite$displayDay");
        check(day.desc.equals("(J)J") && (day.access & Opcodes.ACC_STATIC) != 0,
                "The merged day formatter is a pure static long-to-long function");
        check(calls(parts, DEBUG, day.name) == 1,
                "The actual drawn day uses the merged MITE display-day formula once");
        check(calls(parts, "net/minecraft/client/multiplayer/ClientLevel", "getDayTime")
                        + calls(parts, "net/minecraft/world/level/Level", "getDayTime") == 1,
                "The day label reads the real synchronized client dayTime");
        MethodInsnNode read = calls(parts, "net/minecraft/client/multiplayer/ClientLevel", "getDayTime") == 1
                ? call(parts, "net/minecraft/client/multiplayer/ClientLevel", "getDayTime")
                : call(parts, "net/minecraft/world/level/Level", "getDayTime");
        AbstractInsnNode stored = next(read), clock = previous(call(parts, DEBUG, day.name));
        check(stored instanceof VarInsnNode store && store.getOpcode() == Opcodes.LSTORE
                        && clock instanceof VarInsnNode load && load.getOpcode() == Opcodes.LLOAD && load.var == store.var,
                "Synchronized dayTime is stored once and feeds the actual display-day formatter");
        check(parts.stream().anyMatch(part -> fieldAccess(part, MINECRAFT, "level", Opcodes.GETFIELD)),
                "The day label reads the current client level");
        check(parts.stream().anyMatch(part -> hasString(part, "gui.mite.world_day"))
                        && calls(parts, "net/minecraft/network/chat/Component", "translatable") == 3,
                "The day label uses the localized world-day component");
        String descriptor = "(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I";
        check(calls(parts, GRAPHICS, "drawString", descriptor) == 2, "Localized components are used for day count and game time");
        MethodInsnNode draw = call(parts, GRAPHICS, "drawString", descriptor);
        AbstractInsnNode shadow = previous(draw), color = previous(shadow), y = previous(color), x = previous(y);
        check(integer(x) == 2 && integer(y) == 12 && integer(color) == 0xE0E0E0 && integer(shadow) == 1,
                "The localized day is positioned under FPS at (2,12) with the same color and shadow");
        for (MethodNode part : parts) for (AbstractInsnNode instruction : part.instructions)
            if (instruction instanceof MethodInsnNode call)
                check(!call.owner.startsWith("net/minecraft/server/") && !call.name.equals("getGameTime")
                                && !call.name.equals("fixedTime") && !call.name.equals("getDayTimeFraction"),
                        "The display uses synchronized dayTime, not a server, gameTime or dimension fixed-time clock");
        Method actual = DebugScreenOverlay.class.getDeclaredMethod(day.name, long.class); actual.setAccessible(true);
        Class<?> shared = Class.forName("io.github.ningmeng123.mite.worldgen.R196StructureGeneration");
        Method sharedDay = shared.getMethod("displayDay", long.class);
        long[][] cases = {{Long.MIN_VALUE, 1L}, {-1L, 1L}, {0L, 1L}, {17999L, 1L},
                {18000L, 2L}, {23999L, 2L}, {24000L, 2L}, {41999L, 2L}, {42000L, 3L},
                {Long.MAX_VALUE, 384307168202283L}};
        JsonArray results = new JsonArray();
        for (long[] example : cases) {
            long displayed = (Long)actual.invoke(null, example[0]);
            check(displayed == example[1] && displayed == (Long)sharedDay.invoke(null, example[0]),
                    "Actual merged day formula matches the independently shared MITE display-day rule at " + example[0]);
            JsonObject result = new JsonObject(); result.addProperty("day_time", example[0]); result.addProperty("displayed_day", displayed); results.add(result);
        }
        report.add("real_display_day_boundary_cases", results);
        report.addProperty("localized_day_reads_synchronized_client_day_time", true);
        report.addProperty("actual_display_day_matches_shared_mite_rule_including_rollover_and_long_limits", true);
        report.addProperty("localized_day_at_2_12_preserves_only_requested_time_information", true);
    }

    private static void auditWorldTime(ClassNode debug, JsonObject report) throws Exception {
        List<MethodNode> parts = lambdaParts(debug, merged(debug, "MinimalDebugOverlayMixin", "mite$drawFpsAndPerformance"));
        MethodNode time = merged(debug, "MinimalDebugOverlayMixin", "mite$displayTime");
        MethodNode day = merged(debug, "MinimalDebugOverlayMixin", "mite$displayDay");
        check(time.desc.equals("(J)Ljava/lang/String;") && (time.access & Opcodes.ACC_STATIC) != 0
                        && calls(parts, DEBUG, time.name) == 1,
                "The game-time row uses the actual merged static tick-to-time formatter once");
        AbstractInsnNode timeInput = previous(call(parts, DEBUG, time.name));
        AbstractInsnNode dayInput = previous(call(parts, DEBUG, day.name));
        check(timeInput instanceof VarInsnNode timeLoad && timeLoad.getOpcode() == Opcodes.LLOAD
                        && dayInput instanceof VarInsnNode dayLoad && dayLoad.getOpcode() == Opcodes.LLOAD
                        && timeLoad.var == dayLoad.var,
                "Game time and day count use the same synchronized client dayTime value");
        check(parts.stream().anyMatch(part -> hasString(part, "gui.mite.world_time")),
                "The game-time row uses a localized label");
        AbstractInsnNode instruction = next(call(parts, DEBUG, time.name));
        while (instruction != null && !(instruction instanceof MethodInsnNode draw
                && draw.owner.equals(GRAPHICS) && draw.name.equals("drawString"))) instruction = next(instruction);
        check(instruction instanceof MethodInsnNode, "Formatted game time reaches an actual text draw");
        AbstractInsnNode shadow = previous(instruction), color = previous(shadow), y = previous(color), x = previous(y);
        check(integer(x) == 2 && integer(y) == 22 && integer(color) == 0xE0E0E0 && integer(shadow) == 1,
                "The localized game time is positioned under the day at (2,22) with the same color and shadow");
        Method actual = DebugScreenOverlay.class.getDeclaredMethod(time.name, long.class); actual.setAccessible(true);
        long[] inputs = {Long.MIN_VALUE, -1L, 0L, 16L, 17L, 999L, 1000L, 6000L,
                12000L, 17999L, 18000L, 23999L, 24000L, Long.MAX_VALUE};
        String[] expected = {"22:11", "05:59", "06:00", "06:00", "06:01", "06:59", "07:00", "12:00",
                "18:00", "23:59", "00:00", "05:59", "06:00", "13:48"};
        JsonArray results = new JsonArray();
        for (int i = 0; i < inputs.length; i++) {
            String displayed = (String)actual.invoke(null, inputs[i]);
            check(expected[i].equals(displayed), "Actual merged game-time formatter matches the independent clock case at " + inputs[i]);
            JsonObject result = new JsonObject(); result.addProperty("day_time", inputs[i]); result.addProperty("displayed_time", displayed); results.add(result);
        }
        report.add("real_display_time_boundary_cases", results);
        report.addProperty("localized_game_time_hh_mm_at_2_22_uses_shared_synchronized_day_time", true);
        MethodNode period = merged(debug, "MinimalDebugOverlayMixin", "mite$displayTimePeriod");
        check(period.desc.equals("(J)Ljava/lang/String;") && (period.access & Opcodes.ACC_STATIC) != 0
                        && calls(parts, DEBUG, period.name) == 1
                        && calls(period, "io/github/ningmeng123/mite/rules/LunarCalendar", "daytime") == 1,
                "The displayed day/night period uses the shared MITE lunar-calendar daytime rule");
        AbstractInsnNode periodInput = previous(call(parts, DEBUG, period.name));
        check(periodInput instanceof VarInsnNode periodLoad && periodLoad.getOpcode() == Opcodes.LLOAD
                        && dayInput instanceof VarInsnNode dayLoad && periodLoad.var == dayLoad.var,
                "Day/night and the clock use the same synchronized client dayTime value");
        AbstractInsnNode localized = next(call(parts, DEBUG, period.name));
        check(localized instanceof MethodInsnNode label && label.owner.equals("net/minecraft/network/chat/Component")
                        && label.name.equals("translatable"),
                "The period translation key is localized before inclusion in the game-time row");
        Method actualPeriod = DebugScreenOverlay.class.getDeclaredMethod(period.name, long.class); actualPeriod.setAccessible(true);
        long[] periodInputs = {Long.MIN_VALUE, -1000L, -999L, -1L, 0L, 12999L, 13000L,
                18000L, 23000L, 23001L, 23999L, 24000L, 37000L, Long.MAX_VALUE};
        boolean[] daytime = {false, false, true, true, true, true, false,
                false, false, true, true, true, false, true};
        JsonArray periods = new JsonArray();
        for (int i = 0; i < periodInputs.length; i++) {
            String displayed = (String)actualPeriod.invoke(null, periodInputs[i]);
            check((daytime[i] ? "gui.mite.time.day" : "gui.mite.time.night").equals(displayed),
                    "Actual merged day/night formatter matches the independent boundary case at " + periodInputs[i]);
            JsonObject result = new JsonObject(); result.addProperty("day_time", periodInputs[i]); result.addProperty("displayed_period_key", displayed); periods.add(result);
        }
        report.add("real_display_time_period_boundary_cases", periods);
        report.addProperty("localized_day_night_uses_shared_day_time_and_mite_calendar_boundaries", true);
    }

    private static void auditHardwareScope(ClassNode debug, JsonObject report) throws Exception {
        MethodNode system = method(debug, "getSystemInformation", "()Ljava/util/List;");
        MethodNode reduced = merged(debug, "MinimalDebugOverlayMixin", "mite$hardwareInformationOnly");
        MethodNode demo = merged(debug, "MinimalDebugOverlayMixin", "mite$omitDemoWorldTime");
        check(calls(system, debug.name, reduced.name) == 1 && calls(system, debug.name, demo.name) == 1,
                "Only the system-information reduced-data and demo-time expressions receive the scoped guards");
        for (MethodNode other : debug.methods) if (other != system) {
            check(calls(other, debug.name, reduced.name) == 0 && calls(other, debug.name, demo.name) == 0,
                    "Hardware-only guards do not change unrelated reduced-debug or demo checks");
        }
        MethodInsnNode gate = call(system, debug.name, reduced.name);
        AbstractInsnNode conditional = next(gate);
        check(conditional instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ,
                "Native reduced-information result gates the early hardware-list return");
        JumpInsnNode branch = (JumpInsnNode)conditional;
        boolean returned = false;
        for (AbstractInsnNode instruction = next(branch); instruction != null && instruction != branch.label; instruction = instruction.getNext())
            returned |= instruction.getOpcode() == Opcodes.ARETURN;
        check(returned, "Hardware-only true result returns before the targeted block, fluid and entity branch");
        int gateIndex = system.instructions.indexOf(gate), targets = system.instructions.indexOf(branch.label);
        for (AbstractInsnNode instruction : system.instructions) if (instruction instanceof FieldInsnNode field
                && (field.owner.equals(DEBUG) && List.of("block", "liquid").contains(field.name)
                || field.owner.equals(MINECRAFT) && field.name.equals("crosshairPickEntity")))
            check(system.instructions.indexOf(field) > targets && targets > gateIndex,
                    "Target block, fluid and entity data are all beyond the hardware-only early return");
        for (String hardware : List.of("Java: %s", "Mem: %2d%% %03d/%03dMB", "CPU: %s", "Display: %dx%d (%s)"))
            check(hasString(system, hardware), "Native hardware output remains present: " + hardware);
        check(calls(system, "java/lang/Runtime", "maxMemory") == 1 && calls(system, "java/lang/Runtime", "freeMemory") == 1,
                "Native memory measurements remain present");
        report.addProperty("target_block_fluid_entity_excluded_by_scoped_early_return", true);
        report.addProperty("native_java_cpu_gpu_display_memory_measurements_retained", true);
        report.addProperty("demo_world_time_excluded_without_changing_global_demo_mode", true);
    }

    private static void auditChartTransforms(ClassNode debug, JsonObject report) {
        MethodNode hidden = merged(debug, "MinimalDebugOverlayMixin", "mite$hideDebugCharts");
        MethodNode toggled = merged(debug, "MinimalDebugOverlayMixin", "mite$toggleFpsOverlayInsteadOfCharts");
        check(calls(toggled, DEBUG, "toggleOverlay") == 1 && calls(toggled, CALLBACK, "cancel") == 1,
                "Chart shortcut handler toggles the same native FPS visibility and cancels chart activation");
        for (String entry : List.of("toggleProfilerChart", "toggleFpsCharts", "toggleNetworkCharts"))
            assertEarlyCancellation(debug, method(debug, entry, "()V"), toggled);
        for (String entry : List.of("showProfilerChart", "showFpsCharts", "showNetworkCharts"))
            check(calls(method(debug, entry, "()Z"), DEBUG, hidden.name) == 1,
                    "All actual chart predicates reach the merged false-result handler: " + entry);
        report.addProperty("all_three_chart_shortcuts_reach_native_overlay_toggle", true);
        report.addProperty("all_three_chart_predicates_have_actual_injected_guards", true);
    }

    private static void auditHardwareSummary(ClassNode debug, JsonObject report) throws Exception {
        MethodNode summary = merged(debug, "MinimalDebugOverlayMixin", "mite$hardwareSummary");
        check(summary.desc.equals("(Ljava/util/List;)Ljava/util/List;") && (summary.access & Opcodes.ACC_STATIC) != 0,
                "The actual final-output summary is an independent static list filter");
        Method actual = DebugScreenOverlay.class.getDeclaredMethod(summary.name, List.class); actual.setAccessible(true);
        JsonArray cases = new JsonArray();
        String javaRow = "Java: 21.0.8 64bit", memory = "Mem: 24% 986/4096MB", allocation = "Allocation rate: 015MB/s";
        String allocated = "Allocated: 40% 1638MB", direct = "Direct Buffers: +0MB", offHeap = "Off-Heap: +64MB";
        String cpu = "CPU: 16x AMD Ryzen 7 5800X", display = "Display: 1920x1080 (NVIDIA Corporation)";
        String renderer = "NVIDIA GeForce RTX 3060/PCIe/SSE2", openGl = "3.2.0 NVIDIA 576.52";
        List<String> vanilla = List.of(javaRow, memory, allocation, allocated, "", cpu, "", display, renderer, openGl);
        summaryCase(actual, "native_vanilla_hardware_without_optional_memory_fields", vanilla, vanilla, cases);
        List<String> sodium = List.of(javaRow, memory, allocation, direct, allocated, offHeap, "", cpu, "", display, renderer, openGl,
                "", "Sodium Renderer (0.8.13)", "Pool: 512MiB", "TransferQueue: 8", "ChunkBuilder: 3 threads", "Task: 64",
                "ModernFix 5.27.26", "ModernFix: 22 cached resources");
        List<String> moddedHardware = List.of(javaRow, memory, allocation, direct, allocated, offHeap, "", cpu, "", display, renderer, openGl);
        summaryCase(actual, "optional_native_memory_retained_brands_and_renderer_queues_removed", sodium, moddedHardware, cases);
        java.util.ArrayList<String> fullDebug = new java.util.ArrayList<>(sodium);
        fullDebug.addAll(List.of("", "XYZ: 123.45 / 64.0 / -321.5", "Block: 123 64 -322", "Chunk: 11 0 14 in 7 4 -21",
                "Facing: north (Towards negative Z) (0.0 / 0.0)", "Biome: minecraft:plains", "Local Difficulty: 3.50 // 1.00 (Day 120)",
                "Targeted Block: 123, 64, -322", "minecraft:stone", "#minecraft:mineable/pickaxe", "Targeted Fluid: 123, 64, -322",
                "minecraft:water", "falling: false", "Targeted Entity", "minecraft:zombie", "#minecraft:undead", "For help: press F3 + Q"));
        summaryCase(actual, "full_modern_world_targets_tags_and_dimensions_removed", fullDebug, moddedHardware, cases);
        summaryCase(actual, "formatting_preserved_for_allowed_fields_but_never_bypasses_unknown_fields",
                List.of("\u00a7a" + javaRow + "\u00a7r", "\u00a7cXYZ: 1 / 2 / 3", "\u00a76Sodium Renderer (0.8.13)",
                        "\u00a7b" + cpu, "", "\u00a7e" + display, "\u00a77" + renderer, "\u00a77" + openGl,
                        "\u00a7dTask: 10", "\u00a7fTargeted Entity", "\u00a7cModernFix: hidden"),
                List.of("\u00a7a" + javaRow + "\u00a7r", "\u00a7b" + cpu, "", "\u00a7e" + display,
                        "\u00a77" + renderer, "\u00a77" + openGl), cases);
        summaryCase(actual, "blank_null_and_removed_only_groups_do_not_create_leading_trailing_or_duplicate_gaps",
                java.util.Arrays.asList(null, "", "   ", javaRow, "", "", "Sodium Renderer", "", cpu, "", "", display,
                        renderer, openGl, "", null, "Task: 3", "", "\t"),
                List.of(javaRow, "", cpu, "", display, renderer, openGl), cases);
        summaryCase(actual, "target_header_after_display_never_consumed_as_gpu_model_or_properties",
                List.of(display, "\u00a7nTargeted Block: 1, 2, 3", "minecraft:stone", "age: 2", "#minecraft:stone", javaRow),
                List.of(display, javaRow), cases);
        summaryCase(actual, "renderer_brand_after_display_never_consumed_as_gpu_or_queue_details",
                List.of(display, "Sodium Renderer (0.8.13)", "Task: 64", "Pool: 256MiB", "ChunkBuilder: 3"),
                List.of(display), cases);
        summaryCase(actual, "next_hardware_header_after_display_remains_a_header_without_adopting_unknown_details",
                List.of(display, cpu, "XYZ: 1 / 2 / 3", "minecraft:zombie"), List.of(display, cpu), cases);
        summaryCase(actual, "unknown_data_without_native_hardware_yields_empty_output",
                List.of("", "Sodium Renderer", "Pool: 128MiB", "XYZ: 1 / 2 / 3", "Targeted Block: 1,2,3", "ModernFix", ""),
                List.of(), cases);
        summaryCase(actual, "empty_input_stays_empty_without_manufacturing_hardware_or_native_memory", List.of(), List.of(), cases);
        report.add("actual_hardware_summary_cases", cases);
        report.addProperty("exact_requested_memory_cpu_display_gpu_fields_only", true);
        report.addProperty("sodium_renderer_pool_queue_task_and_modernfix_brand_output_removed", true);
        report.addProperty("hardware_summary_preserves_formatting_and_never_mutates_source_list", true);
    }

    private static void summaryCase(Method actual, String name, List<String> input, List<String> expected, JsonArray cases) throws Exception {
        List<String> snapshot = new java.util.ArrayList<>(input);
        Object filtered = actual.invoke(null, input);
        check(filtered instanceof List<?> lines && lines.equals(expected),
                "Actual merged hardware-summary output matches the requested fields for " + name + ": " + filtered);
        check(filtered != input && input.equals(snapshot), "Actual hardware summary owns a fresh result and never changes the source list: " + name);
        JsonObject result = new JsonObject(); result.addProperty("case", name); result.addProperty("source_unchanged", true);
        JsonArray visible = new JsonArray(); expected.forEach(visible::add); result.add("visible_lines", visible); cases.add(result);
    }

    private static void auditOptionalPerformancePaths(ClassNode debug, JsonArray exports, JsonObject report) throws Exception {
        ClassLoader loader = MinimalDebugDisplayAudit.class.getClassLoader();
        JsonObject optional = new JsonObject(); report.add("optional_performance_extensions", optional);
        if (ModList.get().isLoaded("sodium")) {
            MethodNode system = method(debug, "getSystemInformation", "()Ljava/util/List;");
            String sodiumMixin = "net.caffeinemc.mods.sodium.mixin.features.gui.hooks.debug.DebugScreenOverlayMixin";
            List<MethodNode> handlers = debug.methods.stream().filter(candidate -> mergedFrom(candidate, sodiumMixin)).toList();
            check(handlers.stream().anyMatch(candidate -> calls(system, debug.name, candidate.name) > 0),
                    "Actual hardware-list construction retains Sodium's merged debug-information extension");
            check(handlers.stream().anyMatch(candidate -> calls(candidate, debug.name, "getNativeMemoryString") > 0),
                    "Sodium's actual hardware-list extension still provides its native-memory row before final filtering");
            optional.addProperty("sodium", "actual_native_memory_collection_retained_renderer_brand_and_queue_output_filtered");
        } else {
            check(loader.getResource("net/caffeinemc/mods/sodium/client/config/structure/Option.class") == null,
                    "Sodium classes are truly absent in the no-mods audit");
            optional.addProperty("sodium", "actual_classpath_absence_and_successful_client_transformation");
        }
        String modernFixClass = "org/embeddedt/modernfix/neoforge/init/ModernFixClientForge";
        if (ModList.get().isLoaded("modernfix")) {
            ClassNode modernFix = defined(modernFixClass, exports, false);
            MethodNode handler = method(modernFix, "onRenderOverlay", "(Lnet/neoforged/neoforge/client/event/CustomizeGuiOverlayEvent$DebugText;)V");
            check(calls(handler, "net/neoforged/neoforge/client/event/CustomizeGuiOverlayEvent$DebugText", "getRight") > 0
                            && calls(handler, "java/util/List", "addAll") > 0,
                    "ModernFix's real debug-text listener still receives the event before the final hardware-only output filter");
            optional.addProperty("modernfix", "native_right_side_event_listener_retained_brand_and_other_unrequested_output_filtered");
        } else {
            check(loader.getResource(modernFixClass + ".class") == null, "ModernFix classes are truly absent in the no-mods audit");
            optional.addProperty("modernfix", "actual_classpath_absence_and_successful_client_transformation");
        }
    }

    private static void auditSafeRealToggles(ClassNode bytecode, JsonObject report) throws Exception {
        // allocateInstance bypasses the real constructor, so Minecraft, Font, timers and charts remain null.
        // Only the independent boolean toggles/predicates and constant boolean expression guards are executed.
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        DebugScreenOverlay overlay = (DebugScreenOverlay)unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, DebugScreenOverlay.class);
        for (String uninitialized : List.of("minecraft", "font", "frameTimeLogger", "fpsChart"))
            check(field(DebugScreenOverlay.class, uninitialized).get(overlay) == null, "No constructor dependency was initialized: " + uninitialized);
        Field visible = field(DebugScreenOverlay.class, "renderDebug");
        JsonArray cases = new JsonArray();
        for (String name : List.of("toggleProfilerChart", "toggleFpsCharts", "toggleNetworkCharts")) {
            Method toggle = DebugScreenOverlay.class.getMethod(name);
            for (int repetition = 0; repetition < 12; repetition++) {
                boolean before = visible.getBoolean(overlay); toggle.invoke(overlay);
                check(visible.getBoolean(overlay) != before, "Actual chart shortcut flips native overlay visibility: " + name);
                for (String chart : CHART_FIELDS) check(!field(DebugScreenOverlay.class, chart).getBoolean(overlay),
                        "Actual chart shortcut does not enable any chart field: " + name + "/" + chart);
                assertChartsHidden(overlay);
            }
            boolean before = visible.getBoolean(overlay); overlay.toggleOverlay();
            check(visible.getBoolean(overlay) != before, "Original F3 toggle remains operational between chart shortcuts");
            toggle.invoke(overlay);
            check(visible.getBoolean(overlay) == before, "Chart shortcut and original F3 toggle operate on the same state");
            JsonObject result = new JsonObject(); result.addProperty("entrypoint", name); result.addProperty("repetitions", 12);
            result.addProperty("native_f3_interoperable", true); result.addProperty("chart_fields_remain_false", true); cases.add(result);
        }
        for (String chart : CHART_FIELDS) field(DebugScreenOverlay.class, chart).setBoolean(overlay, true);
        assertChartsHidden(overlay);
        for (String suffix : List.of("mite$hardwareInformationOnly", "mite$omitDemoWorldTime")) {
            MethodNode handler = merged(bytecode, "MinimalDebugOverlayMixin", suffix);
            Method actual = DebugScreenOverlay.class.getDeclaredMethod(handler.name, boolean.class); actual.setAccessible(true);
            boolean expected = suffix.equals("mite$hardwareInformationOnly");
            for (boolean original : new boolean[]{false, true}) check(Boolean.valueOf(expected).equals(actual.invoke(overlay, original)),
                    "Actual scoped hardware expression guard returns its fixed decision for either native result: " + suffix);
        }
        report.add("real_chart_shortcut_toggle_cases", cases);
        report.addProperty("preexisting_true_chart_flags_cannot_bypass_actual_predicates", true);
        report.addProperty("actual_hardware_and_demo_expression_guards_executed_for_both_inputs", true);
        report.addProperty("unsafe_overlay_allocated_without_constructor_only_boolean_paths_executed", true);
    }

    private static void assertChartsHidden(DebugScreenOverlay overlay) {
        check(!overlay.showProfilerChart() && !overlay.showFpsCharts() && !overlay.showNetworkCharts(),
                "Actual chart predicates all remain false without accessing the absent game instance");
    }

    private static void auditCrosshair(ClassNode gui, JsonObject report) {
        MethodNode crosshair = method(gui, "renderCrosshair", "(L" + GRAPHICS + ";Lnet/minecraft/client/DeltaTracker;)V");
        MethodNode handler = merged(gui, "MinimalDebugCrosshairMixin", "mite$keepOrdinaryCrosshair");
        check(calls(crosshair, DEBUG, "showDebugScreen") == 1 && calls(crosshair, GUI, handler.name) == 1,
                "Only the native crosshair debug predicate is wrapped once");
        for (MethodNode other : gui.methods) if (other != crosshair)
            check(calls(other, GUI, handler.name) == 0, "Crosshair guard does not replace other debug visibility checks");
        List<AbstractInsnNode> executable = executable(handler);
        check(executable.size() == 2 && executable.get(0).getOpcode() == Opcodes.ICONST_0
                        && executable.get(1).getOpcode() == Opcodes.IRETURN,
                "Actual merged crosshair handler returns false for either input");
        check(calls(crosshair, GRAPHICS, "blitSprite") > 0, "Original ordinary sprite crosshair drawing remains available");
        report.addProperty("normal_crosshair_forced_only_at_crosshair_debug_expression", true);
    }

    private static void auditKeyboard(ClassNode keyboard, JsonObject report) throws Exception {
        MethodNode keys = method(keyboard, "handleDebugKeys", "(I)Z");
        MethodNode keyPress = method(keyboard, "keyPress", "(JIIII)V");
        check(calls(keyPress, DEBUG, "toggleOverlay") == 1 && calls(keyPress, keyboard.name, "handleDebugKeys") == 1,
                "Native F3 visibility and debug chord dispatch remain in the actual keyboard handler");
        check(hasInteger(keyPress, 292) && hasInteger(keyPress, 290), "Native F3 and F1 key codes remain present");
        check(fieldAccess(keyPress, "net/minecraft/client/Options", "hideGui", Opcodes.PUTFIELD),
                "Native F1 still updates hideGui");
        check(caseCalls(keys, 65, "net/minecraft/client/renderer/LevelRenderer", "allChanged"), "Native F3+A chunk reload remains reachable");
        MethodNode guard = merged(keyboard, "DisabledHitboxDebugKeyMixin", "mite$disableHitboxDebugKey");
        check(calls(keys, keyboard.name, guard.name) == 1,
                "Actual debug-key dispatch reaches the hitbox shortcut guard exactly once");
        for (AbstractInsnNode instruction : keys.instructions) {
            if (instruction == call(keys, keyboard.name, guard.name)) break;
            check(!(instruction instanceof FieldInsnNode field && field.name.equals("minecraft")),
                    "Hitbox shortcut is guarded before accessing the game instance");
        }
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Object keyboardInstance = unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), KeyboardHandler.class);
        Method actualKeys = KeyboardHandler.class.getDeclaredMethod("handleDebugKeys", int.class); actualKeys.setAccessible(true);
        check(Boolean.TRUE.equals(actualKeys.invoke(keyboardInstance, 66)),
                "F3+B is consumed before the native hitbox toggle or debug feedback accesses the absent game");
        Method actualGuard = KeyboardHandler.class.getDeclaredMethod(guard.name, int.class, CallbackInfoReturnable.class);
        actualGuard.setAccessible(true);
        for (int key = 0; key <= 348; key++) {
            CallbackInfoReturnable<Boolean> callback = new CallbackInfoReturnable<>("handleDebugKeys", true);
            actualGuard.invoke(keyboardInstance, key, callback);
            check(callback.isCancelled() == (key == 66), "Only the B debug shortcut is consumed: " + key);
        }
        List<AbstractInsnNode> tooltip = caseBody(keys, 72);
        check(tooltip.stream().anyMatch(instruction -> instruction instanceof FieldInsnNode field && field.owner.equals("net/minecraft/client/Options")
                        && field.name.equals("advancedItemTooltips") && field.getOpcode() == Opcodes.PUTFIELD),
                "Native F3+H advanced tooltip toggle remains reachable");
        check(caseCalls(keys, 72, "net/minecraft/client/Options", "save"), "Native F3+H still saves its selected option");
        for (MethodNode candidate : keyboard.methods)
            check(!mergedFrom(candidate, MIXINS + "MinimalDebugOverlayMixin") && !mergedFrom(candidate, MIXINS + "MinimalDebugCrosshairMixin"),
                    "The minimal display feature adds no keyboard handler");
        report.addProperty("native_f3_toggle_f1_hidegui_and_chord_dispatch_retained", true);
        report.addProperty("native_f3_a_reload_h_tooltips_retained", true);
        report.addProperty("f3_b_hitbox_shortcut_consumed_before_game_access", true);
        report.addProperty("all_other_debug_keys_continue_native_dispatch", true);
    }

    private static void auditHideGui(ClassNode debug, ClassNode gui, JsonObject report) {
        MethodNode shown = method(debug, "showDebugScreen", "()Z");
        check(fieldAccess(shown, DEBUG, "renderDebug", Opcodes.GETFIELD)
                        && fieldAccess(shown, "net/minecraft/client/Options", "hideGui", Opcodes.GETFIELD),
                "The actual native show predicate retains both F3 visibility and F1 hideGui");
        check(gui.methods.stream().anyMatch(candidate -> calls(candidate, DEBUG, "showDebugScreen") > 0 && calls(candidate, DEBUG, "render") > 0),
                "Native overlay layer still checks visibility before rendering");
        report.addProperty("native_f1_and_f3_visibility_gate_preserved", true);
    }

    private static void assertEarlyCancellation(ClassNode type, MethodNode entry, MethodNode handler) {
        check(calls(entry, type.name, handler.name) == 1, "Actual entry reaches exactly one merged replacement: " + entry.name);
        check(calls(handler, CALLBACK, "cancel") == 1 && everyReturnAfterCancel(handler), "Replacement unconditionally cancels before returning: " + handler.name);
        MethodInsnNode injected = call(entry, type.name, handler.name);
        AbstractInsnNode instruction = next(injected);
        while (instruction != null && !(instruction instanceof MethodInsnNode found && found.owner.equals(CALLBACK) && found.name.equals("isCancelled")))
            instruction = next(instruction);
        check(instruction != null, "Actual injected replacement has a cancellation gate: " + entry.name);
        AbstractInsnNode conditional = next(instruction);
        check(conditional instanceof JumpInsnNode && conditional.getOpcode() == Opcodes.IFEQ && next(conditional).getOpcode() == Opcodes.RETURN,
                "Cancelled replacement returns before the original native method body: " + entry.name);
        for (AbstractInsnNode original : entry.instructions) {
            if (original == injected) break;
            if (original instanceof MethodInsnNode before)
                check(!List.of("pick", "collectGameInformationText", "collectSystemInformationText", "drawManaged", "renderLines").contains(before.name),
                        "No original debug data collection or drawing precedes replacement entry");
        }
    }

    private static boolean everyReturnAfterCancel(MethodNode method) {
        record State(AbstractInsnNode instruction, boolean cancelled) {}
        ArrayDeque<State> pending = new ArrayDeque<>(); Set<State> visited = new HashSet<>(); pending.add(new State(method.instructions.getFirst(), false));
        while (!pending.isEmpty()) {
            State state = pending.removeFirst(); if (!visited.add(state)) continue;
            AbstractInsnNode instruction = state.instruction();
            boolean cancelled = state.cancelled() || instruction instanceof MethodInsnNode call && call.owner.equals(CALLBACK) && call.name.equals("cancel");
            int opcode = instruction.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) { if (!cancelled) return false; continue; }
            if (opcode == Opcodes.ATHROW) continue;
            if (instruction instanceof JumpInsnNode jump) {
                pending.add(new State(jump.label, cancelled));
                if (opcode == Opcodes.GOTO) continue;
            }
            if (instruction.getNext() != null) pending.add(new State(instruction.getNext(), cancelled));
        }
        return true;
    }

    private static List<AbstractInsnNode> caseBody(MethodNode method, int key) {
        LabelNode target = null;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LookupSwitchInsnNode lookup) {
                int index = lookup.keys.indexOf(key); if (index >= 0) target = lookup.labels.get(index);
            } else if (instruction instanceof TableSwitchInsnNode table && key >= table.min && key <= table.max) target = table.labels.get(key - table.min);
            if (target != null) break;
        }
        check(target != null, "Native debug key switch retains case " + key);
        java.util.ArrayList<AbstractInsnNode> result = new java.util.ArrayList<>();
        for (AbstractInsnNode instruction = target; instruction != null; instruction = instruction.getNext()) {
            result.add(instruction); if (instruction.getOpcode() == Opcodes.IRETURN) break;
        }
        return result;
    }

    private static boolean caseCalls(MethodNode method, int key, String owner, String name) {
        return caseBody(method, key).stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name));
    }

    private static ClassNode defined(String name, JsonArray exports, boolean requireExport) throws Exception {
        Class.forName(name.replace('/', '.'), false, MinimalDebugDisplayAudit.class.getClassLoader());
        Path path = Path.of(".mixin.out", "class", name + ".class");
        JsonObject fact = new JsonObject(); fact.addProperty("class", name.replace('/', '.'));
        fact.addProperty("class_defined_without_initialization", true); byte[] bytes;
        if (Files.isRegularFile(path)) {
            bytes = Files.readAllBytes(path); fact.addProperty("export", path.toAbsolutePath().normalize().toString());
        } else {
            check(!requireExport, "Missing actual transformed-class export " + name);
            try (var stream = MinimalDebugDisplayAudit.class.getClassLoader().getResourceAsStream(name + ".class")) {
                check(stream != null, "Missing native class bytecode " + name); bytes = stream.readAllBytes();
            }
            fact.addProperty("bytecode_source", "native_classpath_resource_no_mixin_export");
        }
        fact.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); exports.add(fact);
        ClassNode type = new ClassNode(); new ClassReader(bytes).accept(type, 0); return type;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned actual method " + type.name + "." + name + descriptor));
    }

    private static MethodNode merged(ClassNode type, String mixin, String original) {
        // Mixin also annotates the compiler-generated lambda implementation, whose
        // generated name embeds the enclosing handler. Only the original method or
        // an injector-renamed exact suffix is the entrypoint; lambdaParts follows
        // the actual invokedynamic implementation handle separately.
        List<MethodNode> methods = type.methods.stream().filter(candidate -> (candidate.access & Opcodes.ACC_SYNTHETIC) == 0
                && (candidate.name.equals(original) || candidate.name.endsWith("$" + original))
                && mergedFrom(candidate, MIXINS + mixin)).toList();
        check(methods.size() == 1, "Exactly one actual merged handler " + mixin + "." + original); return methods.getFirst();
    }

    private static boolean mergedFrom(MethodNode method, String mixin) {
        return mergedFrom(method.visibleAnnotations, mixin) || mergedFrom(method.invisibleAnnotations, mixin);
    }

    private static boolean mergedFrom(List<AnnotationNode> annotations, String mixin) {
        if (annotations != null) for (AnnotationNode annotation : annotations)
            if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null)
                for (int i = 0; i + 1 < annotation.values.size(); i += 2)
                    if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals(mixin)) return true;
        return false;
    }

    private static long calls(MethodNode method, String owner, String name) {
        long count = 0; for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++; return count;
    }

    private static MethodInsnNode call(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return call;
        throw new IllegalStateException("Missing call " + method.name + " -> " + owner + "." + name);
    }

    private static long calls(List<MethodNode> methods, String owner, String name) {
        return methods.stream().mapToLong(method -> calls(method, owner, name)).sum();
    }

    private static long calls(List<MethodNode> methods, String owner, String name, String descriptor) {
        return methods.stream().flatMap(method -> executable(method).stream())
                .filter(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                        && call.name.equals(name) && call.desc.equals(descriptor)).count();
    }

    private static MethodInsnNode call(List<MethodNode> methods, String owner, String name, String descriptor) {
        for (MethodNode method : methods) for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)
                    && call.desc.equals(descriptor)) return call;
        throw new IllegalStateException("Missing replacement call -> " + owner + "." + name + descriptor);
    }

    private static MethodInsnNode call(List<MethodNode> methods, String owner, String name) {
        for (MethodNode method : methods) if (calls(method, owner, name) > 0) return call(method, owner, name);
        throw new IllegalStateException("Missing replacement call -> " + owner + "." + name);
    }

    private static List<MethodNode> lambdaParts(ClassNode type, MethodNode entry) {
        // Follow only the actual invokedynamic implementation handles, rather than treating
        // unrelated native collectors/renderers as part of the injected replacement body.
        java.util.LinkedHashSet<MethodNode> result = new java.util.LinkedHashSet<>();
        ArrayDeque<MethodNode> pending = new ArrayDeque<>(); pending.add(entry);
        while (!pending.isEmpty()) {
            MethodNode current = pending.removeFirst(); if (!result.add(current)) continue;
            for (AbstractInsnNode instruction : current.instructions) if (instruction instanceof InvokeDynamicInsnNode dynamic)
                for (Object argument : dynamic.bsmArgs) if (argument instanceof Handle handle && handle.getOwner().equals(type.name))
                    pending.add(method(type, handle.getName(), handle.getDesc()));
        }
        return List.copyOf(result);
    }

    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static AbstractInsnNode previous(AbstractInsnNode instruction) { do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0); return instruction; }
    private static AbstractInsnNode next(AbstractInsnNode instruction) { do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0); return instruction; }
    private static List<AbstractInsnNode> executable(MethodNode method) { return java.util.Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList(); }
    private static boolean hasInteger(MethodNode method, int expected) { return executable(method).stream().anyMatch(instruction -> { try { return integer(instruction) == expected; } catch (IllegalStateException ignored) { return false; } }); }
    private static int integer(AbstractInsnNode instruction) {
        if (instruction != null && instruction.getOpcode() >= Opcodes.ICONST_M1 && instruction.getOpcode() <= Opcodes.ICONST_5) return instruction.getOpcode() - Opcodes.ICONST_0;
        if (instruction instanceof IntInsnNode value) return value.operand;
        if (instruction instanceof LdcInsnNode value && value.cst instanceof Integer number) return number;
        throw new IllegalStateException("Expected an actual integer constant instruction");
    }
    private static boolean hasString(MethodNode method, String expected) { for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof LdcInsnNode value && expected.equals(value.cst)) return true; return false; }
    private static boolean fieldAccess(MethodNode method, String owner, String name, int opcode) { for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof FieldInsnNode field && field.owner.equals(owner) && field.name.equals(name) && field.getOpcode() == opcode) return true; return false; }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void write(JsonObject report) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent()); Files.writeString(output,
                new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
