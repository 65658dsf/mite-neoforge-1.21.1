package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.BoatWaterMaskCompatibility;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** CPU vertex and transformed-call audit; does not establish visual appearance. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BoatWaterMaskClientAudit implements DataProvider {
    private static final String RENDERER = "net/minecraft/client/renderer/entity/BoatRenderer";
    private static final String PATCH = "net/minecraft/client/model/WaterPatchModel";
    private static final String PART = "Lnet/minecraft/client/model/geom/ModelPart;";
    private static final String MIXIN = "io.github.ningmeng123.mite.mixin.client.BoatWaterMaskMixin";
    private final Path output;

    private BoatWaterMaskClientAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.boatWaterMaskAudit")) return;
        Path output = Path.of(System.getProperty("mite.boatWaterMaskAuditOutput", "boat-water-mask-client-audit.json"));
        event.getGenerator().addProvider(true, new BoatWaterMaskClientAudit(output));
    }

    @Override public String getName() { return "Boat water mask CPU geometry and transformed renderer audit"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_game_ui_used", false);
        report.addProperty("visual_appearance_passed", false);
        try {
            auditRenderer(report);
            auditVertices(report);
            auditPack(report);
            report.addProperty("status", "passed_cpu_geometry_and_transformed_bytecode_only");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void auditVertices(JsonObject report) {
        ModelPart vanilla = BoatModel.createBodyModel().bakeRoot().getChild("water_patch");
        MeshDefinition mesh = new MeshDefinition();
        var patch = mesh.getRoot().addOrReplaceChild("water_patch", CubeListBuilder.create(), PartPose.ZERO);
        patch.addOrReplaceChild("oversized_depth_volume", CubeListBuilder.create().addBox(-14, -9, -8, 28, 16, 12),
                PartPose.offsetAndRotation(0, -3, 1, (float) (Math.PI / 2), 0, 0));
        ModelPart custom = LayerDefinition.create(mesh, 128, 64).bakeRoot().getChild("water_patch");
        check(BoatWaterMaskCompatibility.select(vanilla, false) == vanilla, "Empty vanilla boat retains its own mask");
        check(BoatWaterMaskCompatibility.select(vanilla, true) == vanilla, "Occupied vanilla boat retains its own mask");
        check(BoatWaterMaskCompatibility.select(custom, false) == custom, "A custom mask without a player is preserved");
        ModelPart selected = BoatWaterMaskCompatibility.select(custom, true);
        check(selected != custom, "A player excludes the custom hierarchical mask");
        check(selected.getAllParts().count() == 1, "The fallback is independent of the custom hierarchy");
        JsonArray samples = new JsonArray();
        for (int yaw : new int[] {0, 45, 180}) {
            List<Point> nativeVertices = capture(vanilla, yaw);
            List<Point> selectedVertices = capture(selected, yaw);
            List<Point> customVertices = capture(custom, yaw);
            check(nativeVertices.size() == 24, "The native mask emits six actual ModelPart quads");
            check(selectedVertices.equals(nativeVertices), "Fallback emitted vertices match native geometry at yaw " + yaw);
            check(!customVertices.equals(nativeVertices), "The custom fixture is geometrically different at yaw " + yaw);
            check(maxY(customVertices) > maxY(nativeVertices) + .2F, "The oversized fixture would occlude above the native mask");
            JsonObject sample = new JsonObject();
            sample.addProperty("boat_yaw", yaw);
            sample.addProperty("actual_model_part_vertices", selectedVertices.size());
            sample.addProperty("fallback_matches_native_point_for_point", true);
            sample.addProperty("custom_mask_max_y", maxY(customVertices));
            sample.addProperty("native_mask_max_y", maxY(nativeVertices));
            samples.add(sample);
        }
        report.add("cpu_vertex_samples", samples);
        report.addProperty("custom_fixture", "synthetic oversized hierarchical depth volume; not an EMF resource load");
        report.addProperty("vanilla_and_empty_boat_identity_preserved", true);
    }

    private static List<Point> capture(ModelPart part, int yaw) {
        PoseStack pose = new PoseStack();
        pose.translate(0, .375F, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.scale(-1, -1, 1);
        pose.mulPose(Axis.YP.rotationDegrees(90));
        Capture capture = new Capture();
        part.render(pose, capture, 0, 0);
        return capture.vertices;
    }

    private static float maxY(List<Point> vertices) {
        return (float) vertices.stream().mapToDouble(Point::y).max().orElseThrow();
    }

    private static void auditRenderer(JsonObject report) throws Exception {
        check(Boolean.getBoolean("mixin.debug.export"), "Enable actual transformed-class export");
        Class.forName(RENDERER.replace('/', '.'), false, BoatWaterMaskClientAudit.class.getClassLoader());
        Path export = Path.of(".mixin.out", "class", RENDERER + ".class");
        check(Files.isRegularFile(export), "Missing actual transformed BoatRenderer export");
        byte[] bytes = Files.readAllBytes(export);
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        MethodNode render = type.methods.stream().filter(method -> method.name.equals("render") && method.desc.equals(
                "(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
                .findFirst().orElseThrow();
        List<MethodNode> handlers = type.methods.stream().filter(method -> method.name.contains("protectBoatPassenger")
                && mergedFromMixin(method)).toList();
        check(handlers.size() == 1, "Exactly one BoatWaterMaskMixin operation wrapper is merged");
        MethodNode handler = handlers.getFirst();
        check(calls(render, type.name, handler.name, handler.desc) == 1, "The real boat render calls the wrapper exactly once");
        check(calls(handler, "io/github/ningmeng123/mite/client/BoatWaterMaskCompatibility", "select", "(" + PART + "Z)" + PART) == 1,
                "The wrapper invokes the real selection helper exactly once");
        check(type.methods.stream().mapToInt(method -> calls(method, PATCH, "waterPatch", "()" + PART)).sum() == 1,
                "Exactly one original WaterPatchModel.waterPatch operation remains behind the wrapper");
        check(calls(render, PATCH, "waterPatch", "()" + PART) == 0, "The native render cannot bypass the wrapper");
        JsonObject facts = new JsonObject();
        facts.addProperty("target_initialization_requested", false);
        facts.addProperty("export", export.toAbsolutePath().normalize().toString());
        facts.addProperty("sha256", sha256(bytes));
        facts.addProperty("merged_wrapper", handler.name + handler.desc);
        facts.addProperty("render_wrapper_calls", 1);
        facts.addProperty("helper_calls", 1);
        report.add("transformed_renderer", facts);
    }

    private static void auditPack(JsonObject report) throws Exception {
        String configured = System.getProperty("mite.boatWaterMaskPack");
        if (configured == null || configured.isBlank()) {
            report.addProperty("external_fa_objects_evidence", "skipped: mite.boatWaterMaskPack was not supplied");
            return;
        }
        Path file = Path.of(configured);
        check(Files.isRegularFile(file), "The supplied external FA+Objects ZIP exists");
        JsonArray assets = new JsonArray();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            for (String name : List.of("boat", "chest_boat")) {
                String path = "assets/minecraft/optifine/cem/" + name + ".jem";
                var entry = zip.getEntry(path);
                check(entry != null, "The external pack contains " + path);
                byte[] bytes;
                try (var input = zip.getInputStream(entry)) { bytes = input.readAllBytes(); }
                JsonObject model = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject mask = null;
                for (var part : model.getAsJsonArray("models")) if (part.getAsJsonObject().get("part").getAsString().equals("bottom_no_water"))
                    mask = part.getAsJsonObject();
                check(mask != null && mask.has("submodels") && mask.has("animations"), "Real FA mask is an animated hierarchy");
                JsonObject rotation = mask.getAsJsonArray("submodels").get(1).getAsJsonObject();
                JsonArray box = rotation.getAsJsonArray("submodels").get(0).getAsJsonObject()
                        .getAsJsonArray("boxes").get(0).getAsJsonObject().getAsJsonArray("coordinates");
                check(box.size() == 6 && box.get(5).getAsFloat() > 3F, "Real FA water mask is thicker than the native three-unit box");
                JsonObject asset = new JsonObject();
                asset.addProperty("path", path);
                asset.addProperty("sha256", sha256(bytes));
                asset.addProperty("mapped_vanilla_part", "water_patch");
                asset.add("custom_box", box.deepCopy());
                asset.addProperty("animated_hierarchy_present", true);
                assets.add(asset);
            }
        }
        report.addProperty("external_pack", file.toAbsolutePath().normalize().toString());
        report.add("external_fa_objects_evidence", assets);
    }

    private static boolean mergedFromMixin(MethodNode method) {
        return mergedFromMixin(method.visibleAnnotations) || mergedFromMixin(method.invisibleAnnotations);
    }

    private static boolean mergedFromMixin(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                && annotation.values != null) for (int i = 0; i + 1 < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals(MIXIN)) return true;
        return false;
    }

    private static int calls(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                && call.name.equals(name) && call.desc.equals(descriptor)) count++;
        return count;
    }

    private record Point(float x, float y, float z) {}

    private static final class Capture implements VertexConsumer {
        private final List<Point> vertices = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            check(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "ModelPart emits finite vertices");
            vertices.add(new Point(x, y, z)); return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private void write(JsonObject report) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
