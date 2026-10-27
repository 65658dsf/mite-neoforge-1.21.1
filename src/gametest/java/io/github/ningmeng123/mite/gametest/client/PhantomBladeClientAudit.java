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
import io.github.ningmeng123.mite.client.phantom.PhantomBladeParticles;
import io.github.ningmeng123.mite.client.phantom.PhantomBladeProjectileRenderer.Trail;
import io.github.ningmeng123.mite.client.phantom.PhantomBladeVisuals;
import io.github.ningmeng123.mite.phantom.PhantomBladeSwarm;
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
import net.minecraft.client.ParticleStatus;
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
        report.addProperty("scope", "decoded RGBA sprites, inherited native FIXED item transforms and actual texture-frame axes, orbiting and flying sword orientation, actual emitted aura/trail vertices, continuous owner orbit, trail history and compiled renderer wiring; not rendered pixels");
        try {
            resources(report); orientations(report); meshes(report); particles(report); wiring(report);
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
        JsonArray models = new JsonArray(); int restingCases = 0, flightCases = 0;
        float[] orbitYaws = {0F, 45F, 90F, 180F, -90F};
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
                for (float orbitYaw : orbitYaws) {
                    var pose = new PoseStack(); PhantomBladeVisuals.orientResting(pose, orbitYaw);
                    auraAligned(pose, new Vec3(0D, 1D, 0D), "The actual orbiting aura stays vertical at every orbit angle");
                    swordAligned(pose, model, axis, new Vec3(0D, 1D, 0D), "The native orbiting sword points up: " + material + "/frame" + frame + "/orbitYaw" + orbitYaw);
                    restingCases++;
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
        report.addProperty("actual_native_resting_grip_to_tip_orientation_cases", restingCases);
        report.addProperty("actual_native_projectile_grip_to_tip_orientation_cases", flightCases);
        report.addProperty("actual_aura_direction_matches_native_sword_tip", true);
    }

    private static void meshes(JsonObject report) {
        JsonArray blades = new JsonArray();
        for (var profile : PhantomBladeRules.profiles()) {
            check(profile.bladeCount() == 1, "Every material controls exactly one persistent blade: " + profile.material());
            JsonObject blade = new JsonObject(); blade.addProperty("material", profile.material()); blade.addProperty("swords", 1); blades.add(blade);
        }
        report.add("single_blade_per_material", blades);
        var orbitOwner = java.util.UUID.fromString("12345678-1234-5678-9abc-123456789abc");
        check(PhantomBladeSwarm.ORBIT_PERIOD_TICKS == 160, "The idle sword completes one orbit every eight seconds");
        for (double time = 0D; time <= 320D; time += .25D) {
            Vec3 offset = PhantomBladeSwarm.restingOffset(orbitOwner, 1.8D, time);
            Vec3 nextFrame = PhantomBladeSwarm.restingOffset(orbitOwner, 1.8D, time + .25D);
            Vec3 repeated = PhantomBladeSwarm.restingOffset(orbitOwner, 1.8D, time + 160D);
            check(Math.abs(offset.horizontalDistance() - 1.1D) < 1E-8D && Math.abs(offset.y - 1.17D) <= .08000001D,
                    "The shared orbit keeps its radius and gently bobs around upper-body height");
            check(offset.distanceTo(nextFrame) > 0D && offset.distanceTo(nextFrame) < .012D,
                    "Fractional render frames move continuously, including the orbit wrap boundary");
            check(offset.distanceTo(repeated) < 1E-8D, "World-clock orbit phase repeats without depending on entity age or owner yaw");
        }
        Vec3 quarterA = PhantomBladeSwarm.restingOffset(orbitOwner, 1.8D, 0D);
        Vec3 quarterB = PhantomBladeSwarm.restingOffset(orbitOwner, 1.8D, 40D);
        check(Math.abs(new Vec3(quarterA.x, 0D, quarterA.z).normalize().dot(new Vec3(quarterB.x, 0D, quarterB.z).normalize())) < 1E-8D,
                "One quarter period moves the upright sword through ninety degrees around its owner");
        report.addProperty("continuous_eight_second_orbit_uses_stable_owner_phase_and_partial_ticks", true);
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
        report.addProperty("actual_aura_vertices", aura.vertices);
        report.addProperty("actual_ribbon_vertices", ribbon.vertices); report.addProperty("all_new_entity_vertex_attributes_written", true);
        report.addProperty("bounded_motion_history_fades_and_rejects_teleport_streaks", true);
    }

    private static void particles(JsonObject report) throws Exception {
        int normal = 0, reduced = 0, minimal = 0;
        for (int tick = 0; tick < 20; tick++) {
            var full = PhantomBladeParticles.emission(tick, 0, ParticleStatus.ALL, false, 1D, .49D);
            var lower = PhantomBladeParticles.emission(tick, 0, ParticleStatus.DECREASED, false, 1D, .49D);
            normal += full.total(); reduced += lower.total();
            minimal += PhantomBladeParticles.emission(tick, 0, ParticleStatus.MINIMAL, false, 1D, .49D).total();
            check(full.motes() == 2 && full.stars() <= 1 && full.total() <= 3,
                    "An ordinary flight tick has two tiny colored motes and at most one star");
            check(lower.motes() <= 1 && lower.stars() <= 1 && lower.total() <= 2,
                    "Reduced particles use a lower deterministic tick cadence");
            for (ParticleStatus quality : ParticleStatus.values()) {
                check(PhantomBladeParticles.emission(tick, 0, quality, true, 1D, .49D).total() == 0,
                        "A parked or orbiting sword cannot drag a particle trail");
                check(PhantomBladeParticles.emission(tick, 0, quality, false, 576.01D, .49D).total() == 0,
                        "Particle work stops beyond twenty-four blocks");
                check(PhantomBladeParticles.emission(tick, 0, quality, false, 1D, 0D).total() == 0,
                        "A stopped sword produces no accumulating particles");
            }
        }
        check(normal == 45 && reduced == 13 && minimal == 0,
                "Twenty flying ticks have bounded material dust and sparse luminous stars for each quality setting");
        Vec3 hilt = PhantomBladeParticles.hilt(new Vec3(2D, 3D, 4D), new Vec3(0D, 0D, .7D));
        check(hilt.distanceTo(new Vec3(2D, 3D, 3.69D)) < 1E-8D,
                "Particle attachment follows the real flight ribbon's hilt offset behind the sword");
        var track = new PhantomBladeParticles.Track();
        check(track.sample(0, Vec3.ZERO) == null, "The first observation cannot invent a preceding path");
        var segment = track.sample(1, new Vec3(.7D, 0D, 0D));
        check(segment != null && segment.at(.5D).distanceTo(new Vec3(.35D, 0D, 0D)) < 1E-8D,
                "Particles are distributed along the actual sampled hilt path");
        check(track.sample(1, new Vec3(1000D, 0D, 0D)) == null,
                "A repeated callback in one tick produces no second emission or changed sample");
        check(track.sample(2, new Vec3(1.4D, 0D, 0D)).from().distanceTo(new Vec3(.7D, 0D, 0D)) < 1E-8D,
                "Duplicate tick callbacks cannot corrupt the following particle segment");
        check(track.sample(4, new Vec3(2.8D, 0D, 0D)) == null
                && track.sample(5, new Vec3(1000D, 0D, 0D)) == null
                && track.sample(6, new Vec3(1000D, 0D, 0D)) == null,
                "Missed ticks, teleports and stationary samples cannot draw long or stacked particle trails");
        var budget = new PhantomBladeParticles.Budget();
        check(budget.claim(0L, 40, 48) == 40 && budget.claim(0L, 40, 48) == 8
                && budget.claim(0L, 1, 48) == 0, "All local swords share a strict forty-eight-particle tick budget");
        check(budget.claim(1L, 3, 48) == 3 && budget.claim(1L, 48, 24) == 21
                && budget.claim(1L, 1, 24) == 0, "A lower quality setting shares twenty-four slots including particles already emitted that tick");
        JsonArray colors = new JsonArray();
        for (String material : PhantomBladeVisuals.MATERIALS) {
            JsonObject row = new JsonObject(); row.addProperty("material", material);
            row.addProperty("color", String.format("#%06X", PhantomBladeVisuals.color(material))); colors.add(row);
        }
        for (String type : List.of("dust", "end_rod")) {
            var definition = JsonParser.parseString(new String(bytes("assets/minecraft/particles/" + type + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            check(!definition.getAsJsonArray("textures").isEmpty(), "The native particle has actual sprite resources: " + type);
            for (var value : definition.getAsJsonArray("textures")) {
                var texture = ResourceLocation.parse(value.getAsString());
                var sprite = ImageIO.read(new java.io.ByteArrayInputStream(bytes("assets/" + texture.getNamespace()
                        + "/textures/particle/" + texture.getPath() + ".png")));
                check(sprite != null && sprite.getWidth() > 0, "Every particle animation sprite decodes: " + texture);
            }
        }
        ClassNode actual = compiled(CLIENT + "PhantomBladeParticles");
        check(actual.methods.stream().anyMatch(method -> method.name.equals("afterTick")
                        && method.desc.equals("(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Post;)V"))
                && calls(actual, "net/minecraft/client/particle/ParticleEngine", "createParticle") == 2
                && calls(actual, CLIENT + "PhantomBladeParticles$Track", "sample") == 1
                && calls(actual, CLIENT + "PhantomBladeParticles$Budget", "claim") == 1,
                "The actual client entity-tick listener uses the audited path, shared limit, dust and luminous star emission");
        check(calls(actual, CLIENT + "PhantomBladeVisuals", "color") == 1
                && hasField(actual, "net/minecraft/core/particles/ParticleTypes", "END_ROD"),
                "Production particles use the same material colors as the flight ribbon and the native fine-star sprite");
        report.add("flight_particle_material_colors", colors);
        report.addProperty("flight_particles_per_twenty_ticks_all", normal);
        report.addProperty("flight_particles_per_twenty_ticks_decreased", reduced);
        report.addProperty("flight_particles_per_twenty_ticks_minimal", minimal);
        report.addProperty("particle_tick_deduplication_path_and_shared_budget_passed", true);
        report.addProperty("particle_resources_and_actual_client_tick_wiring_passed", true);
    }

    private static void wiring(JsonObject report) throws Exception {
        ClassNode registration = compiled(CLIENT + "PhantomBladeClientRegistration");
        check(calls(registration, "net/neoforged/neoforge/client/event/EntityRenderersEvent$RegisterRenderers", "registerEntityRenderer") == 2,
                "Both synchronized entities are wired to real client renderers");
        ClassNode swarm = compiled(CLIENT + "PhantomBladeSwarmRenderer");
        ClassNode projectile = compiled(CLIENT + "PhantomBladeProjectileRenderer");
        check(calls(swarm, "net/minecraft/client/renderer/entity/ItemRenderer", "renderStatic") == 0,
                "The invisible controller never draws a second sword");
        check(calls(projectile, "net/minecraft/client/renderer/entity/ItemRenderer", "renderStatic") == 1
                && calls(projectile, "io/github/ningmeng123/mite/registry/MitePhantomBlades", "sword") == 1,
                "One native material sword is drawn by the persistent projectile renderer");
        check(calls(projectile, CLIENT + "PhantomBladeVisuals", "orientResting") == 1
                && calls(projectile, CLIENT + "PhantomBladeVisuals", "orientProjectile") == 1
                && calls(projectile, CLIENT + "PhantomBladeVisuals", "alignSwordSprite") == 1,
                "The actual renderer uses the verified parked/flight helpers and native sprite alignment");
        check(calls(projectile, "io/github/ningmeng123/mite/phantom/PhantomBladeSwarm", "restingPosition") == 1
                        && calls(projectile, "io/github/ningmeng123/mite/phantom/PhantomBladeSwarm", "restingYaw") == 1,
                "The orbiting renderer uses the shared gameplay position and orientation with partial ticks");
        check(calls(projectile, "io/github/ningmeng123/mite/phantom/PhantomBladeProjectile", "isDocked") >= 2
                && calls(projectile, "java/util/Map", "remove") == 1,
                "Docking changes orientation and removes all old motion-trail history");
        check(calls(projectile, "com/mojang/blaze3d/vertex/PoseStack", "mulPose") == 0,
                "No renderer-local rotation overrides the audited orientation helpers");
        var draw = projectile.methods.stream().filter(method -> method.name.equals("render")
                && method.desc.contains("Lio/github/ningmeng123/mite/phantom/PhantomBladeProjectile;")).findFirst().orElseThrow();
        int resting = -1, flight = -1, aura = -1, align = -1, item = -1, index = 0;
        for (var instruction : draw.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(CLIENT + "PhantomBladeVisuals")) {
                    if (call.name.equals("orientResting")) resting = index;
                    if (call.name.equals("orientProjectile")) flight = index;
                    if (call.name.equals("bladeAura")) aura = index;
                    if (call.name.equals("alignSwordSprite")) align = index;
                }
                if (call.owner.equals("net/minecraft/client/renderer/entity/ItemRenderer") && call.name.equals("renderStatic")) item = index;
            }
            index++;
        }
        check(resting >= 0 && flight >= 0 && resting < aura && flight < aura && aura < align && align < item,
                "Both real orientations precede matching aura, sprite alignment and the single native item draw");
        report.addProperty("one_persistent_sword_renderer_with_resting_and_return_flight_orientation", true);
        check(calls(projectile, CLIENT + "PhantomBladeProjectileRenderer$Trail", "sample") == 1
                && calls(projectile, CLIENT + "PhantomBladeVisuals", "ribbon") == 1, "Flying renderer actually draws its sampled trajectory");
        ClassNode mesh = compiled(CLIENT + "PhantomBladeVisuals");
        check(calls(mesh, "net/minecraft/client/renderer/RenderType", "entityTranslucentEmissive") >= 1,
                "Soft geometry uses the native full-bright translucent emission shader");
        check(hasField(registration, "io/github/ningmeng123/mite/registry/MitePhantomBlades", "SWARM")
                && hasField(registration, "io/github/ningmeng123/mite/registry/MitePhantomBlades", "PROJECTILE"), "The registered types are the actual synchronized gameplay entities");
        report.addProperty("real_entity_registration_single_native_sword_and_motion_trail_wired", true);
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
