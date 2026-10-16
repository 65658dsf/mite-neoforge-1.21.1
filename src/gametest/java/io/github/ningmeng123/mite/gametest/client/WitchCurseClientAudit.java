package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.curse.R196Curse;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.locale.Language;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** CPU-only transformed HUD, sprint eligibility and source-resource audit; never renders. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class WitchCurseClientAudit implements DataProvider {
    private static final String ROOT = "io/github/ningmeng123/mite/";
    private static final String MIXINS = "io.github.ningmeng123.mite.mixin.client.";
    private static final String GUI = "net/minecraft/client/gui/Gui";
    private static final String ICON = "assets/mite/textures/gui/curse_source_icons.png";
    private static final String ICON_SHA256 = "6b7e6ef88ec29febb65552b3855d253076bfdeef241b9d1527a5d674aff10d4c";
    private final Path output;

    private WitchCurseClientAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.witchCurseAudit")) return;
        event.getGenerator().addProvider(true, new WitchCurseClientAudit(Path.of(System.getProperty(
                "mite.witchCurseAuditOutput", "build/verification/witch-curse-client-audit.json"))));
    }

    @Override public String getName() { return "R196 witch curse transformed client and original icon audit without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("visual_appearance_passed", false);
        report.addProperty("target_initialization_requested", false);
        JsonArray targets = new JsonArray(); report.add("targets", targets);
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "Actual transformed-class export is required");
            ClassNode gui = transformed(GUI, targets);
            MethodNode fancy = merged(gui, "CurseGuiMixin", "mite$alwaysDrawCurseVignette");
            MethodNode dark = merged(gui, "CurseGuiMixin", "mite$darkestCurseVignette");
            MethodNode cursed = merged(gui, "CurseGuiMixin", "mite$cursed");
            MethodNode camera = method(gui, "renderCameraOverlays");
            check(calls(camera, gui.name, fancy.name) == 1, "Native camera overlay consults the curse fancy-graphics override once");
            check(calls(camera, "net/minecraft/client/Minecraft", "useFancyGraphics") == 1,
                    "Native graphics choice remains available to uncursed players");
            check(calls(fancy, gui.name, cursed.name) == 1 && calls(dark, gui.name, cursed.name) == 1,
                    "Both visual overrides consult the same actual curse-state gate");
            check(calls(cursed, ROOT + "curse/PlayerCurses", "state") == 1
                    && calls(cursed, ROOT + "curse/CurseState", "active") == 1,
                    "Visual gate reads synchronized active state without revealing the hidden effect");

            MethodNode vignette = method(gui, "renderVignette");
            check(calls(vignette, gui.name, dark.name) == 1, "Native vignette color receives the maximum-darkness override exactly once");
            check(opcodes(dark).contains(Opcodes.FCONST_1), "Cursed handler returns float one, not the inverse zero");
            auditVignetteDirection(gui, vignette, report);
            report.addProperty("fast_and_fancy_graphics_use_native_curse_vignette", true);

            MethodNode air = method(gui, "renderAirLevel");
            MethodNode breath = merged(gui, "CurseGuiMixin", "mite$hideLandBreathCurseBubbles");
            check(calls(air, gui.name, breath.name) == 1, "Actual air HUD wraps exactly its native air-reserve read");
            check(calls(breath, "net/minecraft/world/entity/player/Player", "isEyeInFluid") == 1
                    && field(breath, "net/minecraft/tags/FluidTags", "WATER", Opcodes.GETSTATIC),
                    "Air HUD uses actual eye submersion in water, matching source avj");
            check(field(breath, ROOT + "curse/R196Curse", "CANNOT_HOLD_BREATH", Opcodes.GETSTATIC)
                    && calls(breath, ROOT + "curse/PlayerCurses", "has") == 1,
                    "Only the breath curse receives the land-only HUD adaptation");
            check(calls(breath, "net/minecraft/world/entity/player/Player", "getMaxAirSupply") == 1
                    && calls(breath, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call") == 1,
                    "Land-cursed HUD can return full display air while water and uncursed cases retain the original operation");
            check(calls(breath, "net/minecraft/world/entity/player/Player", "setAirSupply") == 0
                    && calls(breath, "net/minecraft/world/entity/Entity", "setAirSupply") == 0
                    && calls(breath, ROOT + "curse/PlayerCurses", "reveal") == 0,
                    "Air HUD neither changes the actual ninety-tick reserve nor reveals an unknown curse");
            check(gui.methods.stream().mapToLong(m -> calls(m, "net/minecraft/world/entity/player/Player", "getAirSupply")).sum() == 1,
                    "One native air-reserve read remains in the transformed original-operation implementation");
            check(calls(air, "net/minecraft/world/entity/player/Player", "getMaxAirSupply") == 1
                    && calls(air, "net/minecraft/world/entity/player/Player", "isEyeInFluid") == 1,
                    "Native maximum-air comparison and underwater bubble rendering remain intact");
            report.addProperty("breath_curse_land_bubbles_hidden_without_changing_real_air", true);
            report.addProperty("breath_curse_underwater_air_hud_uses_original_reserve", true);

            ClassNode local = transformed("net/minecraft/client/player/LocalPlayer", targets);
            MethodNode eligibility = method(local, "hasEnoughFoodToStartSprinting");
            MethodNode sprint = merged(local, "LocalPlayerFoodMixin", "mite$sprintWhileEnergyRemains");
            check(calls(eligibility, local.name, sprint.name) == 1, "The actual local sprint eligibility has the existing food override");
            check(calls(sprint, ROOT + "curse/PlayerCurses", "has") == 0,
                    "Ordinary forward-walk eligibility polling cannot prematurely reveal cannot-run");
            check(calls(sprint, ROOT + "survival/FoodConsumption", "hasEnergy") == 1,
                    "Uncursed low-food sprint policy remains wired to source energy");
            check(calls(sprint, "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", "setReturnValue") == 1,
                    "The actual injected result is returned through the cancellable native entrypoint");
            ClassNode living = transformed("net/minecraft/world/entity/LivingEntity", targets);
            MethodNode toggle = method(living, "setSprinting");
            MethodNode curseSprint = merged(living, "io.github.ningmeng123.mite.mixin.CurseLivingEntityMixin", "mite$cannotRun");
            check(calls(toggle, living.name, curseSprint.name) == 1,
                    "Actual inherited sprint toggle applies the shared curse guard once");
            check(calls(curseSprint, ROOT + "curse/PlayerCurses", "has") == 1
                    && field(curseSprint, ROOT + "curse/R196Curse", "CANNOT_RUN", Opcodes.GETSTATIC),
                    "Only the attempted toggle checks and reveals cannot-run on client and server");
            report.addProperty("ordinary_forward_walking_does_not_reveal_cannot_run", true);
            report.addProperty("actual_sprint_toggle_checks_and_reveals_curse_on_both_sides", true);

            // Read the production overlay class without initializing Minecraft or invoking any event/render method.
            ClassNode overlay = resourceClass(ROOT + "client/CurseStatusOverlay");
            MethodNode inventory = method(overlay, "inventory");
            for (String accessor : List.of("active", "known", "type"))
                check(calls(inventory, ROOT + "curse/CurseState", accessor) > 0, "Inventory uses actual synchronized " + accessor);
            check(strings(inventory).contains("curse.mite.unknown"), "Unknown curses have an explicit hidden-effect label");
            check(calls(inventory, ROOT + "curse/R196Curse", "title") > 0
                    && calls(inventory, ROOT + "curse/R196Curse", "description") > 0,
                    "Learned curses show their source name and description");
            check(overlay.methods.stream().allMatch(m -> calls(m, ROOT + "curse/PlayerCurses", "reveal") == 0
                    && calls(m, ROOT + "curse/PlayerCurses", "has") == 0),
                    "Viewing or hovering inventory cannot discover a hidden curse");
            MethodNode notice = method(overlay, "notification");
            check(calls(notice, ROOT + "curse/PlayerCurses", "notificationTicks") == 1
                    && calls(notice, ROOT + "curse/PlayerCurses", "notificationActive") == 1,
                    "HUD notification consumes the dedicated server notification rather than login/state changes");
            check(strings(notice).containsAll(List.of("curse.mite.notify", "curse.mite.lifted")),
                    "Both realization and lifting notices are reachable");
            report.addProperty("inventory_read_only_unknown_and_known_states_wired", true);
            report.addProperty("dedicated_realization_and_lifting_notification_wired", true);

            auditLanguages(report);
            auditIcon(report);
            report.addProperty("status", "passed_transformed_bytecode_resources_and_cpu_blend_math_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void auditVignetteDirection(ClassNode gui, MethodNode vignette, JsonObject report) throws Exception {
        check(calls(vignette, "com/mojang/blaze3d/systems/RenderSystem", "blendFuncSeparate") == 1,
                "Native vignette uses its pinned separate blend function");
        check(field(vignette, "com/mojang/blaze3d/platform/GlStateManager$SourceFactor", "ZERO", Opcodes.GETSTATIC)
                && field(vignette, "com/mojang/blaze3d/platform/GlStateManager$DestFactor", "ONE_MINUS_SRC_COLOR", Opcodes.GETSTATIC),
                "Native RGB equation is destination * (one minus source color)");
        boolean grayColor = false;
        for (AbstractInsnNode instruction : vignette.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals("net/minecraft/client/gui/GuiGraphics")
                    || !call.name.equals("setColor") || !call.desc.equals("(FFFF)V")) continue;
            var alpha = previousReal(instruction);
            var blue = previousReal(alpha);
            var green = previousReal(blue);
            var red = previousReal(green);
            if (alpha != null && alpha.getOpcode() == Opcodes.FCONST_1
                    && red instanceof VarInsnNode r && green instanceof VarInsnNode g && blue instanceof VarInsnNode b
                    && r.getOpcode() == Opcodes.FLOAD && g.getOpcode() == Opcodes.FLOAD && b.getOpcode() == Opcodes.FLOAD
                    && r.var == g.var && r.var == b.var) grayColor = true;
        }
        check(grayColor, "Native unbordered vignette multiplies all three texture color channels by the same brightness float");
        MethodNode update = method(gui, "updateVignetteBrightness");
        check(calls(update, "net/minecraft/client/renderer/LightTexture", "getBrightness") == 1,
                "Native vignette brightness is derived from actual world light");
        boolean inverseLight = false;
        for (AbstractInsnNode instruction : update.instructions) if (instruction.getOpcode() == Opcodes.FSUB) {
            var light = previousReal(instruction);
            var one = previousReal(light);
            if (light != null && light.getOpcode() == Opcodes.FLOAD && one != null && one.getOpcode() == Opcodes.FCONST_1)
                inverseLight = true;
        }
        check(inverseLight, "Native darkness target is one minus light, rather than light itself");

        BufferedImage texture;
        try (var input = WitchCurseClientAudit.class.getClassLoader().getResourceAsStream("assets/minecraft/textures/misc/vignette.png")) {
            check(input != null, "Pinned native vignette resource exists for CPU-only decoding");
            texture = ImageIO.read(input);
        }
        check(texture != null, "Native PNG decodes without graphics initialization");
        float maximum = 0F;
        for (int y = 0; y < texture.getHeight(); y++) for (int x = 0; x < texture.getWidth(); x++) {
            int pixel = texture.getRGB(x, y);
            for (int shift : new int[]{0, 8, 16}) {
                float sample = ((pixel >>> shift) & 255) / 255F;
                maximum = Math.max(maximum, sample);
                float atZero = 1F - sample * 0F, atHalf = 1F - sample * .5F, atOne = 1F - sample;
                check(atZero >= atHalf && atHalf >= atOne, "Increasing native brightness never brightens the destination at any pixel");
            }
        }
        check(maximum > 0F, "Native texture contains darkening color, so one is strictly darker than zero");
        JsonObject fact = new JsonObject();
        fact.addProperty("native_blend_equation", "out.rgb = dst.rgb * (1 - vignetteTexture.rgb * f2)");
        fact.addProperty("native_darkness_target", "clamp(1 - LightTexture.getBrightness(...), 0, 1)");
        fact.addProperty("cursed_f2", 1F);
        fact.addProperty("zero_would_disable_darkening", true);
        fact.addProperty("one_is_maximum_darkness", true);
        fact.addProperty("native_texture_width", texture.getWidth());
        fact.addProperty("native_texture_height", texture.getHeight());
        fact.addProperty("all_native_texture_channels_checked_on_cpu", true);
        fact.addProperty("largest_native_channel", maximum);
        report.add("vignette_direction", fact);
    }

    private static void auditLanguages(JsonObject report) throws Exception {
        JsonArray languages = new JsonArray();
        for (String locale : List.of("en_us", "zh_cn")) {
            String path = "assets/mite/lang/" + locale + ".json";
            Map<String, String> translations = new LinkedHashMap<>();
            try (var input = WitchCurseClientAudit.class.getClassLoader().getResourceAsStream(path)) {
                check(input != null, "Language resource exists: " + locale);
                Language.loadFromJson(input, translations::put);
            }
            JsonObject checked = new JsonObject();
            for (R196Curse curse : R196Curse.values()) for (String suffix : List.of("name", "desc")) {
                String key = "curse.mite." + curse.key() + "." + suffix;
                String value = translations.get(key);
                check(value != null && !value.isBlank() && !value.equals(key), "Translated source curse key " + locale + ": " + key);
                checked.addProperty(key, value);
            }
            check(checked.size() == 32, "Sixteen source curse names and sixteen descriptions are localized");
            for (String status : List.of("cursed", "unknown", "notify", "lifted")) {
                String key = "curse.mite." + status;
                String value = translations.get(key);
                check(value != null && !value.isBlank() && !value.equals(key), "Translated curse status " + locale + ": " + key);
                checked.addProperty(key, value);
            }
            JsonObject language = new JsonObject();
            language.addProperty("locale", locale); language.addProperty("source_name_and_description_keys", 32);
            language.addProperty("status_keys", 4); language.add("translations", checked);
            languages.add(language);
        }
        report.add("languages", languages);
    }

    private static void auditIcon(JsonObject report) throws Exception {
        byte[] bytes;
        try (var input = WitchCurseClientAudit.class.getClassLoader().getResourceAsStream(ICON)) {
            check(input != null, "Source curse icon atlas exists"); bytes = input.readAllBytes();
        }
        check(bytes.length > 33 && ByteBuffer.wrap(bytes).getLong() == 0x89504e470d0a1a0aL, "Original icon has valid PNG signature");
        check(ByteBuffer.wrap(bytes, 16, 4).getInt() == 256 && ByteBuffer.wrap(bytes, 20, 4).getInt() == 256,
                "Original 256-by-256 atlas keeps its source UV mapping");
        String hash = sha256(bytes);
        check(hash.equals(ICON_SHA256), "Source MITE_icons.png copied unchanged, independently pinned SHA256");
        JsonObject icon = new JsonObject(); icon.addProperty("resource", ICON); icon.addProperty("sha256", hash);
        icon.addProperty("source", "R196 assets/minecraft/textures/gui/MITE_icons.png");
        icon.addProperty("original_bytes_unchanged", true); icon.addProperty("width", 256); icon.addProperty("height", 256);
        report.add("source_icon", icon);
    }

    private static ClassNode transformed(String name, JsonArray targets) throws Exception {
        Class.forName(name.replace('/', '.'), false, WitchCurseClientAudit.class.getClassLoader());
        Path path = Path.of(".mixin.out", "class", name + ".class");
        check(Files.isRegularFile(path), "Actual transformed target was exported: " + name);
        byte[] bytes = Files.readAllBytes(path);
        JsonObject target = new JsonObject(); target.addProperty("class", name); target.addProperty("initialized", false);
        target.addProperty("export", path.toAbsolutePath().normalize().toString()); target.addProperty("sha256", sha256(bytes)); targets.add(target);
        return read(bytes);
    }
    private static ClassNode resourceClass(String name) throws Exception {
        try (var input = WitchCurseClientAudit.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(input != null, "Production class resource exists: " + name); return read(input.readAllBytes());
        }
    }
    private static ClassNode read(byte[] bytes) { ClassNode type = new ClassNode(); new ClassReader(bytes).accept(type, 0); return type; }
    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(m -> m.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing actual method " + type.name + "." + name));
    }
    private static MethodNode merged(ClassNode type, String mixin, String originalName) {
        String className = mixin.contains(".") ? mixin : MIXINS + mixin;
        var matches = type.methods.stream().filter(m -> m.name.contains(originalName)
                && (mergedFrom(m.visibleAnnotations, className) || mergedFrom(m.invisibleAnnotations, className))).toList();
        check(matches.size() == 1, "Exactly one actual merged handler exists: " + mixin + "." + originalName);
        return matches.getFirst();
    }
    private static boolean mergedFrom(List<AnnotationNode> annotations, String mixin) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                && annotation.values != null) for (int i = 0; i + 1 < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals(mixin)) return true;
        return false;
    }
    private static long calls(MethodNode method, String owner, String name) {
        long total = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) total++;
        return total;
    }
    private static boolean field(MethodNode method, String owner, String name, int opcode) {
        for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode field
                && field.owner.equals(owner) && field.name.equals(name) && field.getOpcode() == opcode) return true;
        return false;
    }
    private static List<String> strings(MethodNode method) {
        return java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(i -> i instanceof LdcInsnNode ldc && ldc.cst instanceof String)
                .map(i -> (String)((LdcInsnNode)i).cst).toList();
    }
    private static List<Integer> opcodes(MethodNode method) {
        return java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false).map(AbstractInsnNode::getOpcode).toList();
    }
    private static AbstractInsnNode previousReal(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        var previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void write(JsonObject report) throws Exception {
        Path target = output.toAbsolutePath().normalize(); Files.createDirectories(target.getParent());
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
