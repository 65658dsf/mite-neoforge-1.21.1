package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Source lunar quads in the modern sky's existing additive pass. @author NingMeng123 */
public final class LunarSkyRendering {
    public static final ResourceLocation MOON = texture("moon_phases.png");
    public static final ResourceLocation HALO = texture("halo_256.png");
    public static final ResourceLocation RING = texture("ring_128.png");
    private LunarSkyRendering() {}
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "textures/environment/" + name);
    }

    public static void beforeMoon(LunarCalendar.State state, float rain) {
        var color = LunarVisuals.moonColor(state);
        RenderSystem.setShaderTexture(0, MOON);
        RenderSystem.setShaderColor(color.red(), color.green(), color.blue(), 1F - rain);
    }

    public static void afterMoon(LunarCalendar.State state, float rain, float celestialAngle, Matrix4f moonPose) {
        var glow = LunarVisuals.glow(state, celestialAngle, rain);
        if (glow.alpha() <= 0F || glow.texture() == LunarVisuals.GlowTexture.NONE) return;
        var color = glow.color();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, glow.texture() == LunarVisuals.GlowTexture.HALO ? HALO : RING);
        RenderSystem.setShaderColor(color.red(), color.green(), color.blue(), glow.alpha());
        // Capture the actual native moon pose, including any third-party sun-path tilt.
        Matrix4f matrix = new Matrix4f(moonPose);
        float radius = glow.radius();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, -radius, -100F, radius).setUv(0F, 0F);
        buffer.addVertex(matrix, radius, -100F, radius).setUv(1F, 0F);
        buffer.addVertex(matrix, radius, -100F, -radius).setUv(1F, 1F);
        buffer.addVertex(matrix, -radius, -100F, -radius).setUv(0F, 1F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    public static void restoreColor(float rain) { RenderSystem.setShaderColor(1F, 1F, 1F, 1F - rain); }
}
