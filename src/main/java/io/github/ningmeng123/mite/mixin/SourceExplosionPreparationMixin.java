package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.world.SourceExplosionBlocks;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Source pre-blast low-resistance clearing precedes native ray traversal. @author NingMeng123 */
@Mixin(Explosion.class)
public abstract class SourceExplosionPreparationMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private net.minecraft.world.level.ExplosionDamageCalculator damageCalculator;
    @Inject(method="explode",at=@At("HEAD"))
    private void mite$prepareLowResistanceBlocks(CallbackInfo ci) {
        SourceExplosionBlocks.prepare(level,(Explosion)(Object)this,damageCalculator);
    }
}
