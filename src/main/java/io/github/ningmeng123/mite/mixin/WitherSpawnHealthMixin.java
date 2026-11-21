package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The summon explosion ends the charge even when an add-on raises the Wither's maximum health. @author NingMeng123 */
@Mixin(WitherBoss.class)
public abstract class WitherSpawnHealthMixin {
    @Inject(method = "customServerAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"))
    private void mite$finishSummonAtFullHealth(CallbackInfo ci) {
        WitherBoss wither = (WitherBoss) (Object) this;
        wither.setHealth(wither.getMaxHealth());
    }
}
