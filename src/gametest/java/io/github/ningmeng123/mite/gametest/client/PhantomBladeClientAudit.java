package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.phantom.PhantomBladeProjectileRenderer.Trail;
import io.github.ningmeng123.mite.client.phantom.PhantomBladeVisuals;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.joml.Vector3f;

/** Actual client resources and emitted ribbon vertices, without creating Minecraft, a world or GL. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PhantomBladeClientAudit implements DataProvider {
    private static final String CLIENT = "io/github/ningmeng123/mite/client/phantom/";
    private final Path output;
    private PhantomBladeClientAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.phantomBladeClientAudit")) return;
        event.getGenerator().addProvider(true, new PhantomBladeClientAudit(Path.of(System.getProperty(
                "mite.phantomBladeClientAuditOutput", "build/verification/phantom-blade-client-audit.json"))));
    }
    @Override public String getName() { return "MITE phantom blade client assets and actual mesh contract"; }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("visual_appearance_passed", false);
        report.addProperty("scope", "decoded RGBA sprites, inherited native FIXED item transforms and actual texture-frame axes, orbit/projectile orientation, actual emitted formation/aura/trail vertices, trail history and compiled renderer wiring; not rendered pixels");
        try {
            resources(report); orientations(report); meshes(report); wiring(report);
            report.addProperty("status", "passed_client_assets_actual_mesh_and_renderer_wiring_without_rendering");
            write(report); return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed"); report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void resources(JsonObject report) throws Exception {
        JsonArray assets = new JsonArray(); var hashes = new HashSet<String>();
        check(PhantomBladeVisuals.MATERIALS.size() == 9, "Nine material variants are visible");
        for (String material : PhantomBladeVisuals.MATERIALS) {
            check(MitePhantomBlades.get(material) != Items.AIR && MitePhantomBlades.sword(material) != Items.AIR,
                    "A registered formation core and its actual material sword exist: " + material);
            String texture = "mite:item/" + material + "_phantom_blade";
            var model = JsonParser.parseString(new String(bytes("assets/mite/models/item/" + material + "_phantom_blade.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            check(model.get("parent").getAsString().equals("minecraft:item/generated"), "Core uses its own flat item silhouette");
            check(model.getAsJsonObject("textures").get("layer0").getAsString().equals(texture), "Core selects its own formation texture");
            byte[] png = bytes("assets/mite/textures/item/" + material + "_phantom_blade.png");
            var image = ImageIO.read(new java.io.ByteArrayInputStream(png));
            check(image != null && image.getWidth() == 32 && image.getHeight() == 32, "32px material sprite decodes: " + material);
            int transparent = 0, translucent = 0, opaque = 0;
            for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha == 0) transparent++; else if (alpha < 255) translucent++; else opaque++;
            }
            check(transparent > 300 && translucent > 100 && opaque > 30, "Core has transparent space, spectral echoes and a readable solid center: " + material);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
            check(hashes.add(hash), "Every material has an independent colored image");
            JsonObject asset = new JsonObject(); asset.addProperty("material", material); asset.addProperty("texture", texture);
            asset.addProperty("sha256", hash); asset.addProperty("transparent_pixels", transparent);
            asset.addProperty("translucent_pixels", translucent); asset.addProperty("opaque_pixels", opaque); assets.add(asset);
        }
        try (InputStream input = resource("assets/mite/textures/entity/phantom/blade_light.png")) {
            var image = ImageIO.read(input);
            check(image != null && image.getWidth() == 16 && image.getHeight() == 16, "Soft ribbon texture decodes");
            check(image.getRGB(8, 0) >>> 24 == 0 && image.getRGB(8, 7) >>> 24 > 200, "Ribbon edges fade to transparency around its bright center");
        }
        report.add("material_assets", assets);
    }

    private record NativeModel(ItemTransform fixed, Map<String, String> textures, List<String> chain) {}
    private record BladeAxis(Vec3 handle, Vec3 tip) {}

    /** Resolve the actual parent chain, rather than assuming FIXED is identity for generated item sprites. */
    private static NativeModel nativeModel(ResourceLocation id, Set<ResourceLocation> visited) throws Exception {
        check(visited.add(id), "Native item model parents have no cycle: " + id);
        var model = JsonParser.parseString(new String(bytes("assets/" + id.getNamespace() + "/models/"
                + id.getPath() + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        NativeModel parent = new NativeModel(ItemTransform.NO_TRANSFORM, Map.of(), List.of());
        if (model.has("parent")) {
            var parentId = ResourceLocation.parse(model.get("parent").getAsString());
            if (!parentId.getPath().startsWith("builtin/")) parent = nativeModel(parentId, visited);
        }
        var textures = new HashMap<>(parent.textures());
        if (model.has("textures")) model.getAsJsonObject("textures").entrySet()
                .forEach(entry -> textures.put(entry.getKey(), entry.getValue().getAsString()));
        ItemTransform fixed = parent.fixed();
        if (model.has("display") && model.getAsJsonObject("display").has("fixed"))
            fixed = new ItemTransform.Deserializer().deserialize(model.getAsJsonObject("display").get("fixed"), ItemTransform.class, null);
        var chain = new ArrayList<String>(); chain.add(id.toString()); chain.addAll(parent.chain());
        return new NativeModel(fixed, textures, chain);
    }

    private static ResourceLocation layerTexture(NativeModel model) {
        String texture = model.textures().get("layer0"); var visited = new HashSet<String>();
        while (texture != null && texture.startsWith("#")) {
            check(visited.add(texture), "Native sword texture aliases have no cycle");
            texture = model.textures().get(texture.substring(1));
        }
        check(texture != null, "Native material sword has an actual layer0 texture");
        return ResourceLocation.parse(texture);
    }

    /** The opaque diagonal endpoints are the source sprites' lower-left grip and upper-right sword point. */
    private static BladeAxis textureAxis(java.awt.image.BufferedImage image, int frame, int size) {
        int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE, handleCount = 0, tipCount = 0;
        double handleX = 0D, handleY = 0D, tipX = 0D, tipY = 0D;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if ((image.getRGB(x, frame * size + y) >>> 24) == 0) continue;
            int projection = x - y;
            if (projection < minimum) { minimum = projection; handleCount = 0; handleX = 0D; handleY = 0D; }
            if (projection == minimum) { handleCount++; handleX += x + .5D; handleY += y + .5D; }
            if (projection > maximum) { maximum = projection; tipCount = 0; tipX = 0D; tipY = 0D; }
            if (projection == maximum) { tipCount++; tipX += x + .5D; tipY += y + .5D; }
        }
        check(handleCount > 0 && tipCount > 0 && maximum > minimum, "Each animated sword frame has distinct opaque grip and tip endpoints");
        Vec3 handle = new Vec3(handleX / handleCount / size, 1D - handleY / handleCount / size, .5D);
        Vec3 tip = new Vec3(tipX / tipCount / size, 1D - tipY / tipCount / size, .5D);
        Vec3 axis = tip.subtract(handle).normalize();
        check(axis.x > 0D && axis.y > 0D && Math.abs(axis.x - axis.y) < 1E-8D,
                "The actual source texture frame has the known lower-left grip to upper-right sword point diagonal");
        return new BladeAxis(handle, tip);
    }

    private static Vec3 transformed(PoseStack pose, Vec3 point) {
        var result = pose.last().pose().transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3(result.x(), result.y(), result.z());
    }

    private static void aligned(Vec3 actual, Vec3 expected, String message) {
        check(actual.lengthSqr() > 1E-10D && actual.normalize().dot(expected.normalize()) > .9999D, message);
    }

    private static void swordAligned(PoseStack pose, NativeModel model, BladeAxis axis, Vec3 expected, String message) {
        pose.pushPose();
        PhantomBladeVisuals.alignSwordSprite(pose);
        model.fixed().apply(false, pose); // The same native ItemTransform.apply used by FIXED item rendering.
        pose.translate(-.5F, -.5F, -.5F); // ItemRenderer centers the generated sprite after its display transform.
        aligned(transformed(pose, axis.tip()).subtract(transformed(pose, axis.handle())), expected, message);
        pose.popPose();
    }

    private static void auraAligned(PoseStack pose, Vec3 expected, String message) {
        Capture aura = new Capture(); PhantomBladeVisuals.bladeAura(pose, aura.buffers(), 0x8AE4FF, .8F); aura.finish();
        check(aura.vertices == 4, "Orientation test captures the real four-vertex aura mesh");
        Vec3 tail = aura.position.get(0).add(aura.position.get(1)).scale(.5D);
        Vec3 tip = aura.position.get(2).add(aura.position.get(3)).scale(.5D);
        aligned(tip.subtract(tail), expected, message);
    }

    private static void orientations(JsonObject report) throws Exception {
        JsonArray models = new JsonArray(); int orbitCases = 0, flightCases = 0;
        double[] times = {0D, 42.5D, 180.75D, 10_000D};
        Vec3[] directions = {new Vec3(1D, 0D, 0D), new Vec3(-1D, 0D, 0D), new Vec3(0D, 1D, 0D),
                new Vec3(0D, -1D, 0D), new Vec3(0D, 0D, 1D), new Vec3(0D, 0D, -1D), new Vec3(3D, -4D, 5D)};
        for (String material : PhantomBladeVisuals.MATERIALS) {
            var item = BuiltInRegistries.ITEM.getKey(MitePhantomBlades.sword(material));
            var model = nativeModel(ResourceLocation.fromNamespaceAndPath(item.getNamespace(), "item/" + item.getPath()), new HashSet<>());
            var texture = layerTexture(model);
            String texturePath = "assets/" + texture.getNamespace() + "/textures/" + texture.getPath() + ".png";
            var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes(texturePath)));
            check(image != null && image.getHeight() % image.getWidth() == 0, "Native sword texture is a square sprite or vertical square-frame animation: " + material);
            int size = image.getWidth(), frames = image.getHeight() / size;
            if (frames > 1) {
                var metadata = JsonParser.parseString(new String(bytes(texturePath + ".mcmeta"), StandardCharsets.UTF_8)).getAsJsonObject();
                check(metadata.has("animation") && !metadata.getAsJsonObject("animation").has("width")
                        && !metadata.getAsJsonObject("animation").has("height"), "The actual animated sword uses native default square frame dimensions");
            }
            check(model.chain().contains("minecraft:item/handheld") && model.chain().contains("minecraft:item/generated"),
                    "The native sword resolves its real handheld to generated parent chain: " + material);
            check(Math.abs(model.fixed().rotation.y() - 180F) < .0001F,
                    "The actual inherited FIXED display transform includes its hidden180-degree Y rotation: " + material);
            for (int frame = 0; frame < frames; frame++) {
                BladeAxis axis = textureAxis(image, frame, size);
                for (double time : times) for (int index = 0; index < PhantomBladeRules.profile(material).bladeCount(); index++) {
                    var pose = new PoseStack(); PhantomBladeVisuals.orientOrbit(pose, time, index, PhantomBladeRules.profile(material).bladeCount());
                    auraAligned(pose, new Vec3(0D, 1D, 0D), "The actual orbit aura points upward at every blade index and time");
                    swordAligned(pose, model, axis, new Vec3(0D, 1D, 0D), "The native model grip-to-tip axis is vertical and points up: " + material + "/frame" + frame + "/blade" + index + "/time" + time);
                    orbitCases++;
                }
                for (Vec3 direction : directions) {
                    float yaw = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
                    float elevation = (float) Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
                    var pose = new PoseStack(); PhantomBladeVisuals.orientProjectile(pose, yaw, elevation);
                    auraAligned(pose, direction, "The actual projectile aura points along all six cardinal and the diagonal flight directions");
                    swordAligned(pose, model, axis, direction, "The native model sword point faces its actual flight direction: " + material + "/frame" + frame + "/direction" + direction);
                    flightCases++;
                }
            }
            JsonObject entry = new JsonObject(); entry.addProperty("material", material); entry.addProperty("native_item", item.toString());
            entry.addProperty("texture", texture.toString()); entry.addProperty("frame_size", size); entry.addProperty("frames_checked", frames);
            JsonArray chain = new JsonArray(); model.chain().forEach(chain::add); entry.add("model_parent_chain", chain);
            JsonArray rotation = new JsonArray(); rotation.add(model.fixed().rotation.x()); rotation.add(model.fixed().rotation.y()); rotation.add(model.fixed().rotation.z());
            entry.add("inherited_fixed_rotation", rotation); models.add(entry);
        }
        report.add("native_sword_model_orientation", models);
        report.addProperty("actual_native_orbit_grip_to_tip_orientation_cases", orbitCases);
        report.addProperty("actual_native_projectile_grip_to_tip_orientation_cases", flightCases);
        report.addProperty("actual_aura_direction_matches_native_sword_tip", true);
    }

    private static void meshes(JsonObject report) {
        JsonArray capacities = new JsonArray();
        for (var profile : PhantomBladeRules.profiles()) {
            var positions = new HashSet<Vec3>();
            int count = profile.bladeCount();
            for (int index = 0; index < count; index++) {
                Vec3 point = PhantomBladeVisuals.orbit(42.5D, index, count); positions.add(point);
                Vec3 next = PhantomBladeVisuals.orbit(42.5D, (index+1)%count, count);
                check(Math.abs(point.horizontalDistance() - PhantomBladeVisuals.ORBIT_RADIUS) < 1E-8D, "Each blade stays on the owner-centered orbit");
                double gap = Math.atan2(point.x*next.z-point.z*next.x, point.x*next.x+point.z*next.z);
                check(Math.abs(gap-2D*Math.PI/count) < 1E-8D, "Every adjacent pair, including the closing pair, has equal material-specific angular spacing");
            }
            check(positions.size() == count && !PhantomBladeVisuals.orbit(42.5D, 0, count).equals(PhantomBladeVisuals.orbit(43.5D, 0, count)),
                    "The actual material capacity forms a complete moving ring: "+profile.material());
            JsonObject capacity = new JsonObject(); capacity.addProperty("material", profile.material()); capacity.addProperty("orbit_blades", count); capacities.add(capacity);
        }
        report.add("material_orbit_capacities_and_equal_spacing", capacities);
        Capture ring = new Capture(); PhantomBladeVisuals.formationRing(new PoseStack(), ring.buffers(), 42.5D, 0x8AE4FF); ring.finish();
        check(ring.vertices == 256, "The actual formation ring emits 64 complete quads");
        Capture aura = new Capture(); PhantomBladeVisuals.bladeAura(new PoseStack(), aura.buffers(), 0x8AE4FF, .8F); aura.finish();
        check(aura.vertices == 4, "The actual native-sword aura emits one soft quad");
        Capture ribbon = new Capture();
        PhantomBladeVisuals.ribbon(new PoseStack(), ribbon.buffers(), Vec3.ZERO, new Vec3(1D, .3D, 0D), new Vec3(0D, 2D, 4D),
                .1D, .02D, 0x8AE4FF, .8F, .1F); ribbon.finish();
        check(ribbon.vertices == 4 && ribbon.alpha.getFirst() > ribbon.alpha.getLast(), "Actual ribbon vertices fade from the moving sword into history");
        check(ribbon.position.get(0).distanceTo(ribbon.position.get(1)) > ribbon.position.get(2).distanceTo(ribbon.position.get(3)),
                "Actual trail geometry narrows toward its old endpoint");
        Capture degenerate = new Capture();
        PhantomBladeVisuals.ribbon(new PoseStack(), degenerate.buffers(), Vec3.ZERO, Vec3.ZERO, new Vec3(0D, 2D, 4D), .1D, .1D, 0xFFFFFF, 1F, 1F);
        check(degenerate.vertices == 0, "Stationary duplicate samples emit no broken ribbon");
        check(PhantomBladeVisuals.trailOpacity(0D) > PhantomBladeVisuals.trailOpacity(6D)
                && PhantomBladeVisuals.trailOpacity(12D) == 0F, "Light expires within twelve ticks");
        Trail trail = new Trail();
        for (int tick = 0; tick < 20; tick++) { trail.sample(tick, new Vec3(tick * .5D, 0D, 0D)); trail.sample(tick, new Vec3(999D, 0D, 0D)); }
        check(trail.points().size() == 12 && trail.points().getFirst().position().x == 9.5D,
                "Real trajectory cache is bounded and samples only once per tick");
        trail.sample(20, new Vec3(1000D, 0D, 0D));
        check(trail.points().size() == 1, "A teleport cannot draw a world-spanning light streak");
        for (int tick = 21; tick <= 32; tick++) trail.sample(tick, new Vec3(1000D, 0D, 0D));
        check(trail.points().isEmpty(), "The real old trajectory expires even while a projectile stops moving");
        report.addProperty("actual_ring_vertices", ring.vertices); report.addProperty("actual_aura_vertices", aura.vertices);
        report.addProperty("actual_ribbon_vertices", ribbon.vertices); report.addProperty("all_new_entity_vertex_attributes_written", true);
        report.addProperty("bounded_motion_history_fades_and_rejects_teleport_streaks", true);
    }

    private static void wiring(JsonObject report) throws Exception {
        ClassNode registration = compiled(CLIENT + "PhantomBladeClientRegistration");
        check(calls(registration, "net/neoforged/neoforge/client/event/EntityRenderersEvent$RegisterRenderers", "registerEntityRenderer") == 2,
                "Both synchronized entities are wired to real client renderers");
        ClassNode swarm = compiled(CLIENT + "PhantomBladeSwarmRenderer");
        ClassNode projectile = compiled(CLIENT + "PhantomBladeProjectileRenderer");
        for (ClassNode renderer : List.of(swarm, projectile)) {
            check(calls(renderer, "net/minecraft/client/renderer/entity/ItemRenderer", "renderStatic") == 1,
                    "Actual native ItemRenderer draws the corresponding material sword: " + renderer.name);
            check(calls(renderer, "io/github/ningmeng123/mite/registry/MitePhantomBlades", "sword") == 1,
                    "Each rendered sword resolves its actual material rather than the formation core");
            String orientation = renderer == swarm ? "orientOrbit" : "orientProjectile";
            check(calls(renderer, CLIENT + "PhantomBladeVisuals", orientation) == 1
                    && calls(renderer, CLIENT + "PhantomBladeVisuals", "alignSwordSprite") == 1,
                    "The real renderer uses both the verified world-facing helper and native sprite alignment: " + renderer.name);
            check(calls(renderer, "com/mojang/blaze3d/vertex/PoseStack", "mulPose") == 0,
                    "No old renderer-local rotation overrides the audited orientation helpers: " + renderer.name);
            var draw = renderer.methods.stream().filter(method -> method.name.equals("render")
                    && method.desc.contains(renderer == swarm ? "Lio/github/ningmeng123/mite/phantom/PhantomBladeSwarm;"
                            : "Lio/github/ningmeng123/mite/phantom/PhantomBladeProjectile;")).findFirst().orElseThrow();
            int orient = -1, aura = -1, align = -1, item = -1, index = 0;
            for (var instruction : draw.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals(CLIENT + "PhantomBladeVisuals")) {
                        if (call.name.equals(orientation)) orient = index;
                        if (call.name.equals("bladeAura")) aura = index;
                        if (call.name.equals("alignSwordSprite")) align = index;
                    }
                    if (call.owner.equals("net/minecraft/client/renderer/entity/ItemRenderer") && call.name.equals("renderStatic")) item = index;
                }
                index++;
            }
            check(orient >= 0 && orient < aura && aura < align && align < item,
                    "Actual call order is world orientation, matching aura, native sprite alignment, then FIXED item rendering: " + renderer.name);
        }
        report.addProperty("actual_renderers_use_verified_orientation_then_aura_then_native_fixed_sprite_alignment", true);
        check(calls(swarm, "io/github/ningmeng123/mite/phantom/PhantomBladeSwarm", "orbitBladeMask") == 1,
                "Launching a sword actually hides that sword from the orbit formation");
        check(calls(swarm, "io/github/ningmeng123/mite/phantom/PhantomBladeSwarm", "bladeCount") == 1,
                "The actual renderer obtains its slot count from the synchronized material's profile capacity");
        var drawSwarm = swarm.methods.stream().filter(method -> method.name.equals("render")
                && method.desc.contains("Lio/github/ningmeng123/mite/phantom/PhantomBladeSwarm;")).findFirst().orElseThrow();
        int ringCall = -1, maskCall = -1, instructionIndex = 0;
        for (var instruction : drawSwarm.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(CLIENT + "PhantomBladeVisuals") && call.name.equals("formationRing")) ringCall = instructionIndex;
                if (call.owner.equals("io/github/ningmeng123/mite/phantom/PhantomBladeSwarm") && call.name.equals("orbitBladeMask")) maskCall = instructionIndex;
            }
            instructionIndex++;
        }
        check(ringCall >= 0 && ringCall < maskCall, "The actual formation light is drawn before any blade-mask gate, so an empty eight-shot magazine keeps its ring throughout recharge");
        report.addProperty("empty_magazine_keeps_formation_light_during_recharge", true);
        check(calls(projectile, CLIENT + "PhantomBladeProjectileRenderer$Trail", "sample") == 1
                && calls(projectile, CLIENT + "PhantomBladeVisuals", "ribbon") == 1, "Flying renderer actually draws its sampled trajectory");
        ClassNode mesh = compiled(CLIENT + "PhantomBladeVisuals");
        check(calls(mesh, "net/minecraft/client/renderer/RenderType", "entityTranslucentEmissive") >= 1,
                "Soft geometry uses the native full-bright translucent emission shader");
        check(hasField(registration, "io/github/ningmeng123/mite/registry/MitePhantomBlades", "SWARM")
                && hasField(registration, "io/github/ningmeng123/mite/registry/MitePhantomBlades", "PROJECTILE"), "The registered types are the actual synchronized gameplay entities");
        report.addProperty("real_entity_registration_native_swords_masks_and_motion_trail_wired", true);
    }

    private static final class Capture implements VertexConsumer {
        int vertices, fields = 31;
        final List<Integer> alpha = new ArrayList<>(); final List<Vec3> position = new ArrayList<>();
        MultiBufferSource buffers() { return type -> {
            check(type.format() == DefaultVertexFormat.NEW_ENTITY && type.mode() == VertexFormat.Mode.QUADS, "Actual light buffer uses complete entity quads");
            return this;
        }; }
        void finish() { check(fields == 31, "All color, UV, overlay, light and normal attributes are present"); }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            finish(); check(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "Every mesh position is finite");
            vertices++; fields = 0; position.add(new Vec3(x, y, z)); return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { fields |= 1; alpha.add(a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { fields |= 2; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { fields |= 4; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { fields |= 8; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { fields |= 16; return this; }
    }
    private static InputStream resource(String path) {
        var stream = PhantomBladeClientAudit.class.getClassLoader().getResourceAsStream(path);
        check(stream != null, "Client resource exists: " + path); return stream;
    }
    private static byte[] bytes(String path) throws Exception { try (var stream = resource(path)) { return stream.readAllBytes(); } }
    private static ClassNode compiled(String name) throws Exception { ClassNode node = new ClassNode(); new ClassReader(bytes(name + ".class")).accept(node, 0); return node; }
    private static long calls(ClassNode type, String owner, String name) {
        long count = 0; for (var method : type.methods) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }
    private static boolean hasField(ClassNode type, String owner, String name) {
        for (var method : type.methods) for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.owner.equals(owner) && field.name.equals(name)) return true;
        return false;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void write(JsonObject report) throws Exception {
        Path path = output.toAbsolutePath().normalize(); Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
