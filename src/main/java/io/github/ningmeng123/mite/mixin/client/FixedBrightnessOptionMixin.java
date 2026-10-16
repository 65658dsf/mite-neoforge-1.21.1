package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 locks gamma on initialization, reads, writes and the native options control. @author NingMeng123 */
@Mixin(OptionInstance.class)
public abstract class FixedBrightnessOptionMixin<T> {
    @Shadow @Final public Component caption;
    @Shadow T value;
    @Unique private boolean mite$lockedBrightness;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mite$initializeMinimumBrightness(CallbackInfo ci) {
        mite$lockedBrightness = caption.getContents() instanceof TranslatableContents translation
                && translation.getKey().equals("options.gamma");
        if (mite$lockedBrightness) value = mite$minimumBrightness();
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void mite$readMinimumBrightness(CallbackInfoReturnable<T> cir) {
        if (mite$lockedBrightness) cir.setReturnValue(mite$minimumBrightness());
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void mite$keepMinimumBrightness(T requested, CallbackInfo ci) {
        if (!mite$lockedBrightness) return;
        // Gamma has no value-update side effects; do not save options or invoke a rejected update.
        value = mite$minimumBrightness();
        ci.cancel();
    }

    @Inject(method = "createButton(Lnet/minecraft/client/Options;IIILjava/util/function/Consumer;)Lnet/minecraft/client/gui/components/AbstractWidget;",
            at = @At("RETURN"))
    private void mite$disableBrightnessControl(CallbackInfoReturnable<AbstractWidget> cir) {
        if (!mite$lockedBrightness) return;
        AbstractWidget control = cir.getReturnValue();
        control.active = false;
        control.setTooltip(Tooltip.create(Component.translatable("tooltip.mite.brightness_locked")));
    }

    @Unique @SuppressWarnings("unchecked")
    private T mite$minimumBrightness() { return (T)Double.valueOf(0D); }
}
