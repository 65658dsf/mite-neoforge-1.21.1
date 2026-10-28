package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Consume F3+B before it enables entity hitboxes or reports a debug toggle. @author NingMeng123 */
@Mixin(KeyboardHandler.class)
public abstract class DisabledHitboxDebugKeyMixin {
    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void mite$disableHitboxDebugKey(int key, CallbackInfoReturnable<Boolean> cir) {
        if (key == GLFW.GLFW_KEY_B) cir.setReturnValue(true);
    }
}
