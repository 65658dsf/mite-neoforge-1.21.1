package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.client.BloodMoonAtmosphere;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tint only the native atmosphere result; moon meshes and the existing night lightmap stay unchanged. @author NingMeng123 */
@Mixin(ClientLevel.class)
public abstract class BloodMoonAtmosphereMixin {
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 mite$bloodRainSky(Vec3 original, Vec3 cameraPosition, float partialTick) {
        return BloodMoonAtmosphere.color((ClientLevel)(Object)this, partialTick, original);
    }

    @ModifyReturnValue(method = "getCloudColor", at = @At("RETURN"))
    private Vec3 mite$bloodRainClouds(Vec3 original, float partialTick) {
        return BloodMoonAtmosphere.color((ClientLevel)(Object)this, partialTick, original);
    }
}
