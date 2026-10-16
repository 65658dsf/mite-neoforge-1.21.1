package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.AutoHarvestInput;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Releasing the cursor ends the gesture without changing physical mouse or key states. @author NingMeng123 */
@Mixin(MouseHandler.class)
public abstract class AutoHarvestMouseMixin {
    @Inject(method = "releaseMouse", at = @At("HEAD"))
    private void mite$clearAutoHarvestOnCursorRelease(CallbackInfo ci) { AutoHarvestInput.reset(); }
}
