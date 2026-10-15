package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.client.LunarVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** R196 moon exposure before sky mixing, then its colored night tint after modern gamma. @author NingMeng123 */
@Mixin(LightTexture.class)
public abstract class LunarLightTextureMixin {
    @Shadow @Final private Minecraft minecraft;
    @Unique private LunarVisuals.LightState mite$lunarFrame;

    @Inject(method = "updateLightTexture", at = @At("HEAD"))
    private void mite$moonLightFrame(float partialTick, CallbackInfo ci) {
        var level = minecraft.level;
        mite$lunarFrame = level != null && level.dimension().equals(Level.OVERWORLD)
                ? LunarVisuals.lightState(level.getDayTime(), true, level.getRainLevel(partialTick)) : null;
    }

    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LightTexture;getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F", ordinal = 0))
    private float mite$moonSkyBrightness(float original) {
        return mite$lunarFrame == null ? original : original * mite$lunarFrame.skyMultiplier();
    }

    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lorg/joml/Vector3f;mul(F)Lorg/joml/Vector3f;", ordinal = 2))
    private Vector3f mite$moonTintAfterGamma(Vector3f color, float multiplier, Operation<Vector3f> original,
            @Local(ordinal = 0) int skyLight, @Local(ordinal = 1) int blockLight) {
        if (mite$lunarFrame != null) {
            var tint = LunarVisuals.tintLightmap(mite$lunarFrame, skyLight, blockLight, color.x, color.y, color.z);
            color.set(tint.red(), tint.green(), tint.blue());
        }
        return original.call(color, multiplier);
    }
}
