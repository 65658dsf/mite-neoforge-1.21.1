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

/** Modern block rays retain their native radius; R196 living damage has its own power and origin. @author NingMeng123 */
@Mixin(Creeper.class)
public abstract class R196CreeperExplosionMixin {
    @WrapOperation(method = "explodeCreeper", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"))
    private Explosion mite$sourceBlast(Level level, Entity entity, double x, double y, double z, float nativeRadius,
            Level.ExplosionInteraction interaction, Operation<Explosion> original) {
        // tf's .715 is not a modern Explosion.radius: abr also has a separate weak-block
        // pass and uses block hardness as resistance. Feeding it to modern rays reduced
        // the ordinary creeper's block energy from 3 to .715. Keep the 1.21 terrain's
        // native scale, including charged/custom NBT radii, and double infernal blasts.
        float blockRadius = nativeRadius * (entity instanceof R196InfernalCreeper ? 2F : 1F);
        float livingPower = blockRadius / 3F * 1.1F;
        return level.explode(entity, Explosion.getDefaultDamageSource(level, entity), new R196ExplosionCalculator(entity, livingPower),
                x, y + entity.getBbHeight() * .25F, z, blockRadius, false, interaction);
    }
}
