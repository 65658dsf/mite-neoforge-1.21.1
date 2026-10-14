package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** te applies poison for 480 ticks only after real lost health, on every difficulty. @author NingMeng123 */
@Mixin(CaveSpider.class)
public abstract class NativeCaveSpiderPoisonMixin extends Spider {
    protected NativeCaveSpiderPoisonMixin(EntityType<? extends Spider> type,Level level) { super(type,level); }
    @Inject(method="doHurtTarget",at=@At("HEAD"),cancellable=true)
    private void mite$sourceCaveToxin(Entity target,CallbackInfoReturnable<Boolean> cir) {
        if(getType()!=EntityType.CAVE_SPIDER) return;
        float before=target instanceof LivingEntity living ? living.getHealth() : 0F;
        boolean affected=super.doHurtTarget(target);
        if(affected && target instanceof LivingEntity living && living.isAlive() && living.getHealth()<before)
            living.addEffect(new MobEffectInstance(MobEffects.POISON,480),this);
        cir.setReturnValue(affected);
    }
}
