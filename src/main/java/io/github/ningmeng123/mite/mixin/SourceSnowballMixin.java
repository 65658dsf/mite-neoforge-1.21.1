package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.R196Elemental;
import io.github.ningmeng123.mite.entity.R196FireElemental;
import io.github.ningmeng123.mite.entity.R196Silverfish;
import io.github.ningmeng123.mite.entity.MobKind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.projectile.Snowball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Source up damage and quenching run in the native impact path, retaining projectile events. @author NingMeng123 */
@Mixin(Snowball.class)
public abstract class SourceSnowballMixin {
    @WrapOperation(method="onHitEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mite$sourceSnowDamage(Entity target,DamageSource source,float nativeAmount,Operation<Boolean> original) {
        float amount=target instanceof Blaze || target instanceof R196FireElemental ? 3F
                : target instanceof MagmaCube || target instanceof R196Silverfish fish && fish.miteKind()==MobKind.NETHERSPAWN ? 2F : 1F;
        boolean affected=original.call(target,source,amount);
        Snowball ball=(Snowball)(Object)this;
        if (ball.level() instanceof ServerLevel server) {
            if (target.isOnFire()) {
                if (ball.getRandom().nextBoolean()) target.clearFire();
                server.sendParticles(ParticleTypes.CLOUD,target.getX(),target.getY()+target.getBbHeight()*.5,target.getZ(),8,.25,.3,.25,.01);
            } else if (target instanceof R196Elemental earth && earth.isMagma()) earth.quench();
        }
        return affected;
    }
}
