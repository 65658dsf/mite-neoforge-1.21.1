package io.github.ningmeng123.mite.client.phantom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Small shared meshes for a material-colored formation and fading sword light. @author NingMeng123 */
public final class PhantomBladeVisuals {
    public static final int TRAIL_TICKS = 12;
    public static final double ORBIT_RADIUS = 1.25D;
    public static final ResourceLocation LIGHT = ResourceLocation.fromNamespaceAndPath("mite", "textures/entity/phantom/blade_light.png");
    public static final List<String> MATERIALS = List.of("rusted_iron", "copper", "silver", "gold", "iron",
            "ancient_metal", "mithril", "adamantium", "vibranium");
    private PhantomBladeVisuals() {}

    public static int color(String material) {
        return switch (material) {
            case "rusted_iron" -> 0xC58C68;
            case "copper" -> 0xF0AD75;
            case "silver" -> 0xDDEBFF;
            case "gold" -> 0xFFE188;
            case "ancient_metal" -> 0x9DCEBD;
            case "mithril" -> 0x8AE4FF;
            case "adamantium" -> 0xB9D695;
            case "vibranium" -> 0xDC9AFF;
            default -> 0xC8D8E7;
        };
    }

    public static double orbitAngle(double time, int index, int bladeCount) {
        return time * .035D + index * Math.PI * 2D / bladeCount;
    }

    public static Vec3 orbit(double time, int index, int bladeCount) {
        double angle = orbitAngle(time, index, bladeCount);
        return new Vec3(Math.cos(angle) * ORBIT_RADIUS, .09D * Math.sin(time * .085D + index * .9D),
                Math.sin(angle) * ORBIT_RADIUS);
    }

    /** Turn the sword's face around the ring, keeping its tip on the local +Y axis. */
    public static void orientOrbit(PoseStack pose, double time, int index, int bladeCount) {
        pose.mulPose(Axis.YP.rotationDegrees((float) (-Math.toDegrees(orbitAngle(time, index, bladeCount)) + 90D)));
    }

    /** Rotate the upright sword frame so +Y points along the projectile's velocity. */
    public static void orientProjectile(PoseStack pose, float facingYaw, float elevation) {
        pose.mulPose(Axis.YP.rotationDegrees(facingYaw));
        pose.mulPose(Axis.XP.rotationDegrees(90F - elevation));
    }

    /**
     * Native handheld models inherit FIXED's Y=180 transform. Their diagonal tip axis is then
     * (-X,+Y), so -45 degrees around Z makes the tip point up in the aura's local frame.
     */
    public static void alignSwordSprite(PoseStack pose) {
        pose.mulPose(Axis.ZP.rotationDegrees(-45F));
    }

    public static float trailOpacity(double age) {
        double remaining = Math.clamp(1D - age / TRAIL_TICKS, 0D, 1D);
        return (float) (remaining * remaining * .8D);
    }

    public static void formationRing(PoseStack pose, MultiBufferSource buffers, double time, int color) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(LIGHT));
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double from = i * Math.PI * 2D / segments + time * .012D;
            double to = (i + 1) * Math.PI * 2D / segments + time * .012D;
            double inner = ORBIT_RADIUS - .018D, outer = ORBIT_RADIUS + .018D;
            quad(vertices, pose.last(), new Vec3(Math.cos(from) * inner, -.32D, Math.sin(from) * inner),
                    new Vec3(Math.cos(from) * outer, -.32D, Math.sin(from) * outer),
                    new Vec3(Math.cos(to) * outer, -.32D, Math.sin(to) * outer),
                    new Vec3(Math.cos(to) * inner, -.32D, Math.sin(to) * inner), color, .28F, .28F);
        }
    }

    /** A soft blade-shaped aura in the same local plane as the native sword sprite. */
    public static void bladeAura(PoseStack pose, MultiBufferSource buffers, int color, float alpha) {
        var vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(LIGHT));
        quad(vertices, pose.last(), new Vec3(-.09D, -.24D, -.012D), new Vec3(.09D, -.24D, -.012D),
                new Vec3(.045D, .54D, -.012D), new Vec3(-.045D, .54D, -.012D), color, alpha * .35F, alpha);
    }

    public static void ribbon(PoseStack pose, MultiBufferSource buffers, Vec3 from, Vec3 to,
            Vec3 camera, double widthFrom, double widthTo, int color, float alphaFrom, float alphaTo) {
        Vec3 tangent = to.subtract(from);
        if (tangent.lengthSqr() < .0000001D) return;
        Vec3 side = tangent.cross(camera.subtract(from));
        if (side.lengthSqr() < .0000001D) side = tangent.cross(new Vec3(0D, 1D, 0D));
        if (side.lengthSqr() < .0000001D) side = tangent.cross(new Vec3(0D, 0D, 1D));
        side = side.normalize();
        var vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(LIGHT));
        quad(vertices, pose.last(), from.add(side.scale(widthFrom)), from.subtract(side.scale(widthFrom)),
                to.subtract(side.scale(widthTo)), to.add(side.scale(widthTo)), color, alphaFrom, alphaTo);
    }

    private static void quad(VertexConsumer vertices, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            int color, float alphaFrom, float alphaTo) {
        vertex(vertices, pose, a, color, alphaFrom, 0F, 0F);
        vertex(vertices, pose, b, color, alphaFrom, 0F, 1F);
        vertex(vertices, pose, c, color, alphaTo, 1F, 1F);
        vertex(vertices, pose, d, color, alphaTo, 1F, 0F);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 point, int color,
            float alpha, float u, float v) {
        vertices.addVertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, (int) (255F * alpha))
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0F, 1F, 0F);
    }
}
