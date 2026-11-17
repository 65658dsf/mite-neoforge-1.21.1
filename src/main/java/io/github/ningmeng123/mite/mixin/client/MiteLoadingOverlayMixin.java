package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.loading.MiteEarlyLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the startup background across the window handoff. @author NingMeng123 */
@Mixin(targets = "net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay", remap = false)
public abstract class MiteLoadingOverlayMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/fml/earlydisplay/DisplayWindow;close()V"))
    private void mite$releaseLoadingTextures(CallbackInfo ci) {
        MiteEarlyLoadingScreen.releaseTextures();
    }
}
