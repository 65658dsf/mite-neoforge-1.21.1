package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AppleSkin's modern rapid-regeneration estimate does not describe R196 metabolism. @author NingMeng123 */
@Pseudo
@Mixin(targets = "squeek.appleskin.client.HUDOverlayHandler$HealthOverlay", remap = false)
public abstract class AppleSkinHealthMixin {
    @Inject(method = "shouldRenderOverlay(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;I)Z", at = @At("HEAD"), cancellable = true, require = 1, allow = 1, remap = false)
    private void mite$disableModernHealingPreview(Minecraft minecraft, Player player, GuiGraphics graphics, int tick, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
