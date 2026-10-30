package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.InBedChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leave the sleeping player's health HUD unobstructed. @author NingMeng123 */
@Mixin(InBedChatScreen.class)
public abstract class InBedChatScreenMixin {
    @Shadow private Button leaveBedButton;

    @Inject(method = "init", at = @At("RETURN"))
    private void mite$raiseWakeButton(CallbackInfo ci) {
        leaveBedButton.setY(Math.max(4, leaveBedButton.getY() - 32));
    }
}
