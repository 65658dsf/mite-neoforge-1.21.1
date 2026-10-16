package io.github.ningmeng123.mite.mixin.client;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** The source consciousness layer owns fading; lying in bed alone must not trigger native darkness. @author NingMeng123 */
@Mixin(Gui.class)
public abstract class SourceSleepOverlayMixin {
    @Inject(method="renderSleepOverlay",at=@At("HEAD"),cancellable=true)
    private void mite$sourceFade(GuiGraphics graphics,DeltaTracker delta,CallbackInfo ci){ci.cancel();}
}
