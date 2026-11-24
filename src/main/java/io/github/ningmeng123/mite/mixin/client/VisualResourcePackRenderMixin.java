package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.VisualResourcePackRestrictions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoids rendering stale forbidden assets while a policy-triggered reload completes. @author NingMeng123 */
@Mixin(GameRenderer.class)
public abstract class VisualResourcePackRenderMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void mite$hideForbiddenAssets(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VisualResourcePackRestrictions.shouldHideWorld(Minecraft.getInstance())) ci.cancel();
    }
}
