package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.client.BloodMoonAtmosphere;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Match rain impact droplets to the daytime blood-rain curtain. @author NingMeng123 */
@Mixin(WaterDropParticle.Provider.class)
public abstract class BloodRainParticleMixin {
    @ModifyReturnValue(method = "createParticle(Lnet/minecraft/core/particles/SimpleParticleType;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("RETURN"))
    private Particle mite$bloodRainDroplet(Particle original, SimpleParticleType type, ClientLevel level,
            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        if (original != null) {
            var color = BloodMoonAtmosphere.rainColor(level);
            if (color.red() == 1F && color.green() == 1F && color.blue() == 1F) return original;
            original.setColor(color.red(), color.green(), color.blue());
        }
        return original;
    }
}
