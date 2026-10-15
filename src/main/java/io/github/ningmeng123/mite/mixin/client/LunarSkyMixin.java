package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.client.LunarSkyRendering;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Only the moon draw is wrapped: modern sun, fog, stars and dimension renderers remain in their normal paths. @author NingMeng123 */
// Draw-wrapper ordering is checked against the optional Iris moon-visibility operation.
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class LunarSkyMixin {
    @Shadow private ClientLevel level;

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V", ordinal = 2))
    private void mite$sourceMoon(MeshData mesh, Operation<Void> original, Matrix4f viewRotation, Matrix4f projection,
            float partialTick, Camera camera, boolean foggy, Runnable setupFog, @Local PoseStack moonPose) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            original.call(mesh);
            return;
        }
        var state = LunarCalendar.state(level.getDayTime(), true);
        float rain = level.getRainLevel(partialTick);
        LunarSkyRendering.beforeMoon(state, rain);
        try {
            original.call(mesh);
            LunarSkyRendering.afterMoon(state, rain, level.getTimeOfDay(partialTick), moonPose.last().pose());
        } finally {
            LunarSkyRendering.restoreColor(rain);
        }
    }
}
