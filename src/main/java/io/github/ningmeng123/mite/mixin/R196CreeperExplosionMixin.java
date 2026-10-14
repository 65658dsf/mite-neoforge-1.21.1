package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.R196InfernalCreeper;
import io.github.ningmeng123.mite.entity.R196ExplosionCalculator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native death/cloud lifecycle is retained, while explosion energy and origin use R196. @author NingMeng123 */
@Mixin(Creeper.class)
public abstract class R196CreeperExplosionMixin {
    @WrapOperation(method = "explodeCreeper", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"))
    private Explosion mite$sourceBlast(Level level, Entity entity, double x, double y, double z, float ignoredRadius,
            Level.ExplosionInteraction interaction, Operation<Explosion> original) {
        Creeper creeper = (Creeper)entity;
        float power = (entity instanceof R196InfernalCreeper ? 2F : 1F) * (creeper.isPowered() ? 2F : 1F);
        return level.explode(entity, Explosion.getDefaultDamageSource(level, entity), new R196ExplosionCalculator(entity, power * 1.1F),
                x, y + entity.getBbHeight() * .25F, z, power * .715F, false, interaction);
    }
}
