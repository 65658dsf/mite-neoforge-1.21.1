package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.item.SourceArmorDefense;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source armor/potion protection is calculated once; native events and damage-container finalization still run. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class SourceArmorDefenseMixin {
    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
    private void mite$sourceProtection(DamageSource source, float incoming, CallbackInfoReturnable<Float> cir) {
        if ((LivingEntity)(Object)this instanceof Player player && SourceArmorDefense.canProcess(player))
            cir.setReturnValue(SourceArmorDefense.absorb(player, source, incoming));
    }
    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("HEAD"), cancellable = true)
    private void mite$avoidDuplicateMagicReduction(DamageSource source, float incoming, CallbackInfoReturnable<Float> cir) {
        if ((LivingEntity)(Object)this instanceof Player player && SourceArmorDefense.processed(player)) cir.setReturnValue(incoming);
    }
    @WrapOperation(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean mite$sourceFireResistanceIsProtection(LivingEntity owner, Holder<MobEffect> effect, Operation<Boolean> original) {
        if (effect.equals(MobEffects.FIRE_RESISTANCE) && owner instanceof Player player && SourceArmorDefense.canHandle(player)) return false;
        return original.call(owner, effect);
    }
}
