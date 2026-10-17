package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.ShieldRendering;
import io.github.ningmeng123.mite.registry.MiteShields;
import java.io.InputStream;
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
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Actual client pose transforms and shield renderer registration, without a game or GL. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ShieldClientAudit implements DataProvider {
    private static final String MIXINS = "io.github.ningmeng123.mite.mixin.";
    private static final String POSES = "io/github/ningmeng123/mite/client/MonsterShieldPoses";
    private static final String RENDERING = "io/github/ningmeng123/mite/client/ShieldRendering";
    private final Path output;

    private ShieldClientAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.shieldClientAudit")) return;
        event.getGenerator().addProvider(true, new ShieldClientAudit(Path.of(System.getProperty(
                "mite.shieldClientAuditOutput", "build/verification/shield-client-audit.json"))));
    }

    @Override public String getName() { return "Transformed MITE shield client wiring without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("visual_appearance_passed", false);
        report.addProperty("scope", "actual transformed model classes, native decoration and compiled renderer registration; not rendered pixels");
        try {
            check(Boolean.getBoolean("mixin.debug.export"), "Actual Mixin export is required");
            JsonArray poses = new JsonArray();
            report.add("pose_targets", poses);
            pose("HumanoidModel", "net/minecraft/world/entity/LivingEntity", "HumanoidShieldPoseMixin", "mite$showShieldBlock", poses);
            ClassNode skeleton = pose("SkeletonModel", "net/minecraft/world/entity/Mob", "SkeletonShieldPoseMixin", "mite$showSkeletonShieldBlock", poses);
            pose("AbstractZombieModel", "net/minecraft/world/entity/monster/Monster", "ZombieShieldPoseMixin", "mite$showZombieShieldBlock", poses);
            pose("PiglinModel", "net/minecraft/world/entity/Mob", "PiglinShieldPoseMixin", "mite$showPiglinShieldBlock", poses);
            pose("IllagerModel", "net/minecraft/world/entity/monster/AbstractIllager", "IllagerShieldPoseMixin", "mite$showIllagerShieldBlock", poses);
            MethodNode animation = method(skeleton, "setupAnim", "(Lnet/minecraft/world/entity/Mob;FFFFF)V");
            MethodNode swap = merged(skeleton, "client.SkeletonWeaponPoseMixin", "mite$lowerArmsDuringWeaponSwap");
            MethodNode block = merged(skeleton, "client.SkeletonShieldPoseMixin", "mite$showSkeletonShieldBlock");
            check(callIndex(animation, skeleton.name, swap.name) < callIndex(animation, skeleton.name, block.name),
                    "The active skeleton shield stance is restored after weapon-swap arm animation");
            report.addProperty("skeleton_shield_pose_after_weapon_swap", true);

            decoration(report);
            rendering(report);
            report.addProperty("status", "passed_actual_shield_client_transforms_and_renderer_wiring_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed"); report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static ClassNode pose(String model, String entity, String mixin, String handler, JsonArray results) throws Exception {
        ClassNode type = transformed("net/minecraft/client/model/" + model);
        MethodNode animation = method(type, "setupAnim", "(L" + entity + ";FFFFF)V");
        MethodNode actual = merged(type, "client." + mixin, handler);
        check(calls(animation, type.name, actual.name) == 1, model + " invokes its actual injected shield pose exactly once");
        check(calls(actual, POSES, "apply") == 1, "The actual " + model + " handler uses the shared blocking pose");
        JsonObject fact = new JsonObject(); fact.addProperty("class", type.name.replace('/', '.'));
        fact.addProperty("handler", actual.name + actual.desc); fact.addProperty("actual_animation_calls", 1);
        fact.addProperty("shared_shield_pose_wired", true); results.add(fact);
        return type;
    }

    private static void decoration(JsonObject report) throws Exception {
        ClassNode recipe = transformed("net/minecraft/world/item/crafting/ShieldDecorationRecipe");
        MethodNode redirected = merged(recipe, "ShieldDecorationMixin", "mite$decorateAnyMetalShield");
        check(calls(redirected, "io/github/ningmeng123/mite/registry/MiteShields", "definition") == 1,
                "The actual decoration redirect recognizes the complete MITE shield registry");
        int guarded = 0;
        for (MethodNode nativeMethod : recipe.methods) if ((nativeMethod.name.equals("matches") || nativeMethod.name.equals("assemble"))
                && calls(nativeMethod, recipe.name, redirected.name) > 0) {
            check(calls(nativeMethod, recipe.name, redirected.name) == 1, "The native decoration path checks the metal shield once");
            check(calls(nativeMethod, "net/minecraft/world/item/ItemStack", "is") == 0, "No hard-coded native-only shield check remains");
            guarded++;
        }
        check(guarded == 2, "Both actual native matching and assembly use the broadened shield identity");
        report.addProperty("native_banner_matching_and_assembly_transformed", true);
    }

    private static void rendering(JsonObject report) throws Exception {
        ClassNode registration = compiled(RENDERING);
        ClassNode renderer = compiled(RENDERING + "$ShieldItemRenderer");
        ClassNode extension = compiled(RENDERING + "$1");
        check(allCalls(registration, "net/minecraft/client/renderer/item/ItemProperties", "register") == 1,
                "The client registers the native blocking model predicate for the material shields");
        check(allCalls(registration, "net/neoforged/neoforge/client/extensions/common/RegisterClientExtensionsEvent", "registerItem") == 1,
                "Custom shields are wired into the native item-rendering extension event");
        check(allCalls(registration, "net/neoforged/neoforge/client/event/RegisterClientReloadListenersEvent", "registerReloadListener") == 1,
                "The shield model cache is reset by the actual resource reload event");
        check(allCalls(extension, RENDERING, "renderer") == 1, "The item extension actually returns the shield renderer");
        boolean usingGate = false;
        for (MethodNode candidate : registration.methods) if (calls(candidate, "net/minecraft/world/entity/LivingEntity", "getUseItem") == 1
                && calls(candidate, "net/minecraft/world/entity/LivingEntity", "isUsingItem") == 1) usingGate = true;
        check(usingGate, "The blocking predicate tests the active use item rather than shield presence alone");
        check(allCalls(renderer, RENDERING, "texture") == 1, "Rendering selects the per-metal texture");
        check(allCalls(renderer, "net/minecraft/client/model/ShieldModel", "handle") >= 1
                && allCalls(renderer, "net/minecraft/client/model/ShieldModel", "plate") >= 2,
                "The native shield mesh renders its handle, plate and optional banner overlay");
        check(allCalls(renderer, "net/minecraft/client/renderer/entity/ItemRenderer", "getFoilBufferDirect") == 1,
                "The native enchantment glint is retained on the metal shield");
        check(allCalls(renderer, "net/minecraft/client/renderer/Sheets", "getShieldMaterial") == 1,
                "Banner pattern layers use the native shield pattern atlas");
        boolean nativeGeometry = false;
        for (MethodNode candidate : renderer.methods) for (var instruction : candidate.instructions)
            if (instruction instanceof FieldInsnNode field && field.owner.equals("net/minecraft/client/model/geom/ModelLayers")
                    && field.name.equals("SHIELD")) nativeGeometry = true;
        check(nativeGeometry, "The renderer bakes the native shield layer");
        MethodNode reload = method(renderer, "onResourceManagerReload", "(Lnet/minecraft/server/packs/resources/ResourceManager;)V");
        check(reload.instructions.size() > 0, "The model cache has an actual reload handler");
        JsonArray textures = new JsonArray();
        for (var definition : MiteShields.definitions()) if (definition.isNew()) {
            String expected = "mite:textures/entity/shield/" + definition.material() + ".png";
            check(ShieldRendering.texture(definition.material()).toString().equals(expected), "Each material selects its independent texture path");
            textures.add(expected);
        }
        check(textures.size() == 7, "Seven new metal shield textures are used while vanilla iron rendering remains native");
        report.add("material_texture_paths", textures);
        report.addProperty("blocking_predicate_and_custom_item_renderer_registered", true);
        report.addProperty("native_mesh_banner_pattern_and_enchantment_glint_wired", true);
        report.addProperty("resource_reload_resets_model_cache", true);
    }

    private static ClassNode transformed(String name) throws Exception {
        Class.forName(name.replace('/', '.'), false, ShieldClientAudit.class.getClassLoader());
        Path exported = Path.of(".mixin.out", "class", name + ".class");
        check(Files.isRegularFile(exported), "Actual transformed export exists: " + name);
        return read(Files.readAllBytes(exported));
    }

    private static ClassNode compiled(String name) throws Exception {
        Class.forName(name.replace('/', '.'), false, ShieldClientAudit.class.getClassLoader());
        try (InputStream stream = ShieldClientAudit.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(stream != null, "Compiled class exists: " + name);
            return read(stream.readAllBytes());
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
    }

    private static MethodNode merged(ClassNode type, String mixin, String fragment) {
        for (MethodNode candidate : type.methods) if (candidate.name.contains(fragment) && mergedBy(candidate, MIXINS + mixin)) return candidate;
        throw new IllegalStateException("Missing actual merged handler " + mixin + ":" + fragment);
    }

    private static boolean mergedBy(MethodNode method, String expected) {
        for (List<AnnotationNode> annotations : List.of(method.visibleAnnotations == null ? List.<AnnotationNode>of() : method.visibleAnnotations,
                method.invisibleAnnotations == null ? List.<AnnotationNode>of() : method.invisibleAnnotations))
            for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                    && annotation.values != null)
                for (int i = 0; i + 1 < annotation.values.size(); i += 2)
                    if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals(expected)) return true;
        return false;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing method " + type.name + ":" + name + descriptor));
    }

    private static long calls(MethodNode method, String owner, String name) {
        long count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }

    private static long allCalls(ClassNode type, String owner, String name) {
        return type.methods.stream().mapToLong(candidate -> calls(candidate, owner, name)).sum();
    }

    private static int callIndex(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) return i;
        throw new IllegalStateException("Actual native call is missing: " + owner + ":" + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void write(JsonObject report) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
