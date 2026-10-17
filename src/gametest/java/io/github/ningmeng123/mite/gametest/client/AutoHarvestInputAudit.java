package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.AutoHarvestInput;
import io.github.ningmeng123.mite.client.ClientIngestionDelay;
import io.github.ningmeng123.mite.rules.AutoUseState;
import io.github.ningmeng123.mite.rules.IngestionDelay;
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
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
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
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Checks actual client transformations without a Minecraft constructor, world, input device or window. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AutoHarvestInputAudit implements DataProvider {
    private static final String MINECRAFT = "net/minecraft/client/Minecraft";
    private static final String MOUSE = "net/minecraft/client/MouseHandler";
    private static final String KEY_MAPPING = "net/minecraft/client/KeyMapping";
    private static final String OPTIONS = "net/minecraft/client/Options";
    private static final String MODE = "net/minecraft/client/multiplayer/MultiPlayerGameMode";
    private static final String GUI = "net/minecraft/client/gui/Gui";
    private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphics";
    private static final String INPUT = "io/github/ningmeng123/mite/client/AutoHarvestInput";
    private static final String AUTO_USE = "io/github/ningmeng123/mite/rules/AutoUseState";
    private static final String HARVEST_STATE = "io/github/ningmeng123/mite/rules/AutoHarvestState";
    private static final String INGESTION = "io/github/ningmeng123/mite/client/ClientIngestionDelay";
    private static final String INGESTION_DELAY = "io/github/ningmeng123/mite/rules/IngestionDelay";
    private static final String SCREEN_PAYLOAD = "io/github/ningmeng123/mite/network/AutoRestockScreenPayload";
    private static final String PACKET_DISTRIBUTOR = "net/neoforged/neoforge/network/PacketDistributor";
    private static final String MIXINS = "io.github.ningmeng123.mite.mixin.client.";
    private static final String MINECRAFT_MIXIN = MIXINS + "AutoHarvestMinecraftMixin";
    private static final String MOUSE_MIXIN = MIXINS + "AutoHarvestMouseMixin";
    private static final String HOTBAR_MIXIN = MIXINS + "AutoModeHotbarMixin";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String RETURN_CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private final Path output;

    private AutoHarvestInputAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.autoHarvestInputAudit")) return;
        event.getGenerator().addProvider(true, new AutoHarvestInputAudit(Path.of(System.getProperty(
                "mite.autoHarvestInputAuditOutput", "build/verification/auto-harvest-input-audit.json"))));
    }

    @Override public String getName() { return "Actual AHM/AUM input, ingestion cadence and hotbar transformations without a game window"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("input_simulated", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("opengl_or_glfw_invoked", false);
        report.addProperty("visual_or_manual_test_passed", false);
        report.addProperty("sodium_loaded", ModList.get().isLoaded("sodium"));
        report.addProperty("modernfix_loaded", ModList.get().isLoaded("modernfix"));
        JsonArray exports = new JsonArray(); report.add("defined_targets", exports);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "Actual transformed-class export is required");
            ClassNode minecraft = defined(MINECRAFT, exports);
            ClassNode mouse = defined(MOUSE, exports);
            ClassNode gui = defined(GUI, exports);
            ClassNode input = resource(INPUT);
            auditHeldAttack(minecraft, report);
            auditUseInterception(minecraft, report);
            auditAutomaticIngestion(input, report);
            auditNativeCombat(minecraft, report);
            auditLifecycle(minecraft, mouse, report);
            auditContextAndModeReset(input, report);
            auditGreenHotbar(gui, input, report);
            auditIngestionCadence(resource(INGESTION), report);
            auditScreenPausePayload(minecraft, resource(SCREEN_PAYLOAD), report);
            auditNoSyntheticActions(minecraft, mouse, input, report);
            auditUseStateAndEligibility(report);
            auditTargetMatching(report);
            report.addProperty("status", "passed_actual_client_transforms_without_window_or_input");
            write(report); return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed"); report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void auditHeldAttack(ClassNode minecraft, JsonObject report) {
        MethodNode handle = method(minecraft, "handleKeybinds", "()V");
        MethodNode begin = callingHandler(minecraft, MINECRAFT_MIXIN, "beginInput");
        MethodNode held = callingHandler(minecraft, MINECRAFT_MIXIN, "wantsContinue");
        check(reachable(minecraft, handle, begin), "Native handleKeybinds reaches the actual begin-input handler");
        check(reachable(minecraft, handle, held), "Native handleKeybinds reaches the actual held-attack wrapper");
        check(calls(begin, INPUT, "beginInput") == 1, "Input state is evaluated once by the entry handler");
        check(indexOfCall(handle, minecraft.name, begin.name) < indexOfCall(handle, KEY_MAPPING, "consumeClick"),
                "The begin-input handler precedes the first native click consumer");
        check(calls(held, OPERATION, "call") == 1, "The held-input wrapper still invokes the original operation");
        check(field(held, OPTIONS, "keyAttack", Opcodes.GETFIELD), "The wrapper explicitly selects the attack key mapping");
        check(field(held, OPTIONS, "keyUse", Opcodes.GETFIELD) && calls(held, INPUT, "wantsUse") == 1,
                "The same actual wrapper holds only the identified use mapping for AUM");
        check(calls(held, INPUT, "consumeAttack") == 1 && hasOpcode(held, Opcodes.ICONST_0),
                "The AUM activation/cancel click also suppresses native continuous mining for that input frame");
        check(hasOpcode(held, Opcodes.IF_ACMPEQ) || hasOpcode(held, Opcodes.IF_ACMPNE),
                "Attack/use are selected by mapping object identity rather than changing every held key");
        check(field(handle, MINECRAFT, "screen", Opcodes.GETFIELD)
                        && calls(handle, MOUSE, "isMouseGrabbed") == 1
                        && calls(handle, MINECRAFT, "continueAttack") == 1,
                "Native screen, grabbed-mouse and continued-attack gates are preserved");
        check(indexOfCall(handle, MOUSE, "isMouseGrabbed") < indexOfCall(handle, MINECRAFT, "continueAttack"),
                "The actual final attack boolean still checks grabbed mouse before continuing");
        check(calls(handle, MODE, "releaseUsingItem") == 1,
                "The native release-use path remains present and reads the wrapped held-use predicate");
        report.addProperty("actual_begin_handler", begin.name);
        report.addProperty("actual_held_attack_handler", held.name);
        report.addProperty("original_key_read_and_native_final_gates_preserved", true);
        report.addProperty("actual_held_use_handler", held.name);
    }

    private static void auditUseInterception(ClassNode minecraft, JsonObject report) {
        MethodNode use = method(minecraft, "startUseItem", "()V");
        List<MethodNode> candidates = minecraft.methods.stream().filter(candidate -> mergedFrom(candidate, MINECRAFT_MIXIN)
                && calls(candidate, CALLBACK, "cancel") > 0 && reachable(minecraft, use, candidate)).toList();
        check(candidates.size() == 1, "Exactly one merged AHM/AUM handler can cancel native item use");
        MethodNode interception = candidates.getFirst();
        check(interception.instructions.iterator().hasNext(), "The actual use interceptor has executable code");
        check(callsOwner(interception, INPUT), "The actual use interceptor delegates its decision to shared input state");
        check(calls(interception, INPUT, "consumeUse") == 1 && calls(interception, INPUT, "automaticUse") == 1,
                "Actual native use entry checks the AHM gesture and the ingestion-only AUM path");
        check(calls(interception, CALLBACK, "cancel") == 2
                        && indexOfCall(interception, INPUT, "consumeUse") < indexOfCall(interception, INPUT, "automaticUse")
                        && indexOfCall(interception, INPUT, "automaticUse") < lastIndexOfCall(interception, CALLBACK, "cancel"),
                "Both the AHM gesture and the accepted AUM branch cancel the original block/entity-use body");
        check(field(interception, MINECRAFT, "rightClickDelay", Opcodes.PUTFIELD) && hasInteger(interception, 4),
                "AUM retries retain the native four-tick right-click throttle");
        check(hasOpcode(interception, Opcodes.IFEQ) || hasOpcode(interception, Opcodes.IFNE),
                "Use cancellation is conditional so ordinary right-click use remains available");
        check(calls(use, MODE, "isDestroying") == 1, "Native item-use destruction guard remains present");
        check(indexOfCall(use, minecraft.name, interception.name) < indexOfCall(use, MODE, "isDestroying"),
                "The combined-button interceptor executes before the original use-item body");
        report.addProperty("actual_conditional_use_handler", interception.name);
        report.addProperty("ordinary_native_use_body_preserved", true);
        report.addProperty("aum_intercepts_before_native_block_or_entity_interactions", true);
    }

    private static void auditAutomaticIngestion(ClassNode input, JsonObject report) {
        MethodNode automatic = method(input, "automaticUse", "(L" + MINECRAFT + ";)Z");
        check(calls(automatic, MODE, "useItem") == 1, "AUM issues exactly one native main-hand useItem transaction per accepted attempt");
        check(calls(automatic, INPUT, "context") == 1 && calls(automatic, AUTO_USE, "isActive") == 1
                        && calls(automatic, AUTO_USE, "matches") == 1,
                "Automatic use requires a live context, active latch and matching item");
        check(field(automatic, OPTIONS, "keyUse", Opcodes.GETFIELD) && calls(automatic, KEY_MAPPING, "isDown") == 1,
                "Physical right-click use remains on the ordinary native path");
        check(calls(automatic, INGESTION, "ready") == 1
                        && calls(automatic, "io/github/ningmeng123/mite/survival/FoodConsumption", "canIngest") == 1
                        && callsNamed(automatic, "isOnCooldown") == 1,
                "AUM checks the shared ingestion deadline, actual eating eligibility and native item cooldown");
        check(indexOfCall(automatic, INGESTION, "ready") < indexOfCall(automatic, MODE, "useItem")
                        && indexOfCall(automatic, "io/github/ningmeng123/mite/survival/FoodConsumption", "canIngest")
                        < indexOfCall(automatic, MODE, "useItem"),
                "Eating eligibility and cadence are checked before the native use transaction");
        check(callsNamed(automatic, "onClickInput") == 1 && callsNamed(automatic, "isCanceled") == 1,
                "AUM retains the cancellable NeoForge click-input hook");
        for (MethodNode candidate : input.methods) for (AbstractInsnNode instruction : candidate.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(MODE))
                check(candidate == automatic && call.name.equals("useItem"),
                        "The automatic controller never performs useItemOn, interact, entity attacks or mining actions");
        check(!field(automatic, MINECRAFT, "hitResult", Opcodes.GETFIELD),
                "Automatic ingestion never dispatches on the crosshair's block or entity target");
        report.addProperty("aum_uses_only_native_mainhand_ingestion_transaction", true);
        report.addProperty("aum_preserves_cooldowns_eligibility_and_cancellable_click_event", true);
    }

    private static void auditNativeCombat(ClassNode minecraft, JsonObject report) {
        MethodNode attack = method(minecraft, "startAttack", "()Z");
        MethodNode continued = method(minecraft, "continueAttack", "(Z)V");
        check(calls(continued, MODE, "continueDestroyBlock") == 1,
                "Native held attack still calls the original continueDestroyBlock exactly once");
        check(calls(continued, MODE, "stopDestroyBlock") == 1, "Native stop-destroy path is preserved");
        check(calls(continued, "net/minecraft/client/player/LocalPlayer", "isUsingItem") == 1,
                "Native continued attack still respects active item use");
        check(calls(attack, MODE, "attack") == 1 && calls(attack, MODE, "startDestroyBlock") == 1,
                "The native single-click entity attack and mining start are preserved");
        MethodNode record = callingHandler(minecraft, MINECRAFT_MIXIN, "recordAttempt");
        check(reachable(minecraft, continued, record) && calls(record, CALLBACK, "cancel") == 0,
                "Native continueAttack reaches only a non-cancelling attempt observer");
        check(indexOfCall(continued, minecraft.name, record.name) < indexOfCall(continued, MODE, "continueDestroyBlock"),
                "The attempt observer precedes but does not replace original continuous mining");
        var cancelHandlers = minecraft.methods.stream().filter(candidate -> mergedFrom(candidate, MINECRAFT_MIXIN)
                && calls(candidate, INPUT, "consumeAttack") == 1 && reachable(minecraft, attack, candidate)).toList();
        check(cancelHandlers.size() == 1, "Exactly one actual AUM cancellation handler is merged into single-click attack");
        MethodNode cancel = cancelHandlers.getFirst();
        check(reachable(minecraft, attack, cancel) && calls(cancel, RETURN_CALLBACK, "setReturnValue") == 1
                        && hasOpcode(cancel, Opcodes.ICONST_0),
                "AUM gesture/cancellation consumes the native attack before entity attack or mining start");
        check(indexOfCall(attack, minecraft.name, cancel.name) < indexOfCall(attack, MODE, "attack")
                        && indexOfCall(attack, minecraft.name, cancel.name) < indexOfCall(attack, MODE, "startDestroyBlock"),
                "The actual conditional cancellation precedes every native single-click combat action");
        for (MethodNode handler : minecraft.methods) if (mergedFrom(handler, MINECRAFT_MIXIN)) {
            check(handler == cancel || !reachable(minecraft, attack, handler),
                    "Only the AUM conditional cancellation hooks the native single-click attack");
            check(handler == record || !reachable(minecraft, continued, handler),
                    "The attempt observer is the sole auto-harvest hook in native continuous mining");
        }
        report.addProperty("actual_attempt_observer", record.name);
        report.addProperty("single_click_native_combat_preserved_after_aum_gesture_filter", true);
        report.addProperty("actual_conditional_attack_handler", cancel.name);
        report.addProperty("native_mining_speed_and_stop_path_preserved", true);
    }

    private static void auditContextAndModeReset(ClassNode input, JsonObject report) {
        MethodNode reset = method(input, "reset", "()V");
        check(calls(reset, HARVEST_STATE, "clear") == 1 && calls(reset, AUTO_USE, "clear") == 1
                        && field(reset, INPUT, "suppressAttack", Opcodes.PUTSTATIC),
                "Each actual lifecycle reset clears both modes and pending attack suppression");
        MethodNode context = method(input, "context", "(L" + MINECRAFT + ";)Z");
        check(calls(context, INPUT, "reset") == 1, "Invalid context reaches the shared two-mode reset");
        for (String name : List.of("player", "level", "gameMode", "options", "screen"))
            check(field(context, MINECRAFT, name, Opcodes.GETFIELD), "Input context validates native " + name);
        for (String name : List.of("getOverlay", "isWindowActive", "isPaused", "isMouseGrabbed", "isAlive", "isRemoved", "isSleeping", "isSpectator"))
            check(callsNamed(context, name) > 0, "Input context validates " + name);
        check(calls(context, AUTO_USE, "clear") > 0 && calls(context, HARVEST_STATE, "clear") > 0,
                "Player/level/slot/keybinding transitions can clear both state machines");
        check(calls(context, AUTO_USE, "waitingForRestock") == 1,
                "AUM distinguishes its own short-lived returned vessel from an unrelated manual item change");
        MethodNode begin = method(input, "beginInput", "(L" + MINECRAFT + ";)V");
        check(calls(begin, INGESTION, "ingestion") == 1 && calls(begin, AUTO_USE, "update") == 1
                        && calls(begin, AUTO_USE, "isActive") > 0 && calls(begin, HARVEST_STATE, "clear") > 0,
                "The actual entry feeds only ingestion-eligible items into AUM and excludes concurrent AHM");
        report.addProperty("both_modes_clear_for_invalid_context_and_lifecycle_hooks", true);
    }

    private static void auditGreenHotbar(ClassNode gui, ClassNode input, JsonObject report) {
        MethodNode render = method(gui, "renderItemHotbar", "(L" + GRAPHICS + ";Lnet/minecraft/client/DeltaTracker;)V");
        MethodNode highlight = callingHandler(gui, HOTBAR_MIXIN, "highlighted");
        check(reachable(gui, render, highlight), "The actual native hotbar reaches the merged automatic-mode renderer");
        check(calls(highlight, GRAPHICS, "fill") == 5 && hasInteger(highlight, 0xB03D7D32)
                        && hasInteger(highlight, 0xFF91DC74),
                "The merged HUD draws one green selected-slot background and four green border strips");
        check(field(highlight, "net/minecraft/world/entity/player/Inventory", "selected", Opcodes.GETFIELD)
                        && hasOpcode(highlight, Opcodes.IF_ICMPNE) && hasOpcode(highlight, Opcodes.IADD),
                "The HUD limits the tint to the selected hotbar seed, excluding the offhand slot");
        check(calls(highlight, OPERATION, "call") == 1
                        && indexOfCall(highlight, GRAPHICS, "fill") < indexOfCall(highlight, OPERATION, "call"),
                "The native item render is preserved once after the optional background/frame");
        MethodNode active = method(input, "highlighted", "(L" + MINECRAFT + ";)Z");
        check(calls(active, INPUT, "context") == 1 && calls(active, HARVEST_STATE, "isActive") == 1
                        && calls(active, AUTO_USE, "isActive") == 1,
                "Both AHM and AUM can supply the selected-slot highlight in a valid context");
        report.addProperty("actual_green_hotbar_handler", highlight.name);
        report.addProperty("both_mode_green_background_and_frame_injected_before_native_item", true);
        report.addProperty("hud_pixels_rendered_or_visually_verified", false);
    }

    private static void auditIngestionCadence(ClassNode ingestion, JsonObject report) {
        MethodNode ready = method(ingestion, "ready", "(L" + MINECRAFT + ";Lnet/minecraft/world/item/ItemStack;)Z");
        check(calls(ready, "net/minecraft/Util", "getMillis") == 1 && calls(ready, INGESTION_DELAY, "ready") == 1
                        && calls(ready, INGESTION_DELAY, "clear") == 1,
                "Client cadence uses real monotonic milliseconds and clears for a new player identity");
        MethodNode start = method(ingestion, "start", "(Lnet/neoforged/neoforge/event/entity/living/LivingEntityUseItemEvent$Start;)V");
        MethodNode finish = method(ingestion, "finished", "(Lnet/neoforged/neoforge/event/entity/living/LivingEntityUseItemEvent$Finish;)V");
        check(calls(start, INGESTION, "ready") == 1 && callsNamed(start, "setCanceled") == 1
                        && field(start, MINECRAFT, "player", Opcodes.GETFIELD) && hasOpcode(start, Opcodes.IF_ACMPNE),
                "The client start event rejects early ingestion only for the current local player");
        check(calls(finish, INGESTION, "ingestion") == 1 && calls(finish, INGESTION_DELAY, "finished") == 1
                        && calls(finish, "net/minecraft/Util", "getMillis") == 1
                        && field(finish, MINECRAFT, "player", Opcodes.GETFIELD) && hasOpcode(finish, Opcodes.IF_ACMPNE),
                "A completed local eating/drinking event arms the real-time delay");
        check(calls(finish, INPUT, "finishedIngestion") == 1,
                "The actual local finish event also records the returned vessel for AUM restock waiting");
        IngestionDelay delay = new IngestionDelay();
        check(delay.ready(10L), "No ingestion deadline exists before the first completion");
        delay.finished(5_000L);
        check(!delay.ready(5_000L) && !delay.ready(5_999L) && delay.ready(6_000L),
                "The real client delay blocks through999ms and opens exactly at1000ms");
        delay.clear(); check(delay.ready(10L), "Changing player identity can discard the previous deadline");
        report.addProperty("client_ingestion_real_time_1000ms_boundary_executed", true);
        report.addProperty("actual_local_ingestion_finish_and_start_hooks_verified", true);
    }

    private static void auditUseStateAndEligibility(JsonObject report) {
        AutoUseState<String> state = new AutoUseState<>();
        state.update(true, true, "apple"); check(!state.isActive(), "Attack-first chord cannot activate AUM");
        state.update(false, true, "apple"); state.update(true, true, "apple");
        check(state.matches("apple"), "Use-first chord activates AUM for its current item");
        state.update(false, false, "apple"); check(state.matches("apple"), "Releasing both physical inputs preserves AUM");
        state.update(true, false, "apple"); check(!state.isActive(), "The next attack cancels AUM");
        state.update(false, true, null); state.update(true, true, null);
        check(!state.isActive(), "Non-ingestion items cannot arm the state machine");
        state.update(false, true, "apple"); state.update(true, true, "apple");
        state.clear(); check(!state.isActive() && !state.isArmed(), "Lifecycle clearing removes active and armed AUM state");
        state.update(false, true, "milk"); state.update(true, true, "milk");
        state.finished("milk", "bucket", 5_000L);
        check(state.waitingForRestock("bucket", 7_999L) && !state.waitingForRestock("bucket", 8_000L)
                        && !state.waitingForRestock("bowl", 5_100L),
                "Only the actual returned vessel can wait for its source refill, for strictly less than three seconds");
        state.clear(); check(!state.waitingForRestock("bucket", 5_100L), "Lifecycle clearing also invalidates pending vessel acknowledgement");
        check(ClientIngestionDelay.ingestion(new ItemStack(Items.APPLE))
                        && ClientIngestionDelay.ingestion(new ItemStack(Items.POTION))
                        && ClientIngestionDelay.ingestion(new ItemStack(Items.MILK_BUCKET)),
                "The actual item-animation gate accepts food and drinks");
        check(!ClientIngestionDelay.ingestion(new ItemStack(Items.BOW))
                        && !ClientIngestionDelay.ingestion(new ItemStack(Items.SHIELD))
                        && !ClientIngestionDelay.ingestion(new ItemStack(Items.STONE))
                        && !ClientIngestionDelay.ingestion(ItemStack.EMPTY),
                "The actual item-animation gate rejects bow, shield, placement and empty-stack actions");
        report.addProperty("executed_aum_gesture_latch_cancel_reset_and_ingestion_item_cases", true);
    }

    private static void auditLifecycle(ClassNode minecraft, ClassNode mouse, JsonObject report) {
        JsonArray hooks = new JsonArray();
        requireReset(minecraft, MINECRAFT_MIXIN, "setScreen", "(Lnet/minecraft/client/gui/screens/Screen;)V", hooks);
        requireReset(minecraft, MINECRAFT_MIXIN, "setWindowActive", "(Z)V", hooks);
        requireReset(minecraft, MINECRAFT_MIXIN, "clearClientLevel", "(Lnet/minecraft/client/gui/screens/Screen;)V", hooks);
        requireReset(mouse, MOUSE_MIXIN, "releaseMouse", "()V", hooks);
        report.add("actual_lifecycle_reset_hooks", hooks);
    }

    private static void requireReset(ClassNode type, String mixin, String name, String descriptor, JsonArray hooks) {
        MethodNode nativeMethod = method(type, name, descriptor);
        // Teardown legitimately reaches other native lifecycle methods, which have
        // their own reset hooks. Require this entry's direct injection rather than
        // counting every reset reachable through screen/cursor teardown as its hook.
        var handlers = type.methods.stream().filter(candidate -> mergedFrom(candidate, mixin)
                && calls(candidate, INPUT, "reset") > 0 && calls(nativeMethod, type.name, candidate.name) == 1).toList();
        check(handlers.size() == 1, "Exactly one directly injected reset handler for " + type.name + "." + name);
        hooks.add(type.name + "." + name + " -> " + handlers.getFirst().name);
    }

    private static void auditNoSyntheticActions(ClassNode minecraft, ClassNode mouse, ClassNode input, JsonObject report) {
        MethodNode screen = screenPauseHandler(minecraft);
        java.util.ArrayList<MethodNode> methods = new java.util.ArrayList<>(input.methods);
        minecraft.methods.stream().filter(candidate -> mergedFrom(candidate, MINECRAFT_MIXIN)).forEach(methods::add);
        mouse.methods.stream().filter(candidate -> mergedFrom(candidate, MOUSE_MIXIN)).forEach(methods::add);
        for (MethodNode method : methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                check(!(call.owner.equals(KEY_MAPPING) && Set.of("setDown", "set", "click", "consumeClick").contains(call.name)),
                        "Auto harvest never rewrites native key state or generates synthetic clicks: " + method.name);
                check(!(call.owner.equals(MINECRAFT) && Set.of("startAttack", "continueAttack", "startUseItem").contains(call.name)),
                        "Auto harvest never manually invokes attack/use actions: " + method.name);
                boolean screenSuppression = method == screen && call.owner.equals(PACKET_DISTRIBUTOR) && call.name.equals("sendToServer");
                check(!call.owner.startsWith("net/minecraft/network/protocol/")
                                && (!call.owner.equals(PACKET_DISTRIBUTOR) || screenSuppression)
                                && !(call.owner.startsWith("net/minecraft/client/multiplayer/") && call.name.startsWith("send")),
                        "Auto harvest never sends custom action packets: " + method.name);
            }
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW)
                check(!type.desc.startsWith("net/minecraft/network/protocol/"), "Auto harvest never constructs action packets");
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD)
                check(!field.owner.equals(KEY_MAPPING) && !field.owner.equals(MODE)
                                && !(field.owner.equals(MINECRAFT) && !field.name.equals("rightClickDelay")),
                        "Automatic modes do not rewrite key fields, mining distance/progress or native attack timers");
        }
        report.addProperty("no_synthetic_key_writes_clicks_attack_use_or_inventory_action_packets", true);
        report.addProperty("only_server_bound_screen_boolean_suppression_payload_is_allowed", true);
    }

    private static MethodNode screenPauseHandler(ClassNode minecraft) {
        MethodNode setScreen = method(minecraft, "setScreen", "(Lnet/minecraft/client/gui/screens/Screen;)V");
        var handlers = minecraft.methods.stream().filter(candidate -> mergedFrom(candidate, MINECRAFT_MIXIN)
                && calls(setScreen, minecraft.name, candidate.name) == 1 && calls(candidate, SCREEN_PAYLOAD, "<init>") == 1).toList();
        check(handlers.size() == 1, "Exactly one directly injected screen handler constructs the suppression payload");
        return handlers.getFirst();
    }

    private static void auditScreenPausePayload(ClassNode minecraft, ClassNode payload, JsonObject report) {
        MethodNode screen = screenPauseHandler(minecraft);
        check(calls(screen, PACKET_DISTRIBUTOR, "sendToServer") == 1
                        && calls(screen, MINECRAFT, "getConnection") == 2
                        && calls(screen, "net/minecraft/client/multiplayer/ClientPacketListener", "hasChannel") == 1,
                "The actual screen notification checks its connection, then checks the negotiated suppression channel");
        int sendIndex = indexOfCall(screen, PACKET_DISTRIBUTOR, "sendToServer");
        int channelIndex = indexOfCall(screen, "net/minecraft/client/multiplayer/ClientPacketListener", "hasChannel");
        FieldInsnNode playerRead = null;
        for (AbstractInsnNode instruction : screen.instructions)
            if (instruction instanceof FieldInsnNode read && read.owner.equals(MINECRAFT)
                    && read.name.equals("player") && read.getOpcode() == Opcodes.GETFIELD) {
                check(playerRead == null, "The screen hook has exactly one local-player gate"); playerRead = read;
            }
        check(playerRead != null, "The screen hook explicitly checks its local player");
        screenGuardSkipsSend(screen, playerRead, Opcodes.IFNULL, sendIndex, "Missing player skips the suppression send");
        screenGuardSkipsSend(screen, screen.instructions.get(indexOfCall(screen, MINECRAFT, "getConnection")),
                Opcodes.IFNULL, sendIndex, "Missing connection skips the suppression send");
        AbstractInsnNode channelType = nextExecutable(screen.instructions.get(lastIndexOfCall(screen, MINECRAFT, "getConnection")));
        check(channelType instanceof FieldInsnNode read && read.owner.equals(SCREEN_PAYLOAD)
                        && read.name.equals("TYPE") && read.getOpcode() == Opcodes.GETSTATIC
                        && nextExecutable(channelType) == screen.instructions.get(channelIndex)
                        && channelIndex < indexOfCall(screen, SCREEN_PAYLOAD, "<init>"),
                "The negotiated channel is the exact boolean suppression payload and is checked before constructing it");
        screenGuardSkipsSend(screen, screen.instructions.get(channelIndex), Opcodes.IFEQ, sendIndex,
                "An unnegotiated suppression channel skips the send");
        check(calls(screen, SCREEN_PAYLOAD, "<init>") == 1
                        && payload.recordComponents != null && payload.recordComponents.size() == 1
                        && payload.recordComponents.getFirst().name.equals("open") && payload.recordComponents.getFirst().descriptor.equals("Z")
                        && payload.fields.stream().filter(field -> (field.access & Opcodes.ACC_STATIC) == 0).count() == 1,
                "The only serialized/instance payload value is the screen-open boolean, with no item, count, slot, UUID or inventory declaration");
        for (AbstractInsnNode instruction : screen.instructions) if (instruction instanceof TypeInsnNode type) {
            if (type.getOpcode() == Opcodes.NEW)
                check(type.desc.equals(SCREEN_PAYLOAD), "The allowed screen hook constructs only its boolean suppression payload");
            if (type.getOpcode() == Opcodes.ANEWARRAY
                    && type.desc.equals("net/minecraft/network/protocol/common/custom/CustomPacketPayload")) {
                AbstractInsnNode length = type.getPrevious();
                while (length != null && length.getOpcode() < 0) length = length.getPrevious();
                check(length != null && length.getOpcode() == Opcodes.ICONST_0,
                        "The allowed send has an empty additional-payload array");
            }
        }
        check(!hasOpcode(screen, Opcodes.AASTORE), "No attack/use/inventory payload can be hidden beside the screen boolean");
        method(payload, "<init>", "(Z)V");
        MethodNode initializer = method(payload, "<clinit>", "()V");
        check(field(initializer, "net/minecraft/network/codec/ByteBufCodecs", "BOOL", Opcodes.GETSTATIC),
                "The screen notification wire codec is a boolean");
        long pauses = 0;
        for (MethodNode candidate : payload.methods) for (AbstractInsnNode instruction : candidate.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("io/github/ningmeng123/mite/survival/AutoRestock")) {
                check(call.name.equals("pause"), "The suppression packet cannot authorize an inventory action"); pauses++;
            }
            if (instruction instanceof MethodInsnNode call)
                check(!call.owner.equals(MODE) && !call.owner.equals(KEY_MAPPING) && !call.owner.equals(PACKET_DISTRIBUTOR)
                                && !call.owner.startsWith("net/minecraft/world/inventory/"),
                        "The screen packet handler has no attack, use, key, inventory-menu or secondary-packet action");
        }
        check(pauses == 1 && payload.methods.stream().anyMatch(candidate -> callsNamed(candidate, "player") == 1),
                "The server handler applies pause only to its authenticated network-context player");
        report.addProperty("actual_screen_suppression_handler", screen.name);
        report.addProperty("screen_suppression_requires_local_player_connection_and_negotiated_channel", true);
        report.addProperty("screen_packet_contains_only_boolean_and_only_suppresses_authenticated_player", true);
    }

    private static void screenGuardSkipsSend(MethodNode method, AbstractInsnNode value, int opcode, int sendIndex, String message) {
        AbstractInsnNode next = nextExecutable(value);
        check(next instanceof JumpInsnNode guard && guard.getOpcode() == opcode
                && method.instructions.indexOf(guard.label) > sendIndex, message);
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }

    private static void auditTargetMatching(JsonObject report) {
        JsonArray cases = new JsonArray(); report.add("executed_target_match_cases", cases);
        BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        match(cases, "same_block", stone, stone, true);
        match(cases, "different_stone_blocks", stone, Blocks.COBBLESTONE.defaultBlockState(), false);
        match(cases, "grass_to_dirt", Blocks.GRASS_BLOCK.defaultBlockState(), dirt, true);
        match(cases, "same_log_axis_changes", Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), true);
        match(cases, "legacy_log_species_and_wood", Blocks.OAK_LOG.defaultBlockState(), Blocks.BIRCH_WOOD.defaultBlockState(), true);
        match(cases, "new_log_species_remain_separate", Blocks.OAK_LOG.defaultBlockState(), Blocks.CHERRY_LOG.defaultBlockState(), false);
        match(cases, "stripped_logs_remain_separate", Blocks.OAK_LOG.defaultBlockState(), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), false);
        match(cases, "legacy_leaf_species_and_state", Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true),
                Blocks.JUNGLE_LEAVES.defaultBlockState(), true);
        match(cases, "new_leaf_species_remain_separate", Blocks.OAK_LEAVES.defaultBlockState(), Blocks.ACACIA_LEAVES.defaultBlockState(), false);
        match(cases, "legacy_plank_species", Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.JUNGLE_PLANKS.defaultBlockState(), true);
        match(cases, "new_plank_species_remain_separate", Blocks.OAK_PLANKS.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState(), false);
        match(cases, "legacy_sapling_species_and_stage", Blocks.OAK_SAPLING.defaultBlockState().setValue(BlockStateProperties.STAGE, 1),
                Blocks.BIRCH_SAPLING.defaultBlockState(), true);
        match(cases, "wool_colors", Blocks.WHITE_WOOL.defaultBlockState(), Blocks.BLACK_WOOL.defaultBlockState(), true);
        match(cases, "carpet_colors", Blocks.RED_CARPET.defaultBlockState(), Blocks.BLUE_CARPET.defaultBlockState(), true);
        match(cases, "moss_is_not_wool_carpet", Blocks.MOSS_CARPET.defaultBlockState(), Blocks.WHITE_CARPET.defaultBlockState(), false);
        match(cases, "stained_clay_colors", Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.BLUE_TERRACOTTA.defaultBlockState(), true);
        match(cases, "plain_clay_is_not_stained_clay", Blocks.TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), false);
        match(cases, "glazed_clay_patterns_remain_separate", Blocks.RED_GLAZED_TERRACOTTA.defaultBlockState(), Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), false);
        match(cases, "legacy_sandstone_variants", Blocks.SANDSTONE.defaultBlockState(), Blocks.CHISELED_SANDSTONE.defaultBlockState(), true);
        match(cases, "red_sandstone_remains_separate", Blocks.SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), false);
        match(cases, "legacy_stone_brick_variants", Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), true);
        match(cases, "stone_brick_does_not_match_cobble", Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), false);
        match(cases, "legacy_cobble_wall_variants", Blocks.COBBLESTONE_WALL.defaultBlockState(), Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(), true);
        match(cases, "new_stone_wall_remains_separate", Blocks.COBBLESTONE_WALL.defaultBlockState(), Blocks.DIORITE_WALL.defaultBlockState(), false);
        match(cases, "legacy_quartz_variants_and_axis", Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), true);
        match(cases, "smooth_quartz_remains_separate", Blocks.QUARTZ_BLOCK.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState(), false);
        match(cases, "legacy_tall_grass_metadata_remains_distinct", Blocks.SHORT_GRASS.defaultBlockState(), Blocks.FERN.defaultBlockState(), false);
        match(cases, "crop_age_changes", Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 0),
                Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7), true);
        match(cases, "different_crop_species", Blocks.WHEAT.defaultBlockState(), Blocks.CARROTS.defaultBlockState(), false);
        match(cases, "redstone_lit_state_changes", Blocks.REDSTONE_ORE.defaultBlockState().setValue(BlockStateProperties.LIT, false),
                Blocks.REDSTONE_ORE.defaultBlockState().setValue(BlockStateProperties.LIT, true), true);
        BlockState oakSlab = Blocks.OAK_SLAB.defaultBlockState(), spruceSlab = Blocks.SPRUCE_SLAB.defaultBlockState();
        match(cases, "legacy_wood_half_slab_species_and_position", slab(oakSlab, SlabType.BOTTOM), slab(spruceSlab, SlabType.TOP), true);
        match(cases, "legacy_wood_double_slab_species", slab(oakSlab, SlabType.DOUBLE), slab(spruceSlab, SlabType.DOUBLE), true);
        match(cases, "legacy_wood_double_and_half_differ", slab(oakSlab, SlabType.BOTTOM), slab(spruceSlab, SlabType.DOUBLE), false);
        match(cases, "same_wood_double_and_half_differ", slab(oakSlab, SlabType.BOTTOM), slab(oakSlab, SlabType.DOUBLE), false);
        match(cases, "same_wood_half_slab_position_changes", slab(oakSlab, SlabType.BOTTOM), slab(oakSlab, SlabType.TOP), true);
        BlockState brickSlab = Blocks.BRICK_SLAB.defaultBlockState(), sandstoneSlab = Blocks.SANDSTONE_SLAB.defaultBlockState();
        match(cases, "legacy_stone_half_slab_material_and_position", slab(brickSlab, SlabType.BOTTOM), slab(sandstoneSlab, SlabType.TOP), true);
        match(cases, "legacy_stone_double_slab_material", slab(brickSlab, SlabType.DOUBLE), slab(sandstoneSlab, SlabType.DOUBLE), true);
        match(cases, "legacy_stone_double_and_half_differ", slab(brickSlab, SlabType.BOTTOM), slab(sandstoneSlab, SlabType.DOUBLE), false);
        match(cases, "same_stone_double_and_half_differ", slab(brickSlab, SlabType.BOTTOM), slab(brickSlab, SlabType.DOUBLE), false);
        match(cases, "wood_and_stone_half_slabs_differ", slab(oakSlab, SlabType.BOTTOM), slab(brickSlab, SlabType.BOTTOM), false);
        match(cases, "new_slab_material_remains_separate", slab(oakSlab, SlabType.BOTTOM), Blocks.CHERRY_SLAB.defaultBlockState(), false);
        report.addProperty("real_target_match_cases_executed", cases.size());
        report.addProperty("matcher_executed_symmetrically_without_minecraft_or_input", true);
    }

    private static BlockState slab(BlockState state, SlabType type) { return state.setValue(BlockStateProperties.SLAB_TYPE, type); }

    private static void match(JsonArray cases, String name, BlockState first, BlockState second, boolean expected) {
        boolean forward = AutoHarvestInput.matches(first, second), reverse = AutoHarvestInput.matches(second, first);
        check(forward == expected && reverse == expected, "Actual block-state matcher: " + name + " expected " + expected
                + ", got forward=" + forward + ", reverse=" + reverse);
        JsonObject fact = new JsonObject(); fact.addProperty("case", name); fact.addProperty("expected", expected);
        fact.addProperty("forward", forward); fact.addProperty("reverse", reverse); cases.add(fact);
    }

    private static ClassNode defined(String name, JsonArray exports) throws Exception {
        Class.forName(name.replace('/', '.'), false, AutoHarvestInputAudit.class.getClassLoader());
        Path path = Path.of(".mixin.out", "class", name + ".class");
        check(Files.isRegularFile(path), "Missing actual transformed-class export " + name);
        byte[] bytes = Files.readAllBytes(path);
        JsonObject fact = new JsonObject(); fact.addProperty("class", name.replace('/', '.'));
        fact.addProperty("class_defined_without_initialization", true);
        fact.addProperty("export", path.toAbsolutePath().normalize().toString());
        fact.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); exports.add(fact);
        ClassNode type = new ClassNode(); new ClassReader(bytes).accept(type, 0); return type;
    }

    private static ClassNode resource(String name) throws Exception {
        try (var stream = AutoHarvestInputAudit.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(stream != null, "Missing compiled input controller " + name);
            ClassNode type = new ClassNode(); new ClassReader(stream.readAllBytes()).accept(type, 0); return type;
        }
    }

    private static MethodNode callingHandler(ClassNode type, String mixin, String inputMethod) {
        var methods = type.methods.stream().filter(candidate -> mergedFrom(candidate, mixin)
                && calls(candidate, INPUT, inputMethod) > 0).toList();
        check(methods.size() == 1, "Exactly one actual merged handler calls " + inputMethod); return methods.getFirst();
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

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned actual method " + type.name + "." + name + descriptor));
    }

    private static long calls(MethodNode method, String owner, String name) {
        long count = 0; for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++; return count;
    }

    private static long callsNamed(MethodNode method, String name) {
        long count = 0; for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) count++; return count;
    }

    private static boolean hasInteger(MethodNode method, int expected) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode constant && constant.cst instanceof Integer value && value == expected) return true;
            if (instruction instanceof IntInsnNode constant && constant.operand == expected) return true;
            if (expected >= -1 && expected <= 5 && instruction.getOpcode() == Opcodes.ICONST_0 + expected) return true;
        }
        return false;
    }

    private static boolean callsOwner(MethodNode method, String owner) {
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner)) return true;
        return false;
    }

    private static int indexOfCall(MethodNode method, String owner, String name) {
        for (int index = 0; index < method.instructions.size(); index++)
            if (method.instructions.get(index) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return index;
        throw new IllegalStateException("Missing actual call " + method.name + " -> " + owner + "." + name);
    }

    private static int lastIndexOfCall(MethodNode method, String owner, String name) {
        for (int index = method.instructions.size() - 1; index >= 0; index--)
            if (method.instructions.get(index) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return index;
        throw new IllegalStateException("Missing actual call " + method.name + " -> " + owner + "." + name);
    }

    private static boolean field(MethodNode method, String owner, String name, int opcode) {
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.owner.equals(owner) && field.name.equals(name) && field.getOpcode() == opcode) return true;
        return false;
    }

    private static boolean hasOpcode(MethodNode method, int opcode) {
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() == opcode) return true; return false;
    }

    private static boolean reachable(ClassNode type, MethodNode start, MethodNode target) {
        Set<MethodNode> seen = new HashSet<>(); ArrayDeque<MethodNode> pending = new ArrayDeque<>(); pending.add(start);
        while (!pending.isEmpty()) {
            MethodNode current = pending.removeFirst(); if (!seen.add(current)) continue; if (current == target) return true;
            for (AbstractInsnNode instruction : current.instructions) {
                if (instruction instanceof MethodInsnNode call) enqueue(type, pending, call.owner, call.name, call.desc);
                if (instruction instanceof InvokeDynamicInsnNode dynamic) for (Object argument : dynamic.bsmArgs)
                    if (argument instanceof Handle handle) enqueue(type, pending, handle.getOwner(), handle.getName(), handle.getDesc());
            }
        }
        return false;
    }

    private static void enqueue(ClassNode type, ArrayDeque<MethodNode> pending, String owner, String name, String descriptor) {
        if (owner.equals(type.name)) type.methods.stream().filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor)).forEach(pending::addLast);
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private void write(JsonObject report) throws Exception {
        Path absolute = output.toAbsolutePath().normalize(); Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
