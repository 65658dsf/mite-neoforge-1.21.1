package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.portal.MitePortalProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Opt-in title-screen audit of the real baked portal resources and merged client hooks. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class PortalVisualsClientAudit {
    private static volatile boolean baked;
    private static boolean done;

    private PortalVisualsClientAudit() {}

    @EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class BakingListener {
        private BakingListener() {}
        @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event) {
            if (Boolean.getBoolean("mite.portalVisualsAudit")) baked = true;
        }
    }

    @SubscribeEvent public static void afterTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mite.portalVisualsAudit") || !baked || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose();
            return;
        }
        if (!(mc.screen instanceof TitleScreen)) return;
        done = true;
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("world_created", false);
        JsonArray states = new JsonArray();
        report.add("states", states);
        try {
            check(mc.level == null, "The portal audit runs without a client world");
            String[] textures = {"portal_overworld", "portal_underworld", "portal_nether"};
            for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                for (int destination = 0; destination < textures.length; destination++) {
                    for (boolean rune : new boolean[]{false, true}) {
                        var state = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis)
                                .setValue(MitePortalProperties.DESTINATION, destination).setValue(MitePortalProperties.RUNEGATE, rune);
                        var model = mc.getBlockRenderer().getBlockModel(state);
                        var expected = ResourceLocation.fromNamespaceAndPath("mite", "block/" + textures[destination]);
                        var sprite = model.getParticleIcon(ModelData.EMPTY);
                        check(sprite.contents().name().equals(expected), "Wrong baked particle sprite for " + state);
                        long frames = sprite.contents().getUniqueFrames().count();
                        check(frames == 32, "The baked portal animation must contain 32 distinct frames");
                        var quads = model.getQuads(state, null, RandomSource.create(0), ModelData.EMPTY, null);
                        check(quads.size() == 2, "Each portal model must expose two faces");
                        Direction.Axis normal = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
                        for (BakedQuad quad : quads) {
                            check(quad.getSprite().contents().name().equals(expected), "Wrong baked portal face sprite");
                            check(quad.getDirection().getAxis() == normal, "Portal faces must match their saved horizontal axis");
                            int[] vertices = quad.getVertices();
                            int stride = vertices.length / 4;
                            int depth = normal == Direction.Axis.X ? 0 : 2;
                            float plane = quad.getDirection().getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0.375F : 0.625F;
                            for (int vertex = 0; vertex < 4; vertex++) {
                                check(Float.intBitsToFloat(vertices[vertex * stride + depth]) == plane,
                                        "Portal face vertices must retain their native axis-aligned plane");
                            }
                        }
                        JsonObject result = new JsonObject();
                        result.addProperty("state", state.toString());
                        result.addProperty("sprite", expected.toString());
                        result.addProperty("animation_frames", frames);
                        result.addProperty("axis_geometry_passed", true);
                        states.add(result);
                    }
                }
            }
            checkMerged(Gui.class, "mite$sourcePortalTexture");
            Class<?> particle = Class.forName("net.minecraft.client.particle.PortalParticle");
            checkMerged(particle, "mite$sourcePortalColor");
            report.addProperty("overlay_mixin_merged", true);
            report.addProperty("particle_mixin_merged", true);
            report.addProperty("status", "passed");
        } catch (Throwable failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            failure.printStackTrace();
        }
        try {
            Path output = Path.of(System.getProperty("mite.portalVisualsAuditOutput",
                    "build/verification/portal-visuals-client-audit.json")).toAbsolutePath();
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            failure.printStackTrace();
        } finally {
            mc.stop();
        }
    }

    private static void checkMerged(Class<?> target, String handler) {
        check(Arrays.stream(target.getDeclaredMethods()).anyMatch(method -> method.getName().endsWith(handler)),
                "Missing merged portal hook " + handler + " on " + target.getName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
