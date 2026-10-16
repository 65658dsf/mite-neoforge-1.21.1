package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium and Reese use the same optional option model; no Sodium dependency is required. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.config.structure.Option", remap = false)
public abstract class SodiumBrightnessOptionMixin {
    @Shadow @Final ResourceLocation id;

    @Inject(method = "isEnabled()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void mite$disableSodiumBrightnessControl(CallbackInfoReturnable<Boolean> cir) {
        if (id.getNamespace().equals("sodium") && id.getPath().equals("general.gamma")) cir.setReturnValue(false);
    }
}
