package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.PortalVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep native portal particle motion and random brightness when coloring its source gate. @author NingMeng123 */
@Mixin(PortalParticle.class)
public abstract class PortalParticleMixin extends TextureSheetParticle {
    protected PortalParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mite$sourcePortalColor(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, CallbackInfo ci) {
        PortalVisuals.colorParticle(this, level, BlockPos.containing(x, y, z), bCol);
    }
}
