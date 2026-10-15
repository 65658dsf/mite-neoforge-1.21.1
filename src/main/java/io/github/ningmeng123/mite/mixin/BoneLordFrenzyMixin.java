package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.BoneLordFrenzy;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** tr.setFrenziedByBoneLord(20); tm adds half the unmodified attack attribute. @author NingMeng123 */
@Mixin(AbstractSkeleton.class)
public abstract class BoneLordFrenzyMixin implements BoneLordFrenzy {
    @Unique private static final ResourceLocation MITE_FRENZY = ResourceLocation.fromNamespaceAndPath("mite", "bone_lord_frenzy");
    @Unique private int mite$frenzyTicks;
    @Override public int mite$boneLordFrenzyTicks() { return mite$frenzyTicks; }
    @Override public void mite$setBoneLordFrenzy(LivingEntity target, int ticks) {
        AbstractSkeleton self = (AbstractSkeleton)(Object)this;
        if (self.level().isClientSide) return;
        mite$frenzyTicks = Math.max(0, ticks);
        var attack = self.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(MITE_FRENZY);
        }
        R196MonsterAi.refreshFrenzyDamage(self);
        if (self.getTarget() == null && target != null) self.setTarget(target);
    }
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void mite$tickFrenzy(CallbackInfo ci) {
        AbstractSkeleton self = (AbstractSkeleton)(Object)this;
        if (!(self.level() instanceof ServerLevel level) || mite$frenzyTicks <= 0) return;
        if (--mite$frenzyTicks == 0) mite$setBoneLordFrenzy(null, 0);
        else if (self.tickCount % 4 == 0)
            level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | 8527390), self.getX(), self.getY(.5), self.getZ(),
                    1, self.getBbWidth() * .3, self.getBbHeight() * .3, self.getBbWidth() * .3, 0);
    }
}
