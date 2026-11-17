package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.PortalVisuals;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Use the entered gate's animated texture throughout the native portal fade. @author NingMeng123 */
@Mixin(Gui.class)
public abstract class PortalOverlayMixin {
    @ModifyArg(method = "renderPortalOverlay", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockModelShaper;getParticleIcon(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"), index = 0)
    private BlockState mite$sourcePortalTexture(BlockState state) {
        return PortalVisuals.overlayState(state);
    }
}
