package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** sf.j/az use wild8 and tame12, retaining the missing-health amount across tame transitions. @author NingMeng123 */
@Mixin(TamableAnimal.class)
public abstract class R196VanillaWolfMixin {
    @Unique private float mite$healthBeforeTaming;
    @Unique private float mite$maximumBeforeTaming;
    @Inject(method = "setTame", at = @At("HEAD"))
    private void mite$rememberSourceWolfHealth(boolean tame, boolean sideEffects, CallbackInfo ci) {
        if ((Object)this instanceof Wolf wolf && wolf.getType() == EntityType.WOLF) {
            mite$healthBeforeTaming = wolf.getHealth(); mite$maximumBeforeTaming = wolf.getMaxHealth();
        }
    }
    @WrapOperation(method = "setTame", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/TamableAnimal;applyTamingSideEffects()V"))
    private void mite$skipModernWolfFullHeal(TamableAnimal animal, Operation<Void> original) {
        if (animal.getType() != EntityType.WOLF) original.call(animal);
    }
    @Inject(method = "setTame", at = @At("RETURN"))
    private void mite$sourceWolfTaming(boolean tame, boolean sideEffects, CallbackInfo ci) {
        if ((Object)this instanceof Wolf wolf && wolf.getType() == EntityType.WOLF) {
            wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(tame ? 12 : 8);
            wolf.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(tame ? 32 : 16);
            wolf.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3);
            float change = wolf.getMaxHealth() - mite$maximumBeforeTaming;
            wolf.setHealth(change > 0 ? mite$healthBeforeTaming + change : Math.min(mite$healthBeforeTaming, wolf.getMaxHealth()));
        }
    }
}
