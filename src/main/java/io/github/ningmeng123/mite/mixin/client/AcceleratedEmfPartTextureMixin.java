package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.EmfPartTextureCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Leave recursive CEM trees with independent textures to EMF's per-part buffer selection. @author NingMeng123 */
@Pseudo
@Mixin(targets = "com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature", remap = false)
public abstract class AcceleratedEmfPartTextureMixin {
    @Inject(method = "shouldUseAcceleratedPipeline()Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void mite$preserveCemTextures(CallbackInfoReturnable<Boolean> cir) {
        if (EmfPartTextureCompatibility.useVanillaPipeline()) cir.setReturnValue(false);
    }
}
