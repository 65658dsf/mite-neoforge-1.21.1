package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep optional Enchantment Descriptions details behind the Shift key. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.darkhax.enchdesc.common.impl.EnchdescMod", remap = false)
public abstract class EnchantmentDescriptionKeyMixin {
    @Inject(method = "isKeybindConditionMet()Z", at = @At("HEAD"), cancellable = true)
    private void mite$showDescriptionsOnShift(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(Screen.hasShiftDown());
    }
}
