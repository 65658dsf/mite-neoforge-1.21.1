package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.DifficultyCreationUi;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.locale.Language;
import net.minecraft.world.Difficulty;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Real transformed creation controls and native language parser, without Minecraft, worlds or GL. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DifficultyClientAudit implements DataProvider {
    private static final String MIXINS = "io.github.ningmeng123.mite.mixin.client.";
    private static final String STATE = "net/minecraft/client/gui/screens/worldselection/WorldCreationUiState";
    private static final String CYCLE = "net/minecraft/client/gui/components/CycleButton";
    private final Path output;

    private DifficultyClientAudit(Path output) { this.output = output; }

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.difficultyClientAudit")) return;
        event.getGenerator().addProvider(true, new DifficultyClientAudit(Path.of(System.getProperty(
                "mite.difficultyClientAuditOutput", "build/verification/difficulty-client-audit.json"))));
    }

    @Override public String getName() { return "R196 Extreme creation and options client audit without rendering"; }

    @Override
    public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("visual_appearance_passed", false);
        JsonArray targets = new JsonArray(); report.add("targets", targets);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "Enable actual transformed-class export");
            ClassNode state = transformed(STATE, targets);
            MethodNode initial = merged(state, "WorldCreationDifficultyMixin", "mite$initialExtremeDifficulty");
            MethodNode setter = merged(state, "WorldCreationDifficultyMixin", "mite$fixedExtremeDifficulty");
            requireHardReturn(initial); requireHardReturn(setter);
            check(state.methods.stream().filter(m -> m.name.equals("<init>")).anyMatch(m -> calls(m, state.name, initial.name) == 1),
                    "The real constructor converts the default difficulty exactly once");
            check(calls(method(state, "setDifficulty"), state.name, setter.name) == 1,
                    "The real setter normalizes recreated and requested difficulties");
            check(state.methods.stream().filter(m -> m.name.equals("getDifficulty") || m.name.equals("setGameMode")
                    || m.name.equals("isHardcore")).noneMatch(m -> calls(m, state.name, initial.name) != 0
                            || calls(m, state.name, setter.name) != 0),
                    "Native game mode, hardcore and getter semantics have no injected difficulty side effects");
            report.addProperty("new_and_recreated_world_difficulty_fixed", true);

            ClassNode tab = transformed("net/minecraft/client/gui/screens/worldselection/CreateWorldScreen$GameTab", targets);
            MethodNode button = merged(tab, "CreateWorldDifficultyButtonMixin", "mite$keepDifficultyFixed");
            long wrapped = tab.methods.stream().mapToLong(m -> calls(m, tab.name, button.name)).sum();
            check(wrapped == 3, "All three native button state listeners pass through the typed policy");
            check(calls(button, "io/github/ningmeng123/mite/client/DifficultyCreationUi", "active") == 1,
                    "The transformed listener uses the actual tested button policy");
            check(calls(button, CYCLE, "getValue") == 1, "The policy receives the actual current button value");
            MethodNode tabConstructor = method(tab, "<init>");
            for (String mode : List.of("SURVIVAL", "HARDCORE", "CREATIVE")) check(hasField(tabConstructor,
                    STATE + "$SelectedGameMode", mode, Opcodes.GETSTATIC), "Modern game mode remains available: " + mode);
            report.addProperty("wrapped_native_button_state_writes", wrapped);
            report.addProperty("button_policy_cases_passed", exerciseButtonPolicy());

            // Options already consume the server-synchronized native difficulty lock; no second UI state is needed.
            ClassNode options = resourceClass("net/minecraft/client/gui/screens/options/OptionsScreen");
            MethodNode online = method(options, "createOnlineButton");
            check(calls(online, "net/minecraft/client/multiplayer/ClientLevel$ClientLevelData", "isDifficultyLocked") == 1,
                    "Native in-world options read the synchronized lock state");
            check(calls(online, "net/minecraft/client/gui/components/LockIconButton", "setLocked") == 1,
                    "Native options show the actual lock icon");
            check(hasField(online, CYCLE, "active", Opcodes.PUTFIELD), "Native options disable the difficulty cycle button");
            report.addProperty("in_world_options_use_native_server_lock", true);

            JsonArray languages = new JsonArray();
            for (var locale : Map.of("en_us", "Extreme", "zh_cn", "极难").entrySet()) {
                String path = "assets/minecraft/lang/" + locale.getKey() + ".json";
                String locked = locale.getKey().equals("zh_cn") ? "难度固定为极难，无法更改。"
                        : "difficulty is fixed to Extreme and cannot be changed.";
                Map<String, String> expected = Map.of("options.difficulty.hard", locale.getValue(),
                        "commands.mite.difficulty.locked", locked);
                var candidates = getClass().getClassLoader().getResources(path);
                int matchingOverrides = 0;
                while (candidates.hasMoreElements()) {
                    var url = candidates.nextElement();
                    Map<String, String> translations = new LinkedHashMap<>();
                    try (var input = url.openStream()) { Language.loadFromJson(input, translations::put); }
                    if (translations.equals(expected)) matchingOverrides++;
                }
                check(matchingOverrides == 1, "Exactly one minimal native-language override exists for " + locale.getKey());
                JsonObject language = new JsonObject(); language.addProperty("locale", locale.getKey());
                language.addProperty("hard_display_name", locale.getValue()); language.addProperty("other_vanilla_keys_overridden", 0);
                language.addProperty("fixed_difficulty_command_message", locked);
                languages.add(language);
            }
            report.add("languages", languages);
            report.addProperty("status", "passed_transformed_client_audit_without_rendering");
            write(report); return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed"); report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static int exerciseButtonPolicy() {
        int cases = 0;
        for (Difficulty difficulty : Difficulty.values()) for (boolean requested : List.of(false, true)) {
            check(!DifficultyCreationUi.active(difficulty, requested), "Every difficulty selector remains disabled"); cases++;
        }
        for (var mode : List.of(WorldCreationUiState.SelectedGameMode.SURVIVAL,
                WorldCreationUiState.SelectedGameMode.HARDCORE, WorldCreationUiState.SelectedGameMode.CREATIVE))
            for (boolean requested : List.of(false, true)) {
                check(DifficultyCreationUi.active(mode, requested) == requested, "Game mode availability stays native"); cases++;
            }
        for (boolean value : List.of(false, true)) for (boolean requested : List.of(false, true)) {
            check(DifficultyCreationUi.active(value, requested) == requested, "Allow commands availability stays native"); cases++;
        }
        return cases;
    }

    private static ClassNode transformed(String name, JsonArray targets) throws Exception {
        Class.forName(name.replace('/', '.'), false, DifficultyClientAudit.class.getClassLoader());
        Path path = Path.of(".mixin.out", "class", name + ".class");
        check(Files.isRegularFile(path), "Actual transformed target was exported: " + name);
        JsonObject target = new JsonObject(); target.addProperty("class", name); target.addProperty("initialized", false); targets.add(target);
        return read(Files.readAllBytes(path));
    }

    private static ClassNode resourceClass(String name) throws Exception {
        try (var stream = DifficultyClientAudit.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(stream != null, "Native class resource exists: " + name); return read(stream.readAllBytes());
        }
    }

    private static ClassNode read(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static MethodNode merged(ClassNode node, String mixin, String originalName) {
        var methods = node.methods.stream().filter(m -> m.name.contains(originalName)
                && (merged(m.visibleAnnotations, MIXINS + mixin) || merged(m.invisibleAnnotations, MIXINS + mixin))).toList();
        check(methods.size() == 1, "Exactly one actual merged handler exists: " + originalName); return methods.getFirst();
    }
    private static boolean merged(List<AnnotationNode> annotations, String mixin) {
        if (annotations == null) return false;
        for (var annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                && annotation.values != null) for (int index = 0; index + 1 < annotation.values.size(); index += 2)
            if (annotation.values.get(index).equals("mixin") && annotation.values.get(index + 1).equals(mixin)) return true;
        return false;
    }
    private static void requireHardReturn(MethodNode method) {
        check(hasField(method, "net/minecraft/world/Difficulty", "HARD", Opcodes.GETSTATIC), "The real handler returns HARD");
        check(method.instructions.size() > 0, "The real normalization handler is not empty");
    }
    private static boolean hasField(MethodNode method, String owner, String name, int opcode) {
        for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode field
                && field.getOpcode() == opcode && field.owner.equals(owner) && field.name.equals(name)) return true;
        return false;
    }
    private static long calls(MethodNode method, String owner, String name) {
        long count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void write(JsonObject report) throws Exception {
        Path absolute = output.toAbsolutePath().normalize(); Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
