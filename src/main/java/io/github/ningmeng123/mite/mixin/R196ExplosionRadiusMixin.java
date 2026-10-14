package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.entity.R196ExplosionCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The second radius read starts the entity phase, after the independent block ray traversal. @author NingMeng123 */
@Mixin(Explosion.class)
public abstract class R196ExplosionRadiusMixin {
    @Shadow @Final private ExplosionDamageCalculator damageCalculator;
    @ModifyExpressionValue(method = "explode", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Explosion;radius:F", ordinal = 1))
    private float mite$livingRadius(float blockPower) {
        return damageCalculator instanceof R196ExplosionCalculator source ? source.entityRadius() / 2F : blockPower;
    }
}
