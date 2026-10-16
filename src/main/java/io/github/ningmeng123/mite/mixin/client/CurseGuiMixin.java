package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Source avj forces the darkest native vignette, even with fast graphics. @author NingMeng123 */
@Mixin(Gui.class)
public abstract class CurseGuiMixin {
    private static boolean mite$cursed() {
        var player = Minecraft.getInstance().player;
        return player != null && PlayerCurses.state(player).active();
    }
    @ModifyExpressionValue(method = "renderCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;useFancyGraphics()Z"))
    private boolean mite$alwaysDrawCurseVignette(boolean fancy) { return fancy || mite$cursed(); }

    @ModifyExpressionValue(method = "renderVignette", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;vignetteBrightness:F"))
    private float mite$darkestCurseVignette(float brightness) { return mite$cursed() ? 1F : brightness; }

    @WrapOperation(method = "renderAirLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAirSupply()I"))
    private int mite$hideLandBreathCurseBubbles(Player player, Operation<Integer> original) {
        // Source avj only draws air while submerged. The modern HUD also draws any non-full
        // reserve on land, but this curse's real reserve intentionally remains capped at ninety.
        if (!player.isEyeInFluid(FluidTags.WATER) && PlayerCurses.has(player, R196Curse.CANNOT_HOLD_BREATH))
            return player.getMaxAirSupply();
        return original.call(player);
    }
}
