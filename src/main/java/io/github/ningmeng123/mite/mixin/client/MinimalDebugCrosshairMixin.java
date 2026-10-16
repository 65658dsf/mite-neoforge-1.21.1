package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the ordinary crosshair while R196's FPS overlay is visible. @author NingMeng123 */
@Mixin(Gui.class)
public abstract class MinimalDebugCrosshairMixin {
    @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;showDebugScreen()Z"))
    private boolean mite$keepOrdinaryCrosshair(boolean original) {
        return false;
    }
}
